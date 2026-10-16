package com.recruitment.talentpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H15 P6, P7 - T7-T14 (REQUIREMENT muc 7.1, R-P1-R-P5, R-M3-R-M6). @Transactional: moi @Test rollback rieng; khong
// test nao o day khang dinh thu tu thoi gian. Bo helper du lieu chep tu TalentPoolOwnerControllerIntegrationTest (tien le
// du an: khong tach tien ich test dung chung).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationTalentPoolControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MSG_SELECTION = "Danh sách kho không hợp lệ. Mỗi kho chỉ được chọn một lần.";
    private static final String MSG_CONSENT =
            "Ứng viên chưa đồng ý cho công ty lưu hồ sơ của đơn này nên không thể thêm vào kho.";
    private static final List<String> FORBIDDEN_KEYS =
            List.of("verdict", "label", "isQualified", "passed", "recommendation", "totalScore", "rank", "evidence");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    private record Hr(String token, UUID userId, UUID companyId) {
    }

    private enum Consent {
        NOT_GIVEN,
        ACTIVE,
        REVOKED
    }

    // ---- Du lieu ----

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Hr hrWithoutCompany() throws Exception {
        String email = uniqueEmail("h15a-hr");
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nha Tuyen Dung\",\"phone\":\"0900000000\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new Hr(
                body(login).get("accessToken").asString(), userRepository.findByEmail(email).orElseThrow().getId(), null);
    }

    private Hr hr() throws Exception {
        Hr hr = hrWithoutCompany();
        Company company = new Company();
        company.setOwnerId(hr.userId());
        company.setName("Cong ty " + UUID.randomUUID());
        return new Hr(hr.token(), hr.userId(), companyRepository.saveAndFlush(company).getId());
    }

    private UUID job(Hr hr) {
        Job job = new Job();
        job.setCompanyId(hr.companyId());
        job.setCreatedBy(hr.userId());
        job.setTitle("Nhan vien CSKH");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return jobRepository.saveAndFlush(job).getId();
    }

    private UUID application(UUID jobId, Consent consent, ApplicationStatus applicationStatus) {
        User candidate = new User();
        candidate.setEmail(uniqueEmail("h15a-c"));
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        candidate = userRepository.saveAndFlush(candidate);
        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.saveAndFlush(resume);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications
                    (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at,
                     talent_pool_consent, talent_pool_consent_at, talent_pool_consent_revoked_at)
                VALUES (?, ?, ?, ?, 1, ?, TRUE, now(), ?,
                        CASE WHEN ? THEN now() - interval '1 hour' END, CASE WHEN ? THEN now() END)
                """,
                id, jobId, candidate.getId(), resume.getId(), applicationStatus.name(),
                consent == Consent.ACTIVE, consent != Consent.NOT_GIVEN, consent == Consent.REVOKED);
        return id;
    }

    private UUID application(UUID jobId, Consent consent) {
        return application(jobId, consent, ApplicationStatus.PENDING);
    }

    private UUID insertPool(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO talent_pools (id, company_id, name) VALUES (?, ?, ?)", id, companyId, name);
        return id;
    }

    private UUID insertEntry(UUID poolId, UUID applicationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO talent_pool_entries (id, pool_id, application_id) VALUES (?, ?, ?)",
                id, poolId, applicationId);
        return id;
    }

    // Anh chup toan bo hang muc cua mot don: id -> removed_at (null = hieu luc). Dung de khang dinh "bang khong doi".
    private Map<UUID, Object> entries(UUID applicationId) {
        Map<UUID, Object> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, removed_at FROM talent_pool_entries WHERE application_id = ?",
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getTimestamp("removed_at"));
                },
                applicationId);
        return result;
    }

    private Set<UUID> activePools(UUID applicationId) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT pool_id FROM talent_pool_entries WHERE application_id = ? AND removed_at IS NULL",
                UUID.class, applicationId));
    }

    // ---- HTTP ----

    private String url(UUID applicationId) {
        return "/api/hr/applications/" + applicationId + "/talent-pools";
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        request = request.header("Authorization", "Bearer " + token);
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult replace(Hr hr, UUID applicationId, UUID... poolIds) throws Exception {
        List<String> ids = new ArrayList<>();
        for (UUID id : poolIds) {
            ids.add(id.toString());
        }
        return call(put(url(applicationId)), hr.token(), JSON.writeValueAsString(Map.of("poolIds", ids)));
    }

    private static void assertError(MvcResult result, int status, String code, String message) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(status);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo(code);
        if (message != null) {
            assertThat(json.get("message").asString()).isEqualTo(message);
        }
    }

    private static Map<String, Boolean> contains(JsonNode response) {
        Map<String, Boolean> result = new HashMap<>();
        response.get("pools").values().forEach(p -> result.put(p.get("id").asString(), p.get("contains").asBoolean()));
        return result;
    }

    private static void collectKeys(JsonNode node, List<String> sink) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                sink.add(entry.getKey());
                collectKeys(entry.getValue(), sink);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node.values()) {
                collectKeys(child, sink);
            }
        }
    }

    // ---- T7 ----

    @Test
    void t7_addActive_200_entryCreated_p6ShowsContains() throws Exception {
        Hr hr = hr();
        UUID applicationId = application(job(hr), Consent.ACTIVE);
        UUID poolB = insertPool(hr.companyId(), "beta");
        UUID poolA = insertPool(hr.companyId(), "Alpha");

        MvcResult result = replace(hr, applicationId, poolB);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(Set.copyOf(json.propertyNames())).containsExactlyInAnyOrder("consentState", "pools");
        assertThat(json.get("consentState").asString()).isEqualTo("ACTIVE");
        assertThat(json.get("pools").get(0).get("id").asString()).as("sap theo lower(name)").isEqualTo(poolA.toString());
        assertThat(Set.copyOf(json.get("pools").get(0).propertyNames()))
                .containsExactlyInAnyOrder("id", "name", "contains");
        assertThat(contains(json)).containsEntry(poolA.toString(), false).containsEntry(poolB.toString(), true);
        assertThat(activePools(applicationId)).containsExactly(poolB);

        JsonNode p6 = body(call(get(url(applicationId)), hr.token(), null));
        assertThat(p6).isEqualTo(json);
        List<String> keys = new ArrayList<>();
        collectKeys(p6, keys);
        assertThat(keys).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }

    // ---- T8 ----

    @Test
    void t8_addWhenNotGivenOrRevoked_409_noRow() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);
        UUID poolId = insertPool(hr.companyId(), "Kho");

        for (Consent consent : List.of(Consent.NOT_GIVEN, Consent.REVOKED)) {
            UUID applicationId = application(jobId, consent);
            assertError(replace(hr, applicationId, poolId), 409, "TALENT_POOL_CONSENT_REQUIRED", MSG_CONSENT);
            assertThat(entries(applicationId)).as(consent.name()).isEmpty();
            JsonNode p6 = body(call(get(url(applicationId)), hr.token(), null));
            assertThat(p6.get("consentState").asString()).isEqualTo(consent.name());
        }
    }

    // ---- T9 ----

    @Test
    void t9_strayEntry_whenNotActive_resendSame200_addMore409_dropIt200() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);
        UUID poolA = insertPool(hr.companyId(), "A");
        UUID poolB = insertPool(hr.companyId(), "B");

        for (Consent consent : List.of(Consent.NOT_GIVEN, Consent.REVOKED)) {
            UUID applicationId = application(jobId, consent);
            UUID stray = insertEntry(poolA, applicationId);
            Map<UUID, Object> before = entries(applicationId);

            MvcResult same = replace(hr, applicationId, poolA);
            assertThat(same.getResponse().getStatus()).as(consent + " gui lai dung tap").isEqualTo(200);
            assertThat(contains(body(same))).as("R-M6: khong ACTIVE -> contains false").containsValue(false)
                    .doesNotContainValue(true);
            assertThat(entries(applicationId)).isEqualTo(before);

            assertError(replace(hr, applicationId, poolA, poolB), 409, "TALENT_POOL_CONSENT_REQUIRED", MSG_CONSENT);
            assertThat(entries(applicationId)).isEqualTo(before);

            assertThat(replace(hr, applicationId, poolB).getResponse().getStatus())
                    .as("vua go A vua them B -> van chan vi co them")
                    .isEqualTo(409);
            assertThat(entries(applicationId)).isEqualTo(before);

            assertThat(replace(hr, applicationId).getResponse().getStatus()).as(consent + " go").isEqualTo(200);
            assertThat(entries(applicationId).get(stray)).as("removed_at duoc dat").isNotNull();
        }
    }

    // ---- T10 ----

    @Test
    void t10_invalidSelection_400_tableUnchanged() throws Exception {
        Hr hr = hr();
        UUID applicationId = application(job(hr), Consent.ACTIVE);
        UUID poolId = insertPool(hr.companyId(), "Kho");
        insertEntry(poolId, applicationId);
        Map<UUID, Object> before = entries(applicationId);
        String marker = "BIMAT-" + UUID.randomUUID();

        List<String> bodies = List.of(
                "{}",
                "{\"poolIds\": null}",
                "{\"poolIds\": [null]}",
                "{\"poolIds\": [\"" + poolId + "\", null]}",
                "{\"poolIds\": \"" + marker + "\"}",
                "{\"poolIds\": [\"" + marker + "\"]}",
                "{\"poolIds\": [123]}",
                "{\"poolIds\": [\"" + poolId + "\"",
                "[\"" + poolId + "\"]",
                "{\"poolIds\": [\"" + poolId + "\", \"" + poolId.toString().toUpperCase() + "\"]}");
        for (String json : bodies) {
            MvcResult result = call(put(url(applicationId)), hr.token(), json);
            assertError(result, 400, "INVALID_TALENT_POOL_SELECTION", MSG_SELECTION);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(marker);
            assertThat(entries(applicationId)).as(json).isEqualTo(before);
        }
        assertError(call(put(url(applicationId)), hr.token(), null), 400, "INVALID_TALENT_POOL_SELECTION", null);
        assertThat(entries(applicationId)).isEqualTo(before);

        MvcResult cleared = replace(hr, applicationId);
        assertThat(cleared.getResponse().getStatus()).isEqualTo(200);
        assertThat(activePools(applicationId)).as("[] = go khoi moi kho").isEmpty();
    }

    // ---- T11 ----

    @Test
    void t11_atomic_anyBadPool_rejectsWholeRequest_404Before403() throws Exception {
        Hr hr = hr();
        UUID applicationId = application(job(hr), Consent.ACTIVE);
        UUID kept = insertPool(hr.companyId(), "Dang chua");
        UUID fresh = insertPool(hr.companyId(), "Moi");
        UUID deleted = insertPool(hr.companyId(), "Da xoa");
        jdbcTemplate.update("UPDATE talent_pools SET deleted_at = now() WHERE id = ?", deleted);
        UUID foreign = insertPool(hr().companyId(), "Cong ty khac");
        insertEntry(kept, applicationId);
        Map<UUID, Object> before = entries(applicationId);

        // Moi request bo "kept" (le ra go) va them "fresh" (le ra them) - loi thi KHONG duoc lam phan nao.
        assertError(replace(hr, applicationId, fresh, UUID.randomUUID()), 404, "TALENT_POOL_NOT_FOUND", null);
        assertThat(entries(applicationId)).isEqualTo(before);
        assertError(replace(hr, applicationId, fresh, deleted), 404, "TALENT_POOL_NOT_FOUND", null);
        assertThat(entries(applicationId)).isEqualTo(before);
        assertThat(replace(hr, applicationId, fresh, foreign).getResponse().getStatus()).isEqualTo(403);
        assertThat(entries(applicationId)).isEqualTo(before);
        assertError(replace(hr, applicationId, foreign, UUID.randomUUID()), 404, "TALENT_POOL_NOT_FOUND", null);
        assertThat(entries(applicationId)).isEqualTo(before);
        assertThat(activePools(applicationId)).containsExactly(kept);
    }

    // ---- T12 ----

    @Test
    void t12_applicationAccess_foreign403_missing404_noCompany404() throws Exception {
        Hr hr = hr();
        UUID poolId = insertPool(hr.companyId(), "Kho");
        Hr other = hr();
        UUID foreignApp = application(job(other), Consent.ACTIVE);
        UUID missing = UUID.randomUUID();
        Hr noCompany = hrWithoutCompany();

        assertThat(call(get(url(foreignApp)), hr.token(), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(replace(hr, foreignApp, poolId).getResponse().getStatus()).isEqualTo(403);
        assertThat(entries(foreignApp)).isEmpty();
        assertError(call(get(url(missing)), hr.token(), null), 404, "APPLICATION_NOT_FOUND", null);
        assertError(replace(hr, missing, poolId), 404, "APPLICATION_NOT_FOUND", null);
        assertError(call(get(url(foreignApp)), noCompany.token(), null), 404, "COMPANY_NOT_FOUND", null);
        assertError(replace(noCompany, foreignApp), 404, "COMPANY_NOT_FOUND", null);
    }

    // ---- T13 ----

    @Test
    void t13_everyApplicationStatus_canBeAdded_statusChangeKeepsEntry() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);
        UUID poolId = insertPool(hr.companyId(), "Kho");

        for (ApplicationStatus status : ApplicationStatus.values()) {
            UUID applicationId = application(jobId, Consent.ACTIVE, status);
            assertThat(replace(hr, applicationId, poolId).getResponse().getStatus()).as(status.name()).isEqualTo(200);
            assertThat(activePools(applicationId)).containsExactly(poolId);
        }

        UUID applicationId = application(jobId, Consent.ACTIVE);
        replace(hr, applicationId, poolId);
        jdbcTemplate.update("UPDATE job_applications SET status = 'REJECTED' WHERE id = ?", applicationId);
        assertThat(activePools(applicationId)).as("doi trang thai khong go muc").containsExactly(poolId);
    }

    // ---- T14 ----

    @Test
    void t14_idempotent_sameSetTwice_noNewRow_removeThenAdd_newRowOldKept() throws Exception {
        Hr hr = hr();
        UUID applicationId = application(job(hr), Consent.ACTIVE);
        UUID poolA = insertPool(hr.companyId(), "A");
        UUID poolB = insertPool(hr.companyId(), "B");

        assertThat(replace(hr, applicationId, poolA, poolB).getResponse().getStatus()).isEqualTo(200);
        Map<UUID, Object> afterFirst = entries(applicationId);
        assertThat(afterFirst).hasSize(2);
        assertThat(replace(hr, applicationId, poolB, poolA).getResponse().getStatus()).isEqualTo(200);
        assertThat(entries(applicationId)).as("cung tap -> khong dong moi, khong go").isEqualTo(afterFirst);

        assertThat(replace(hr, applicationId, poolB).getResponse().getStatus()).isEqualTo(200);
        assertThat(replace(hr, applicationId, poolA, poolB).getResponse().getStatus()).isEqualTo(200);
        Map<UUID, Object> afterReAdd = entries(applicationId);
        assertThat(afterReAdd).as("them lai A -> dong moi").hasSize(3);
        assertThat(afterReAdd.values().stream().filter(v -> v != null).count()).as("dong A cu giu removed_at").isEqualTo(1);
        assertThat(activePools(applicationId)).containsExactlyInAnyOrder(poolA, poolB);
    }
}

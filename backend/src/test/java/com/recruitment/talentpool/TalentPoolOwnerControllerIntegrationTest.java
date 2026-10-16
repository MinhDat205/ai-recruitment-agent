package com.recruitment.talentpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

// FR-H15 P1-P5, P8, P9 - T2, T4, T5, T6, T17, T18, T19, T20 (REQUIREMENT muc 7.1). @Transactional: moi @Test rollback
// rieng. Vi vay now() (added_at mac dinh) giong nhau trong mot test (CLAUDE.md muc 3c) - test thu tu (T17) lui added_at
// tuong minh. HR dang ky + dang nhap qua API (can JWT that); cong ty, tin, ung vien, don dung bang repository/JDBC
// (khuon TalentPoolConsentSchemaConstraintIntegrationTest), don dat ba cot dong y tuong minh.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TalentPoolOwnerControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE = "/api/hr/talent-pools";
    private static final String MSG_INVALID_NAME = "Tên kho phải có từ 1 đến 100 ký tự.";
    private static final String MSG_NAME_TAKEN = "Công ty đã có kho ứng viên với tên này.";
    private static final String MSG_POOL_NOT_FOUND = "Không tìm thấy kho ứng viên.";
    private static final String MSG_ENTRY_NOT_FOUND = "Hồ sơ này không có trong kho.";

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

    private String register(String role, String email) throws Exception {
        String extra = role.equals("hr") ? ",\"phone\":\"0900000000\"" : "";
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nguoi Dung\"%s}"
                                .formatted(email, extra)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return body(login).get("accessToken").asString();
    }

    private Hr hrWithoutCompany() throws Exception {
        String email = uniqueEmail("h15o-hr");
        String token = register("hr", email);
        return new Hr(token, userRepository.findByEmail(email).orElseThrow().getId(), null);
    }

    private Hr hr() throws Exception {
        Hr hr = hrWithoutCompany();
        Company company = new Company();
        company.setOwnerId(hr.userId());
        company.setName("Cong ty " + UUID.randomUUID());
        return new Hr(hr.token(), hr.userId(), companyRepository.saveAndFlush(company).getId());
    }

    private String candidateToken() throws Exception {
        return register("candidate", uniqueEmail("h15o-uv"));
    }

    private UUID job(Hr hr, String title) {
        Job job = new Job();
        job.setCompanyId(hr.companyId());
        job.setCreatedBy(hr.userId());
        job.setTitle(title);
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return jobRepository.saveAndFlush(job).getId();
    }

    private UUID application(UUID jobId, String candidateName, Consent consent) {
        User candidate = new User();
        candidate.setEmail(uniqueEmail("h15o-c"));
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName(candidateName);
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
                VALUES (?, ?, ?, ?, 1, 'PENDING', TRUE, now(), ?,
                        CASE WHEN ? THEN now() - interval '1 hour' END, CASE WHEN ? THEN now() END)
                """,
                id, jobId, candidate.getId(), resume.getId(),
                consent == Consent.ACTIVE, consent != Consent.NOT_GIVEN, consent == Consent.REVOKED);
        return id;
    }

    private UUID application(UUID jobId, Consent consent) {
        return application(jobId, "Ung Vien " + UUID.randomUUID().toString().substring(0, 8), consent);
    }

    // Kho va muc dung thang bang JDBC khi test khong phai la test cua chinh endpoint tao/them.
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

    // Muc lech (R-M5): co muc hieu luc nhung don chuyen sang REVOKED bang SQL, KHONG qua R-C1. Dat CA co va revoked_at
    // (chi dat co se vo CHECK chk_talent_pool_consent_state cua V14).
    private void revokeBySql(UUID applicationId) {
        jdbcTemplate.update(
                """
                UPDATE job_applications SET talent_pool_consent = FALSE, talent_pool_consent_revoked_at = now()
                WHERE id = ?
                """,
                applicationId);
    }

    private Timestamp removedAt(UUID entryId) {
        return jdbcTemplate.queryForObject(
                "SELECT removed_at FROM talent_pool_entries WHERE id = ?", Timestamp.class, entryId);
    }

    private int entryRowCount(UUID poolId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_pool_entries WHERE pool_id = ?", Integer.class, poolId);
    }

    // ---- HTTP ----

    private MvcResult call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String nameJson(String name) {
        return JSON.writeValueAsString(java.util.Map.of("name", name));
    }

    private MvcResult create(Hr hr, String name) throws Exception {
        return call(post(BASE), hr.token(), nameJson(name));
    }

    private UUID createOk(Hr hr, String name) throws Exception {
        MvcResult result = create(hr, name);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return UUID.fromString(body(result).get("id").asString());
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

    // ---- T2 ----

    @Test
    void t2_create_201_strippedName_zeroEntries() throws Exception {
        Hr hr = hr();

        MvcResult result = create(hr, "   Backend tiềm năng \t");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode json = body(result);
        assertThat(Set.copyOf(json.propertyNames())).containsExactlyInAnyOrder("id", "name", "entryCount", "createdAt");
        assertThat(json.get("name").asString()).isEqualTo("Backend tiềm năng");
        assertThat(json.get("entryCount").asLong()).isZero();
        assertThat(json.get("createdAt").isNull()).isFalse();
    }

    @Test
    void t2_nameLengthBoundaries_0_1_100_101() throws Exception {
        Hr hr = hr();

        assertError(create(hr, ""), 400, "INVALID_TALENT_POOL_NAME", MSG_INVALID_NAME);
        assertError(create(hr, "    "), 400, "INVALID_TALENT_POOL_NAME", MSG_INVALID_NAME);
        assertThat(create(hr, "A").getResponse().getStatus()).isEqualTo(201);
        assertThat(create(hr, "b".repeat(100)).getResponse().getStatus()).isEqualTo(201);
        assertError(create(hr, "c".repeat(101)), 400, "INVALID_TALENT_POOL_NAME", MSG_INVALID_NAME);
        assertError(call(post(BASE), hr.token(), "{}"), 400, "INVALID_TALENT_POOL_NAME", MSG_INVALID_NAME);
    }

    @Test
    void t2_duplicateIgnoringCase_409_otherCompanyOrDeletedPool_201() throws Exception {
        Hr hr = hr();
        UUID first = createOk(hr, "Thực tập 2027");

        assertError(create(hr, "THỰC TẬP 2027"), 409, "TALENT_POOL_NAME_TAKEN", MSG_NAME_TAKEN);
        assertError(create(hr, "  thực tập 2027  "), 409, "TALENT_POOL_NAME_TAKEN", MSG_NAME_TAKEN);
        assertThat(create(hr(), "Thực tập 2027").getResponse().getStatus()).as("cong ty khac").isEqualTo(201);

        assertThat(call(delete(BASE + "/" + first), hr.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(create(hr, "thực tập 2027").getResponse().getStatus()).as("kho cu da xoa").isEqualTo(201);
    }

    @Test
    void t2_unreadableBody_400_withoutEchoingInput_onCreateAndRename() throws Exception {
        Hr hr = hr();
        UUID poolId = createOk(hr, "Kho goc");
        String marker = "BIMAT-" + UUID.randomUUID();

        List<MvcResult> results = new ArrayList<>();
        results.add(call(post(BASE), hr.token(), "{\"name\": \"" + marker + "\""));
        results.add(call(post(BASE), hr.token(), "{\"name\": {\"x\": \"" + marker + "\"}}"));
        results.add(call(post(BASE), hr.token(), null));
        results.add(call(patch(BASE + "/" + poolId), hr.token(), "{\"name\": \"" + marker + "\""));
        results.add(call(patch(BASE + "/" + poolId), hr.token(), "[\"" + marker + "\"]"));
        for (MvcResult result : results) {
            assertError(result, 400, "INVALID_TALENT_POOL_NAME", MSG_INVALID_NAME);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(marker);
        }
    }

    // ---- T4 ----

    @Test
    void t4_rename_ok_ownCaseChange_ok_clash409_deleted404_foreign403() throws Exception {
        Hr hr = hr();
        UUID poolId = createOk(hr, "Kho cu");
        createOk(hr, "Kho khac");

        MvcResult renamed = call(patch(BASE + "/" + poolId), hr.token(), nameJson("  Kho moi  "));
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(renamed).get("name").asString()).isEqualTo("Kho moi");

        MvcResult caseOnly = call(patch(BASE + "/" + poolId), hr.token(), nameJson("KHO MOI"));
        assertThat(caseOnly.getResponse().getStatus()).as("doi hoa thuong ten cua chinh minh").isEqualTo(200);
        assertThat(body(caseOnly).get("name").asString()).isEqualTo("KHO MOI");

        assertError(
                call(patch(BASE + "/" + poolId), hr.token(), nameJson("kho khac")),
                409, "TALENT_POOL_NAME_TAKEN", MSG_NAME_TAKEN);
        assertError(
                call(patch(BASE + "/" + poolId), hr.token(), nameJson("x".repeat(101))),
                400, "INVALID_TALENT_POOL_NAME", MSG_INVALID_NAME);

        Hr other = hr();
        assertThat(call(patch(BASE + "/" + poolId), other.token(), nameJson("Chiem kho")).getResponse().getStatus())
                .isEqualTo(403);

        assertThat(call(delete(BASE + "/" + poolId), hr.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertError(
                call(patch(BASE + "/" + poolId), hr.token(), nameJson("Hoi sinh")),
                404, "TALENT_POOL_NOT_FOUND", MSG_POOL_NOT_FOUND);
    }

    // ---- T5 ----

    @Test
    void t5_delete_204_thenEveryEndpoint404_rowsKept_entriesUntouched() throws Exception {
        Hr hr = hr();
        UUID poolId = createOk(hr, "Kho se xoa");
        UUID applicationId = application(job(hr, "Tin A"), Consent.ACTIVE);
        UUID entryId = insertEntry(poolId, applicationId);

        assertThat(call(delete(BASE + "/" + poolId), hr.token(), null).getResponse().getStatus()).isEqualTo(204);

        String pool = BASE + "/" + poolId;
        assertError(call(get(pool), hr.token(), null), 404, "TALENT_POOL_NOT_FOUND", MSG_POOL_NOT_FOUND);
        assertError(call(patch(pool), hr.token(), nameJson("X")), 404, "TALENT_POOL_NOT_FOUND", null);
        assertError(call(delete(pool), hr.token(), null), 404, "TALENT_POOL_NOT_FOUND", null);
        assertError(call(get(pool + "/entries"), hr.token(), null), 404, "TALENT_POOL_NOT_FOUND", null);
        assertError(
                call(delete(pool + "/entries/" + applicationId), hr.token(), null), 404, "TALENT_POOL_NOT_FOUND", null);

        JsonNode list = body(call(get(BASE), hr.token(), null));
        assertThat(list.size()).isZero();
        JsonNode p6 = body(call(get("/api/hr/applications/" + applicationId + "/talent-pools"), hr.token(), null));
        assertThat(p6.get("pools").size()).isZero();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT deleted_at FROM talent_pools WHERE id = ?", Timestamp.class, poolId))
                .as("xoa mem, hang con")
                .isNotNull();
        assertThat(removedAt(entryId)).as("muc khong bi cham").isNull();
    }

    // ---- T6 ----

    @Test
    void t6_list_ownNonDeletedOnly_sortedByLowerName_entryCountReadPathOnly() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, "Tin A");
        UUID beta = insertPool(hr.companyId(), "beta");
        UUID alpha = insertPool(hr.companyId(), "Alpha");
        UUID gamma = insertPool(hr.companyId(), "Gamma");
        UUID deleted = insertPool(hr.companyId(), "Aaa da xoa");
        jdbcTemplate.update("UPDATE talent_pools SET deleted_at = now() WHERE id = ?", deleted);
        insertPool(hr().companyId(), "Aaa cong ty khac");

        insertEntry(alpha, application(jobId, Consent.ACTIVE));
        insertEntry(alpha, application(jobId, Consent.ACTIVE));
        UUID removed = insertEntry(alpha, application(jobId, Consent.ACTIVE));
        jdbcTemplate.update("UPDATE talent_pool_entries SET removed_at = now() WHERE id = ?", removed);
        UUID strayApp = application(jobId, Consent.ACTIVE);
        insertEntry(alpha, strayApp);
        revokeBySql(strayApp);
        insertEntry(beta, application(jobId, Consent.ACTIVE));

        MvcResult result = call(get(BASE), hr.token(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode list = body(result);
        List<String> ids = new ArrayList<>();
        list.values().forEach(node -> ids.add(node.get("id").asString()));
        assertThat(ids).containsExactly(alpha.toString(), beta.toString(), gamma.toString());
        assertThat(list.get(0).get("entryCount").asLong()).as("bo muc da go va muc lech").isEqualTo(2);
        assertThat(list.get(1).get("entryCount").asLong()).isEqualTo(1);
        assertThat(list.get(2).get("entryCount").asLong()).isZero();
    }

    @Test
    void t6_hrWithoutCompany_404() throws Exception {
        assertError(call(get(BASE), hrWithoutCompany().token(), null), 404, "COMPANY_NOT_FOUND", null);
    }

    // ---- T17 ----

    @Test
    void t17_entriesPagination_defaults_bounds_order_fields() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, "Chuyên viên Chăm sóc khách hàng");
        UUID poolId = insertPool(hr.companyId(), "Kho phan trang");
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        List<UUID> applications = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID applicationId = application(jobId, "Ung Vien " + i, Consent.ACTIVE);
            UUID entryId = insertEntry(poolId, applicationId);
            // Lui added_at tuong minh (now() giong nhau trong transaction test): don i them luc base + i gio.
            jdbcTemplate.update(
                    "UPDATE talent_pool_entries SET added_at = ? WHERE id = ?",
                    Timestamp.from(base.plusSeconds(3600L * i)), entryId);
            applications.add(applicationId);
        }
        String url = BASE + "/" + poolId + "/entries";

        JsonNode page0 = body(call(get(url), hr.token(), null));
        assertThat(page0.get("size").asInt()).isEqualTo(10);
        assertThat(page0.get("page").asInt()).isZero();
        assertThat(page0.get("totalElements").asLong()).isEqualTo(12);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("items").size()).isEqualTo(10);
        assertThat(page0.get("items").get(0).get("applicationId").asString())
                .as("moi them nhat truoc")
                .isEqualTo(applications.get(11).toString());
        JsonNode item = page0.get("items").get(0);
        assertThat(Set.copyOf(item.propertyNames()))
                .containsExactlyInAnyOrder(
                        "applicationId", "candidateName", "jobId", "jobTitle", "jobDeleted", "applicationStatus",
                        "appliedAt", "addedAt");
        assertThat(item.get("candidateName").asString()).isEqualTo("Ung Vien 11");
        assertThat(item.get("jobTitle").asString()).isEqualTo("Chuyên viên Chăm sóc khách hàng");
        assertThat(item.get("jobDeleted").asBoolean()).isFalse();
        assertThat(item.get("applicationStatus").asString()).isEqualTo("PENDING");
        assertThat(Instant.parse(item.get("addedAt").asString())).isEqualTo(base.plusSeconds(3600L * 11));

        JsonNode page1 = body(call(get(url + "?page=1"), hr.token(), null));
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(1).get("applicationId").asString()).isEqualTo(applications.get(0).toString());

        assertThat(body(call(get(url + "?size=50"), hr.token(), null)).get("size").asInt()).isEqualTo(50);
        assertThat(body(call(get(url + "?size=51"), hr.token(), null)).get("size").asInt()).isEqualTo(50);
        assertThat(body(call(get(url + "?size=0"), hr.token(), null)).get("size").asInt()).isEqualTo(10);
        assertThat(body(call(get(url + "?page=-1"), hr.token(), null)).get("page").asInt()).isZero();
    }

    @Test
    void t17_jobSoftDeleted_flagged_strayEntryHiddenButRemovable() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, "Tin se xoa");
        UUID poolId = insertPool(hr.companyId(), "Kho");
        UUID visibleApp = application(jobId, Consent.ACTIVE);
        insertEntry(poolId, visibleApp);
        UUID strayApp = application(jobId, Consent.ACTIVE);
        UUID strayEntry = insertEntry(poolId, strayApp);
        revokeBySql(strayApp);
        jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE id = ?", jobId);
        String url = BASE + "/" + poolId + "/entries";

        JsonNode page = body(call(get(url), hr.token(), null));
        assertThat(page.get("totalElements").asLong()).as("muc lech khong hien").isEqualTo(1);
        assertThat(page.get("items").get(0).get("applicationId").asString()).isEqualTo(visibleApp.toString());
        assertThat(page.get("items").get(0).get("jobDeleted").asBoolean()).isTrue();
        assertThat(body(call(get(BASE + "/" + poolId), hr.token(), null)).get("entryCount").asLong()).isEqualTo(1);
        JsonNode p6 = body(call(get("/api/hr/applications/" + strayApp + "/talent-pools"), hr.token(), null));
        assertThat(p6.get("consentState").asString()).isEqualTo("REVOKED");
        assertThat(p6.get("pools").get(0).get("contains").asBoolean()).as("R-M6").isFalse();

        // R-M5: P9 khong loc dong y.
        assertThat(call(delete(url + "/" + strayApp), hr.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(removedAt(strayEntry)).isNotNull();

        // R-M5: P7 [] cung go duoc muc lech.
        UUID strayApp2 = application(jobId, Consent.ACTIVE);
        UUID strayEntry2 = insertEntry(poolId, strayApp2);
        revokeBySql(strayApp2);
        MvcResult cleared = call(
                put("/api/hr/applications/" + strayApp2 + "/talent-pools"), hr.token(), "{\"poolIds\": []}");
        assertThat(cleared.getResponse().getStatus()).isEqualTo(200);
        assertThat(removedAt(strayEntry2)).isNotNull();
    }

    // ---- T18 ----

    @Test
    void t18_removeEntry_204_twice404_neverInPool404_deletedPool404_foreign403() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, "Tin A");
        UUID poolId = insertPool(hr.companyId(), "Kho");
        UUID applicationId = application(jobId, Consent.ACTIVE);
        UUID entryId = insertEntry(poolId, applicationId);
        String url = BASE + "/" + poolId + "/entries/";

        assertThat(call(delete(url + applicationId), hr.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(removedAt(entryId)).isNotNull();
        assertError(call(delete(url + applicationId), hr.token(), null),
                404, "TALENT_POOL_ENTRY_NOT_FOUND", MSG_ENTRY_NOT_FOUND);
        assertError(call(delete(url + application(jobId, Consent.ACTIVE)), hr.token(), null),
                404, "TALENT_POOL_ENTRY_NOT_FOUND", MSG_ENTRY_NOT_FOUND);

        UUID otherEntryApp = application(jobId, Consent.ACTIVE);
        UUID otherEntry = insertEntry(poolId, otherEntryApp);
        assertThat(call(delete(url + otherEntryApp), hr().token(), null).getResponse().getStatus())
                .as("kho cong ty khac")
                .isEqualTo(403);
        assertThat(removedAt(otherEntry)).isNull();

        // Xoa qua P5 (khong UPDATE bang JDBC): cac request trong test dung CHUNG transaction, entity kho da nap o request
        // truoc nam trong persistence context - JDBC di vong qua JPA se khien findById tra ban cu.
        assertThat(call(delete(BASE + "/" + poolId), hr.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertError(call(delete(url + otherEntryApp), hr.token(), null), 404, "TALENT_POOL_NOT_FOUND", null);
        assertThat(removedAt(otherEntry)).isNull();
    }

    @Test
    void t18_getForeignPool403_missingPool404() throws Exception {
        Hr hr = hr();
        UUID poolId = insertPool(hr.companyId(), "Kho");

        assertThat(call(get(BASE + "/" + poolId), hr().token(), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(get(BASE + "/" + poolId + "/entries"), hr().token(), null).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(call(delete(BASE + "/" + poolId), hr().token(), null).getResponse().getStatus()).isEqualTo(403);
        assertError(call(get(BASE + "/" + UUID.randomUUID()), hr.token(), null),
                404, "TALENT_POOL_NOT_FOUND", MSG_POOL_NOT_FOUND);
        assertThat(entryRowCount(poolId)).isZero();
    }

    // ---- T19 ----

    @Test
    void t19_candidateToken403_noToken401_onEveryEndpoint() throws Exception {
        Hr hr = hr();
        UUID poolId = insertPool(hr.companyId(), "Kho");
        UUID applicationId = application(job(hr, "Tin A"), Consent.ACTIVE);
        String candidate = candidateToken();
        String pool = BASE + "/" + poolId;
        String app = "/api/hr/applications/" + applicationId + "/talent-pools";

        List<MockHttpServletRequestBuilder> requests = List.of(
                get(BASE),
                post(BASE).contentType(MediaType.APPLICATION_JSON).content(nameJson("X")),
                get(pool),
                patch(pool).contentType(MediaType.APPLICATION_JSON).content(nameJson("X")),
                delete(pool),
                get(app),
                put(app).contentType(MediaType.APPLICATION_JSON).content("{\"poolIds\":[]}"),
                get(pool + "/entries"),
                delete(pool + "/entries/" + applicationId));
        for (MockHttpServletRequestBuilder request : requests) {
            assertThat(mockMvc.perform(request.header("Authorization", "Bearer " + candidate))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(403);
        }
        List<MockHttpServletRequestBuilder> anonymous = List.of(
                get(BASE), post(BASE), get(pool), patch(pool), delete(pool), get(app), put(app), get(pool + "/entries"),
                delete(pool + "/entries/" + applicationId));
        for (MockHttpServletRequestBuilder request : anonymous) {
            MvcResult result = mockMvc.perform(request).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(body(result).get("error").asString()).isEqualTo("UNAUTHENTICATED");
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM talent_pools WHERE id = ?", String.class, poolId))
                .isEqualTo("Kho");
    }

    // ---- T20 ----

    @Test
    void t20_jobSoftDeleted_entryStillListed_hrDetailStill200() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, "Tin se xoa");
        UUID poolId = insertPool(hr.companyId(), "Kho");
        UUID applicationId = application(jobId, Consent.ACTIVE);
        insertEntry(poolId, applicationId);
        assertThat(call(delete("/api/hr/jobs/" + jobId), hr.token(), null).getResponse().getStatus()).isEqualTo(204);

        JsonNode page = body(call(get(BASE + "/" + poolId + "/entries"), hr.token(), null));
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("jobDeleted").asBoolean()).isTrue();
        assertThat(call(get("/api/hr/applications/" + applicationId), hr.token(), null).getResponse().getStatus())
                .isEqualTo(200);
    }
}

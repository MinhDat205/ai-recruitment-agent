package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U09 F4 (PATCH /api/candidates/applications/{id}/talent-pool-consent/revoke) va khoa talentPoolConsent cua E1 -
// T10, T11, T12, T16 (phan F4, E1), T17 (phan F4).
// KHONG @Transactional toan lop: T11 can rut don (withdraw, save entity) THAT SU flush + commit roi moi doc lai bang JDBC;
// trong transaction bao ngoai, lan flush do khong xay ra truoc khi khang dinh va test se xanh rong. Du lieu ngau nhien
// rieng, khong don sau test (L2 cua FR-H10).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TalentPoolConsentIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PDF_CONTENT = "%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8);
    private static final String MSG_NOT_ACTIVE = "Đơn này hiện không có đồng ý lưu hồ sơ để rút.";
    private static final List<String> FORBIDDEN_KEYS =
            List.of("verdict", "label", "isQualified", "passed", "recommendation");

    // Q19 - updated_at doi do trigger V1 o MOI cau UPDATE, loai khoi so sanh; hai cot rut dong y doi co chu dich.
    private static final Set<String> CHANGED_BY_REVOKE =
            Set.of("updated_at", "talent_pool_consent", "talent_pool_consent_revoked_at");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Candidate(String token, String resumeId) {
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("accessToken").asString();
    }

    private String hrWithCompany() throws Exception {
        String email = uniqueEmail("u09-k-hr");
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nha Tuyen Dung\",\"phone\":\"0900000000\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        String token = login(email);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
        return token;
    }

    private String createOpenJob(String hrToken) throws Exception {
        String body =
                """
                {
                  "job": {"title":"Nhan vien CSKH %s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao ung vien.","senderName":"Phong Nhan Su"}
                }
                """
                        .formatted(UUID.randomUUID());
        String jobId = body(mockMvc
                        .perform(post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString();
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kinh nghiem\",\"weight\":100}"))
                .andExpect(status().isCreated());
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());
        return jobId;
    }

    private Candidate newCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Ung Vien\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        String token = login(email);
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF_CONTENT);
        String resumeId = body(mockMvc
                        .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString();
        return new Candidate(token, resumeId);
    }

    // Tin khong co cau hoi: JSON kieu cu + talentPoolConsent (R-R1, R-K1).
    private String apply(Candidate candidate, String jobId, boolean consent) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":\"%s\",\"resumeId\":\"%s\",\"aiConsent\":true,\"talentPoolConsent\":%s}"
                                .formatted(jobId, candidate.resumeId(), consent)))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        return body(result).get("id").asString();
    }

    private MvcResult revoke(String token, String applicationId) throws Exception {
        var request = patch("/api/candidates/applications/" + applicationId + "/talent-pool-consent/revoke");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode detail(Candidate candidate, String applicationId) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/candidates/applications/" + applicationId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result);
    }

    private Map<String, Object> row(String applicationId) {
        return new HashMap<>(jdbcTemplate.queryForMap(
                "SELECT * FROM job_applications WHERE id = ?", UUID.fromString(applicationId)));
    }

    private int count(String sql, String applicationId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, UUID.fromString(applicationId));
    }

    // Thong bao lien quan toi don: theo entity_id, VA moi thong bao cua chinh ung vien (rut dong y khong duoc tao gi).
    private int notificationCount(String applicationId) {
        UUID id = UUID.fromString(applicationId);
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM notifications
                WHERE entity_id = ? OR user_id = (SELECT candidate_id FROM job_applications WHERE id = ?)
                """,
                Integer.class,
                id,
                id);
    }

    private int historyCount(String applicationId) {
        return count("SELECT COUNT(*) FROM application_status_history WHERE application_id = ?", applicationId);
    }

    private static void assertConsentError(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo("TALENT_POOL_CONSENT_NOT_ACTIVE");
        assertThat(json.get("message").asString()).isEqualTo(MSG_NOT_ACTIVE);
    }

    // ---- T10 duong: moi trang thai don, chi hai cot dong y doi (B4) ----

    @Test
    void t10_revokeActiveConsent_inEveryApplicationStatus_changesOnlyConsentColumns() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);

        for (ApplicationStatus status : ApplicationStatus.values()) {
            Candidate candidate = newCandidate("u09-k-t10");
            String applicationId = apply(candidate, jobId, true);
            if (status != ApplicationStatus.PENDING) {
                jdbcTemplate.update(
                        "UPDATE job_applications SET status = ? WHERE id = ?", status.name(), UUID.fromString(applicationId));
            }
            Map<String, Object> before = row(applicationId);
            int historyBefore = historyCount(applicationId);
            int notificationsBefore = notificationCount(applicationId);
            int snapshotBefore = count(
                    "SELECT COUNT(*) FROM application_screening_answers WHERE application_id = ?", applicationId);

            MvcResult result = revoke(candidate.token(), applicationId);

            assertThat(result.getResponse().getStatus()).as("trang thai %s", status).isEqualTo(200);
            JsonNode json = body(result);
            assertThat(json.get("state").asString()).isEqualTo("REVOKED");
            assertThat(json.get("revokedAt").isNull()).isFalse();
            assertThat(Instant.parse(json.get("consentedAt").asString()))
                    .as("consent_at giu nguyen de audit")
                    .isEqualTo(((Timestamp) before.get("talent_pool_consent_at")).toInstant());

            Map<String, Object> after = row(applicationId);
            assertThat(after.get("talent_pool_consent")).isEqualTo(false);
            assertThat(after.get("talent_pool_consent_revoked_at")).isNotNull();
            for (String column : before.keySet()) {
                if (!CHANGED_BY_REVOKE.contains(column)) {
                    assertThat(after.get(column)).as("cot %s, trang thai %s", column, status).isEqualTo(before.get(column));
                }
            }
            assertThat(after.get("status")).isEqualTo(status.name());
            assertThat(historyCount(applicationId)).as("khong ghi lich su").isEqualTo(historyBefore);
            assertThat(notificationCount(applicationId)).as("khong thong bao (T17)").isEqualTo(notificationsBefore);
            assertThat(count("SELECT COUNT(*) FROM application_screening_answers WHERE application_id = ?", applicationId))
                    .isEqualTo(snapshotBefore);
        }
    }

    // ---- T10 am ----

    @Test
    void t10_revokeTwice_or_notGiven_conflict() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        Candidate active = newCandidate("u09-k-twice");
        String activeId = apply(active, jobId, true);

        assertThat(revoke(active.token(), activeId).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> afterFirst = row(activeId);
        assertConsentError(revoke(active.token(), activeId));
        assertThat(row(activeId)).as("lan hai khong doi gi").isEqualTo(afterFirst);

        Candidate notGiven = newCandidate("u09-k-none");
        String notGivenId = apply(notGiven, jobId, false);
        Map<String, Object> beforeNotGiven = row(notGivenId);
        assertConsentError(revoke(notGiven.token(), notGivenId));
        assertThat(row(notGivenId)).isEqualTo(beforeNotGiven);
    }

    @Test
    void t10_otherCandidatesApplication_404_rowUntouched_missing404() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        Candidate owner = newCandidate("u09-k-owner");
        Candidate stranger = newCandidate("u09-k-strg");
        String applicationId = apply(owner, jobId, true);
        Map<String, Object> before = row(applicationId);

        MvcResult foreign = revoke(stranger.token(), applicationId);
        MvcResult missing = revoke(owner.token(), UUID.randomUUID().toString());

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(foreign).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(row(applicationId)).as("khong lot toi cau UPDATE (ke ca updated_at)").isEqualTo(before);
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missing).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    @Test
    void t10_hrToken403_noToken401() throws Exception {
        String hrToken = hrWithCompany();
        Candidate candidate = newCandidate("u09-k-auth");
        String applicationId = apply(candidate, createOpenJob(hrToken), true);
        Map<String, Object> before = row(applicationId);

        assertThat(revoke(hrToken, applicationId).getResponse().getStatus()).isEqualTo(403);
        MvcResult anonymous = revoke(null, applicationId);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(anonymous).get("error").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(row(applicationId)).isEqualTo(before);
    }

    // ---- T11 (R-K6): updatable = false - save() cua rut don/doi trang thai khong ghi de co vua rut ----

    @Test
    void t11_withdrawAfterRevoke_doesNotOverwriteRevokedConsent() throws Exception {
        String hrToken = hrWithCompany();
        Candidate candidate = newCandidate("u09-k-t11");
        String applicationId = apply(candidate, createOpenJob(hrToken), true);
        assertThat(revoke(candidate.token(), applicationId).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> revoked = row(applicationId);

        mockMvc
                .perform(patch("/api/candidates/applications/" + applicationId + "/withdraw")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk());

        Map<String, Object> afterWithdraw = row(applicationId);
        assertThat(afterWithdraw.get("status")).isEqualTo("WITHDRAWN");
        assertThat(afterWithdraw.get("talent_pool_consent")).isEqualTo(false);
        assertThat(afterWithdraw.get("talent_pool_consent_at")).isEqualTo(revoked.get("talent_pool_consent_at"));
        assertThat(afterWithdraw.get("talent_pool_consent_revoked_at"))
                .isEqualTo(revoked.get("talent_pool_consent_revoked_at"));
        assertThat(afterWithdraw.get("screening_captured_at")).isEqualTo(revoked.get("screening_captured_at"));
        assertThat(detail(candidate, applicationId).get("talentPoolConsent").get("state").asString())
                .isEqualTo("REVOKED");
    }

    @Test
    void t11_hrStatusChangeAfterRevoke_doesNotTouchConsent() throws Exception {
        String hrToken = hrWithCompany();
        Candidate candidate = newCandidate("u09-k-t11hr");
        String applicationId = apply(candidate, createOpenJob(hrToken), true);
        assertThat(revoke(candidate.token(), applicationId).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> revoked = row(applicationId);

        mockMvc
                .perform(patch("/api/hr/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk());

        Map<String, Object> after = row(applicationId);
        assertThat(after.get("status")).isEqualTo("REJECTED");
        assertThat(after.get("talent_pool_consent")).isEqualTo(false);
        assertThat(after.get("talent_pool_consent_revoked_at")).isEqualTo(revoked.get("talent_pool_consent_revoked_at"));
    }

    // ---- T12, T16 (E1): khoa talentPoolConsent va ba trang thai ----

    @Test
    void t12_detailExposesTalentPoolConsentWithThreeStates() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        Candidate active = newCandidate("u09-k-t12a");
        Candidate notGiven = newCandidate("u09-k-t12n");
        String activeId = apply(active, jobId, true);
        String notGivenId = apply(notGiven, jobId, false);

        JsonNode activeDetail = detail(active, activeId);
        JsonNode activeConsent = activeDetail.get("talentPoolConsent");
        assertThat(Set.copyOf(activeConsent.propertyNames()))
                .containsExactlyInAnyOrder("state", "consentedAt", "revokedAt");
        assertThat(activeConsent.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(activeConsent.get("consentedAt").isNull()).isFalse();
        assertThat(activeConsent.get("revokedAt").isNull()).isTrue();

        JsonNode notGivenConsent = detail(notGiven, notGivenId).get("talentPoolConsent");
        assertThat(notGivenConsent.get("state").asString()).isEqualTo("NOT_GIVEN");
        assertThat(notGivenConsent.get("consentedAt").isNull()).isTrue();
        assertThat(notGivenConsent.get("revokedAt").isNull()).isTrue();

        MvcResult revokeResult = revoke(active.token(), activeId);
        JsonNode revokedConsent = detail(active, activeId).get("talentPoolConsent");
        assertThat(revokedConsent.get("state").asString()).isEqualTo("REVOKED");
        assertThat(revokedConsent.get("consentedAt")).isEqualTo(activeConsent.get("consentedAt"));
        assertThat(revokedConsent.get("revokedAt")).isEqualTo(body(revokeResult).get("revokedAt"));

        // T16 - so NGUYEN TEN khoa, duyet de quy, ca E1 lan body F4.
        List<String> keys = new ArrayList<>();
        collectKeys(activeDetail, keys);
        collectKeys(body(revokeResult), keys);
        assertThat(keys).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }

    // ---- FR-H15 T15 (R-C1): rut dong y go muc khoi MOI kho trong chinh transaction rut ----
    // Kho va muc dung truc tiep bang JDBC (P1-P9 chua co o dot nay). Lop KHONG @Transactional: rut dong y commit that roi
    // moi doc lai bang JDBC.

    private UUID companyOfJob(String jobId) {
        return jdbcTemplate.queryForObject("SELECT company_id FROM jobs WHERE id = ?", UUID.class, UUID.fromString(jobId));
    }

    private UUID insertPool(UUID companyId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO talent_pools (id, company_id, name) VALUES (?, ?, ?)", id, companyId, "Kho " + id);
        return id;
    }

    private UUID insertEntry(UUID poolId, String applicationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO talent_pool_entries (id, pool_id, application_id) VALUES (?, ?, ?)",
                id, poolId, UUID.fromString(applicationId));
        return id;
    }

    // Moi hang muc cua mot don (ca muc da go), theo id - de so sanh truoc/sau.
    private Map<UUID, Object> removedAtByEntry(String applicationId) {
        Map<UUID, Object> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, removed_at FROM talent_pool_entries WHERE application_id = ?",
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getTimestamp("removed_at"));
                },
                UUID.fromString(applicationId));
        return result;
    }

    private int activeEntryCount(String applicationId) {
        return count(
                "SELECT COUNT(*) FROM talent_pool_entries WHERE application_id = ? AND removed_at IS NULL", applicationId);
    }

    @Test
    void t15_revokeRemovesEntriesFromEveryPool_otherApplicationsAndOldRemovedEntryUntouched() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        UUID companyId = companyOfJob(jobId);
        Candidate revoker = newCandidate("h15-t15-a");
        Candidate sameCompany = newCandidate("h15-t15-b");
        String revokerApp = apply(revoker, jobId, true);
        String sameCompanyApp = apply(sameCompany, jobId, true);

        String otherHrToken = hrWithCompany();
        String otherJobId = createOpenJob(otherHrToken);
        Candidate otherCompany = newCandidate("h15-t15-c");
        String otherCompanyApp = apply(otherCompany, otherJobId, true);

        UUID pool1 = insertPool(companyId);
        UUID pool2 = insertPool(companyId);
        UUID otherPool = insertPool(companyOfJob(otherJobId));
        // Muc da go tu truoc cua chinh don rut: removed_at cu phai giu nguyen.
        UUID oldEntry = insertEntry(pool1, revokerApp);
        jdbcTemplate.update(
                "UPDATE talent_pool_entries SET removed_at = now() WHERE id = ?", oldEntry);
        insertEntry(pool1, revokerApp);
        insertEntry(pool2, revokerApp);
        insertEntry(pool1, sameCompanyApp);
        insertEntry(otherPool, otherCompanyApp);
        Object oldRemovedAt = removedAtByEntry(revokerApp).get(oldEntry);
        assertThat(activeEntryCount(revokerApp)).isEqualTo(2);

        MvcResult result = revoke(revoker.token(), revokerApp);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(Set.copyOf(json.propertyNames()))
                .as("response F4 giu nguyen hinh dang")
                .containsExactlyInAnyOrder("state", "consentedAt", "revokedAt");
        assertThat(json.get("state").asString()).isEqualTo("REVOKED");

        Map<UUID, Object> after = removedAtByEntry(revokerApp);
        assertThat(after).hasSize(3);
        assertThat(after.values()).as("ca hai kho deu da go").doesNotContainNull();
        assertThat(after.get(oldEntry)).as("muc go tu truoc khong doi").isEqualTo(oldRemovedAt);
        assertThat(activeEntryCount(sameCompanyApp)).as("don khac cung cong ty").isEqualTo(1);
        assertThat(activeEntryCount(otherCompanyApp)).as("don cua cong ty khac").isEqualTo(1);
    }

    @Test
    void t15_revokeTwice_conflict_entriesUntouched() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        Candidate candidate = newCandidate("h15-t15-twice");
        String applicationId = apply(candidate, jobId, true);
        insertEntry(insertPool(companyOfJob(jobId)), applicationId);

        assertThat(revoke(candidate.token(), applicationId).getResponse().getStatus()).isEqualTo(200);
        Map<UUID, Object> afterFirst = removedAtByEntry(applicationId);
        // Muc lech dung tay sau lan rut dau (vd du lieu sua tay): lan rut hai la 409 nen KHONG duoc go no.
        UUID strayEntry = insertEntry(insertPool(companyOfJob(jobId)), applicationId);

        assertConsentError(revoke(candidate.token(), applicationId));

        Map<UUID, Object> afterSecond = removedAtByEntry(applicationId);
        assertThat(afterSecond.get(strayEntry)).as("nhanh 409 khong cham muc").isNull();
        afterFirst.forEach((id, removedAt) -> assertThat(afterSecond.get(id)).isEqualTo(removedAt));
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
}

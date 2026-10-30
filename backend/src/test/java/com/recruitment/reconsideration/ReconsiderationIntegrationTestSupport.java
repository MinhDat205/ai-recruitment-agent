package com.recruitment.reconsideration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C09 - helper chung cho cac lop test tich hop cua reconsideration/ (khuon SavedJobIntegrationTestSupport). KHONG
// @Transactional cap lop (CLAUDE.md muc 3c): han tinh bang now() cua DB, thong bao chay AFTER_COMMIT, va test dong thoi
// can du lieu da commit. Moi test tu tao nguoi dung/tin/don rieng (UUID/email ngau nhien), khong doc seed, khong don sau
// test. Tai khoan: dang ky + dang nhap qua API (token that); tin, CV, don, lich su: chen thang bang SQL de dat trang thai
// va moc thoi gian tuy y (backdate bang now() cua DB, khong Instant.now()).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ReconsiderationIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String CANDIDATE_BASE = "/api/candidates/applications";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    record Account(UUID id, String token) {}

    record HrAccount(UUID id, String token, UUID companyId) {}

    // Mot don cua candidate vao tin jobId cua hr.
    record Fixture(HrAccount hr, Account candidate, UUID jobId, UUID applicationId) {}

    // Phan truoc @ <= 64 ky tu: prefix ngan (<= 27 ky tu).
    String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    String login(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    UUID userIdByEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    Account registerCandidate(String prefix, String fullName) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"%s\"}"
                                .formatted(email, fullName)))
                .andExpect(status().isCreated());
        return new Account(userIdByEmail(email), login(email));
    }

    Account registerCandidate(String prefix) throws Exception {
        return registerCandidate(prefix, "Ung Vien C09");
    }

    HrAccount registerHrWithCompany(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"HR C09\","
                                        + "\"phone\":\"0900000000\"}")
                                .formatted(email)))
                .andExpect(status().isCreated());
        String token = login(email);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty C09 %s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
        UUID hrId = userIdByEmail(email);
        UUID companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE owner_id = ?", UUID.class, hrId);
        return new HrAccount(hrId, token, companyId);
    }

    // deadlineOffsetDays: null = khong han nop; khac null = CURRENT_DATE cua DB + so ngay.
    UUID insertJob(HrAccount hr, String status, Integer deadlineOffsetDays, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO jobs (id, company_id, created_by, title, description, status, category_code, location_code,
                                  deadline, published_at, deleted_at)
                VALUES (?, ?, ?, ?, 'Mo ta', ?, 'IT_SOFTWARE', 'HA_NOI',
                        CASE WHEN CAST(? AS int) IS NULL THEN NULL ELSE CURRENT_DATE + CAST(? AS int) END,
                        now(), CASE WHEN ? THEN now() ELSE NULL END)
                """,
                id,
                hr.companyId(),
                hr.id(),
                "Tin C09 " + id,
                status,
                deadlineOffsetDays,
                deadlineOffsetDays,
                deleted);
        return id;
    }

    UUID insertOpenJob(HrAccount hr) {
        return insertJob(hr, "OPEN", null, false);
    }

    // Don voi trang thai tuy y + CV rieng. KHONG tu ghi lich su - goi cac helper lich su ben duoi.
    UUID insertApplication(UUID candidateId, UUID jobId, String status) {
        UUID resumeId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO resumes (id, candidate_id, file_url, file_name, file_type) VALUES (?, ?, 'x', 'cv.pdf', 'PDF')",
                resumeId,
                candidateId);
        jdbcTemplate.update(
                """
                INSERT INTO job_applications (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent,
                                              ai_consent_at)
                VALUES (?, ?, ?, ?, 1, ?, TRUE, now())
                """,
                applicationId,
                jobId,
                candidateId,
                resumeId,
                status);
        return applicationId;
    }

    void history(UUID applicationId, String from, String to, UUID changedBy) {
        jdbcTemplate.update(
                "INSERT INTO application_status_history (application_id, from_status, to_status, changed_by) "
                        + "VALUES (?, ?, ?, ?)",
                applicationId,
                from,
                to,
                changedBy);
    }

    // Don bi tu choi o vong ho so (PENDING -> REJECTED), changed_at = now() luc chen.
    Fixture rejectedAtScreening(String prefix) throws Exception {
        HrAccount hr = registerHrWithCompany(prefix + "-hr");
        Account candidate = registerCandidate(prefix + "-c");
        return rejectedAtScreening(hr, candidate, insertOpenJob(hr));
    }

    Fixture rejectedAtScreening(HrAccount hr, Account candidate, UUID jobId) {
        UUID applicationId = insertApplication(candidate.id(), jobId, "REJECTED");
        history(applicationId, null, "PENDING", candidate.id());
        history(applicationId, "PENDING", "REJECTED", hr.id());
        return new Fixture(hr, candidate, jobId, applicationId);
    }

    // Dich moc tu choi o vong ho so ve qua khu: changed_at = now() - khoang (bieu thuc interval cua Postgres).
    void backdateRejection(UUID applicationId, String intervalExpression) {
        jdbcTemplate.update(
                "UPDATE application_status_history SET changed_at = now() - " + intervalExpression
                        + " WHERE application_id = ? AND from_status = 'PENDING' AND to_status = 'REJECTED'",
                applicationId);
    }

    void insertRequest(UUID applicationId, String status, UUID decidedBy, String reason, String hrResponse) {
        jdbcTemplate.update(
                """
                INSERT INTO application_reconsideration_requests
                    (application_id, reason, status, hr_response, decided_at, decided_by)
                VALUES (?, ?, ?, ?, CASE WHEN ? = 'OPEN' THEN NULL ELSE now() END, ?)
                """,
                applicationId,
                reason,
                status,
                hrResponse,
                status,
                decidedBy);
    }

    MvcResult call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    // bodyJson null = khong gui body.
    MvcResult postRequest(String token, UUID applicationId, String bodyJson) throws Exception {
        MockHttpServletRequestBuilder request = post(CANDIDATE_BASE + "/" + applicationId + "/reconsideration-request");
        if (bodyJson != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(bodyJson);
        }
        return call(request, token);
    }

    // Body {"reason": <reason>} - reason duoc JSON hoa dung (giu \r, \n, ky tu dac biet nguyen van); null -> "reason":null.
    MvcResult postReason(String token, UUID applicationId, String reason) throws Exception {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("reason", reason);
        return postRequest(token, applicationId, JSON.writeValueAsString(body));
    }

    MvcResult getDetail(String token, UUID applicationId) throws Exception {
        return call(get(CANDIDATE_BASE + "/" + applicationId), token);
    }

    MvcResult getList(String token) throws Exception {
        return call(get(CANDIDATE_BASE), token);
    }

    JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    String errorCode(MvcResult result) throws Exception {
        return body(result).get("error").asString();
    }

    String errorMessage(MvcResult result) throws Exception {
        return body(result).get("message").asString();
    }

    long countRequests(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM application_reconsideration_requests WHERE application_id = ?",
                Long.class,
                applicationId);
    }

    Map<String, Object> requestRow(UUID applicationId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM application_reconsideration_requests WHERE application_id = ?", applicationId);
    }

    long countNotifications(UUID userId, String type, UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = ? AND entity_id = ?",
                Long.class,
                userId,
                type,
                applicationId);
    }

    long countNotificationsForApplication(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE entity_id = ?", Long.class, applicationId);
    }

    Instant dbDeadline(UUID applicationId) {
        OffsetDateTime value = jdbcTemplate.queryForObject(
                """
                SELECT changed_at + INTERVAL '168 hours' FROM application_status_history
                WHERE application_id = ? AND from_status = 'PENDING' AND to_status = 'REJECTED'
                """,
                OffsetDateTime.class,
                applicationId);
        return value.toInstant();
    }

    static List<String> keysOf(JsonNode node) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    static void collectKeys(JsonNode node, List<String> sink) {
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

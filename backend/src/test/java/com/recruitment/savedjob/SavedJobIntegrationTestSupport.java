package com.recruitment.savedjob;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

// FR-U16 - helper chung cho cac lop test tich hop cua savedjob/ (khuon JobSearchIntegrationTestSupport). KHONG
// @Transactional cap lop (CLAUDE.md muc 3c): saved_at/deleted_at lay now() cua DB, va test dong thoi can du lieu da
// commit. Moi test tu tao nguoi dung/tin rieng (UUID/email ngau nhien), khong doc seed, khong don sau test.
// Tai khoan: dang ky + dang nhap qua API (co token that); tin: chen thang bang SQL de dat status/han nop/xoa mem tuy y
// (han nop tinh theo CURRENT_DATE cua DB, khong LocalDate.now()).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class SavedJobIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String BASE = "/api/candidates/saved-jobs";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    record Account(UUID id, String token) {}

    record HrAccount(UUID id, String token, UUID companyId) {}

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

    Account registerCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Ung Vien U16\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        return new Account(userIdByEmail(email), login(email));
    }

    HrAccount registerHrWithCompany(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"HR U16\","
                                        + "\"phone\":\"0900000000\"}")
                                .formatted(email)))
                .andExpect(status().isCreated());
        String token = login(email);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty U16 %s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
        UUID hrId = userIdByEmail(email);
        UUID companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE owner_id = ?", UUID.class, hrId);
        return new HrAccount(hrId, token, companyId);
    }

    // Ung vien chi can id (test goi thang service): chen thang users, khong qua API.
    UUID insertCandidate() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, full_name) VALUES (?, ?, 'x', 'CANDIDATE', 'UV U16')",
                id,
                "u16-" + id + "@example.com");
        return id;
    }

    // Tin OPEN, khong han nop, chua xoa.
    UUID insertOpenJob(HrAccount hr) {
        return insertJob(hr, "OPEN", null, false);
    }

    // deadlineOffsetDays: null = khong han nop; khac null = CURRENT_DATE cua DB + so ngay.
    UUID insertJob(HrAccount hr, String status, Integer deadlineOffsetDays, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO jobs (id, company_id, created_by, title, description, status, category_code, location_code,
                                  salary_min, salary_max, salary_currency, deadline, published_at, deleted_at)
                VALUES (?, ?, ?, ?, 'Mo ta', ?, 'IT_SOFTWARE', 'HA_NOI', 10000000, 15000000, 'VND',
                        CASE WHEN CAST(? AS int) IS NULL THEN NULL ELSE CURRENT_DATE + CAST(? AS int) END,
                        now(), CASE WHEN ? THEN now() ELSE NULL END)
                """,
                id,
                hr.companyId(),
                hr.id(),
                "Tin U16 " + id,
                status,
                deadlineOffsetDays,
                deadlineOffsetDays,
                deleted);
        return id;
    }

    // Nhieu tin OPEN mot lan (batch) - dung de dung truoc 99/100 dong dang luu.
    List<UUID> insertOpenJobs(HrAccount hr, int count) {
        List<UUID> ids = new ArrayList<>();
        List<Object[]> args = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            args.add(new Object[] {id, hr.companyId(), hr.id(), "Tin U16 lo " + id});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO jobs (id, company_id, created_by, title, description, status) "
                        + "VALUES (?, ?, ?, ?, 'Mo ta', 'OPEN')",
                args);
        return ids;
    }

    // Dong dang luu chen thang (khong qua service) - dung de dung truoc trang thai tran.
    void insertActiveSaves(UUID candidateId, List<UUID> jobIds) {
        List<Object[]> args = jobIds.stream().map(jobId -> new Object[] {candidateId, jobId}).toList();
        jdbcTemplate.batchUpdate("INSERT INTO saved_jobs (candidate_id, job_id) VALUES (?, ?)", args);
    }

    void setJobColumn(UUID jobId, String assignmentSql) {
        jdbcTemplate.update("UPDATE jobs SET " + assignmentSql + " WHERE id = ?", jobId);
    }

    MvcResult call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult putSave(String token, UUID jobId) throws Exception {
        return call(put(BASE + "/" + jobId), token);
    }

    MvcResult deleteSave(String token, UUID jobId) throws Exception {
        return call(delete(BASE + "/" + jobId), token);
    }

    MvcResult getList(String token) throws Exception {
        return call(get(BASE), token);
    }

    MvcResult getIds(String token) throws Exception {
        return call(get(BASE + "/ids"), token);
    }

    JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    List<String> idsOf(MvcResult result) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : body(result).values()) {
            ids.add(node.asString());
        }
        return ids;
    }

    long countActive(UUID candidateId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saved_jobs WHERE candidate_id = ? AND deleted_at IS NULL", Long.class, candidateId);
    }

    long countRows(UUID candidateId, UUID jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saved_jobs WHERE candidate_id = ? AND job_id = ?", Long.class, candidateId, jobId);
    }

    record Row(UUID id, Instant savedAt, Instant deletedAt) {}

    Row row(UUID candidateId, UUID jobId) {
        Map<String, Object> m = jdbcTemplate.queryForMap(
                "SELECT id, saved_at, deleted_at FROM saved_jobs WHERE candidate_id = ? AND job_id = ?",
                candidateId,
                jobId);
        return new Row((UUID) m.get("id"), toInstant(m.get("saved_at")), toInstant(m.get("deleted_at")));
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        throw new IllegalStateException("Kieu thoi gian khong ho tro: " + value.getClass());
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

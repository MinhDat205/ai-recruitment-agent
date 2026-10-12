package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U09 F1 (GET /api/candidates/jobs/{jobId}/screening-questions) - T1, T16 (phan F1). Trang thai tin dat bang
// JdbcTemplate (test chi kiem bo loc findOpenJobById cua F1, khong kiem luong mo tin). @Transactional: moi @Test rollback
// rieng; JdbcTemplate dung chung connection cua transaction test nen F1 thay ngay du lieu vua ghi.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScreeningFormCandidateIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final List<String> FORBIDDEN_KEYS =
            List.of("verdict", "label", "isQualified", "passed", "recommendation", "maxQuestions", "deletedAt");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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

    private String registerAndLogin(String role, String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody = role.equals("hr")
                ? "{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nha Tuyen Dung\",\"phone\":\"0900000000\"}"
                        .formatted(email)
                : "{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Ung Vien\"}".formatted(email);
        mockMvc
                .perform(post("/api/auth/register/" + role).contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String hrWithCompany() throws Exception {
        String token = registerAndLogin("hr", "u09-f1-hr");
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
        return token;
    }

    // Tin tao qua API luon DRAFT; trang thai dat lai bang setStatus.
    private String createJob(String hrToken) throws Exception {
        String body =
                """
                {
                  "job": {"title":"Nhan vien CSKH","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao ung vien.","senderName":"Phong Nhan Su"}
                }
                """;
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asString();
    }

    // L8 - test chay trong MOT transaction: entity Job vua tao qua API con trong persistence context. Phai flush + clear
    // TRUOC khi sua bang JDBC, neu khong lan flush tu dong truoc native query cua F1 se ghi de trang thai cu len.
    private void jdbcUpdate(String sql, Object... args) {
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update(sql, args);
    }

    private void setStatus(String jobId, String status) {
        jdbcUpdate("UPDATE jobs SET status = ? WHERE id = ?", status, UUID.fromString(jobId));
    }

    private String addQuestion(String hrToken, String jobId, String json) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/screening-questions")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asString();
    }

    private MvcResult getForm(String token, String jobId) throws Exception {
        var request = get("/api/candidates/jobs/" + jobId + "/screening-questions");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private int revision(String jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, UUID.fromString(jobId));
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

    // ---- T1 duong ----

    @Test
    void t1_openJobWithThreeTypes_returnsActiveQuestionsInOrderWithRevision() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createJob(hrToken);
        String q1 = addQuestion(hrToken, jobId,
                "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Bạn có thể bắt đầu đi làm từ khi nào?\",\"required\":true}");
        String q2 = addQuestion(hrToken, jobId,
                "{\"questionType\":\"YES_NO\",\"questionText\":\"Bạn có sẵn sàng làm ca tối không?\"}");
        String deleted = addQuestion(hrToken, jobId, "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Cau se xoa\"}");
        String q3 = addQuestion(hrToken, jobId,
                "{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Mức lương mong muốn?\","
                        + "\"options\":[\"Dưới 10 triệu\",\"10–14 triệu\",\"Trên 14 triệu\"]}");
        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId + "/screening-questions/" + deleted)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());
        setStatus(jobId, "OPEN");
        String candidateToken = registerAndLogin("candidate", "u09-f1-uv");

        MvcResult result = getForm(candidateToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("jobId").asString()).isEqualTo(jobId);
        assertThat(json.get("revision").asInt()).isEqualTo(revision(jobId)).isEqualTo(5);
        JsonNode questions = json.get("questions");
        assertThat(questions.size()).as("khong tra cau da xoa mem").isEqualTo(3);
        assertThat(questions.get(0).get("id").asString()).isEqualTo(q1);
        assertThat(questions.get(0).get("questionType").asString()).isEqualTo("SHORT_TEXT");
        assertThat(questions.get(0).get("questionText").asString()).isEqualTo("Bạn có thể bắt đầu đi làm từ khi nào?");
        assertThat(questions.get(0).get("required").asBoolean()).isTrue();
        assertThat(questions.get(0).get("options").size()).isZero();
        assertThat(questions.get(1).get("id").asString()).isEqualTo(q2);
        assertThat(questions.get(1).get("questionType").asString()).isEqualTo("YES_NO");
        assertThat(questions.get(1).get("required").asBoolean()).isFalse();
        assertThat(questions.get(2).get("id").asString()).isEqualTo(q3);
        assertThat(questions.get(2).get("questionType").asString()).isEqualTo("SINGLE_CHOICE");
        assertThat(questions.get(2).get("options").get(1).asString()).isEqualTo("10–14 triệu");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(deleted);

        // T16 (phan F1) - so NGUYEN TEN khoa, duyet de quy; them maxQuestions/deletedAt (R-F2).
        List<String> keys = new ArrayList<>();
        collectKeys(json, keys);
        assertThat(keys).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }

    @Test
    void t1_openJobWithoutQuestions_returnsEmptyListAndRevision() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createJob(hrToken);
        String temp = addQuestion(hrToken, jobId, "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Tam\"}");
        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId + "/screening-questions/" + temp)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());
        setStatus(jobId, "OPEN");

        MvcResult result = getForm(registerAndLogin("candidate", "u09-f1-uv0"), jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("questions").size()).isZero();
        assertThat(json.get("revision").asInt()).isEqualTo(2);
    }

    // ---- T1 am / bien: chi tin xem duoc cong khai ----

    @Test
    void t1_jobNotPubliclyVisible_returns404JobNotFound() throws Exception {
        String hrToken = hrWithCompany();
        String candidateToken = registerAndLogin("candidate", "u09-f1-uv404");

        String draft = createJob(hrToken);
        String paused = createJob(hrToken);
        setStatus(paused, "PAUSED");
        String closed = createJob(hrToken);
        setStatus(closed, "CLOSED");
        String expired = createJob(hrToken);
        setStatus(expired, "OPEN");
        jdbcUpdate("UPDATE jobs SET deadline = CURRENT_DATE - 1 WHERE id = ?", UUID.fromString(expired));
        String deletedOpen = createJob(hrToken);
        setStatus(deletedOpen, "OPEN");
        jdbcUpdate("UPDATE jobs SET deleted_at = now() WHERE id = ?", UUID.fromString(deletedOpen));
        String openControl = createJob(hrToken);
        setStatus(openControl, "OPEN");

        // Doi chung duong: tin OPEN cung lop du lieu tra 200 - cac ca 404 duoi day khong do loi dung du lieu test.
        assertThat(getForm(candidateToken, openControl).getResponse().getStatus()).isEqualTo(200);

        for (String jobId : List.of(draft, paused, closed, expired, deletedOpen, UUID.randomUUID().toString())) {
            MvcResult result = getForm(candidateToken, jobId);
            assertThat(result.getResponse().getStatus()).as("tin %s", jobId).isEqualTo(404);
            assertThat(body(result).get("error").asString()).isEqualTo("JOB_NOT_FOUND");
        }
    }

    @Test
    void t1_deadlineToday_isStillOpen() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createJob(hrToken);
        setStatus(jobId, "OPEN");
        jdbcUpdate("UPDATE jobs SET deadline = CURRENT_DATE WHERE id = ?", UUID.fromString(jobId));

        assertThat(getForm(registerAndLogin("candidate", "u09-f1-uvtd"), jobId).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void t1_hrToken403_noToken401() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createJob(hrToken);
        setStatus(jobId, "OPEN");

        assertThat(getForm(hrToken, jobId).getResponse().getStatus()).isEqualTo(403);
        MvcResult anonymous = getForm(null, jobId);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(anonymous).get("error").asString()).isEqualTo("UNAUTHENTICATED");
    }
}

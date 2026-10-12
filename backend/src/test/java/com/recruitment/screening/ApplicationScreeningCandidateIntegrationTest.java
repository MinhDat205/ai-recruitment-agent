package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U09 F3 (GET /api/candidates/applications/{id}/screening-answers) - T13, T16 (phan F3).
// KHONG @Transactional toan lop: don tao bang luong nop that (F2) va phai thay du lieu da commit, cung khuon
// ApplicationScreeningCaptureIntegrationTest. Du lieu ngau nhien rieng, khong don sau test (L2 cua FR-H10).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationScreeningCandidateIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PDF_CONTENT = "%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8);
    private static final List<String> FORBIDDEN_KEYS =
            List.of("verdict", "label", "isQualified", "passed", "recommendation");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Candidate(String token, String resumeId) {
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(text(result));
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
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
        String email = uniqueEmail("u09-f3-hr");
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

    private String addQuestion(String hrToken, String jobId, String json) throws Exception {
        return body(mockMvc
                        .perform(post("/api/hr/jobs/" + jobId + "/screening-questions")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString();
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

    private int revision(String jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, UUID.fromString(jobId));
    }

    private String apply(Candidate candidate, String jobId, List<Map<String, Object>> answers) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jobId", jobId);
        request.put("resumeId", candidate.resumeId());
        request.put("aiConsent", true);
        request.put("screeningRevision", revision(jobId));
        request.put("answers", answers);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(text(result)).isEqualTo(201);
        return body(result).get("id").asString();
    }

    private static Map<String, Object> answer(String questionId, String text, Boolean yesNo) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("questionId", questionId);
        answer.put("answerText", text);
        answer.put("answerYesNo", yesNo);
        return answer;
    }

    private MvcResult getMyAnswers(String token, String applicationId) throws Exception {
        var request = get("/api/candidates/applications/" + applicationId + "/screening-answers");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
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

    // ---- T13 duong ----

    @Test
    void t13_ownCapturedApplication_returnsSnapshot_unchangedAfterHrEditsQuestions() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        String q1 = addQuestion(hrToken, jobId,
                "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Bạn có thể đi làm từ khi nào?\",\"required\":true}");
        String q2 = addQuestion(hrToken, jobId,
                "{\"questionType\":\"YES_NO\",\"questionText\":\"Bạn có sẵn sàng làm ca tối không?\"}");
        Candidate candidate = newCandidate("u09-f3-own");
        String applicationId =
                apply(candidate, jobId, List.of(answer(q1, "Đầu tháng 11", null), answer(q2, null, false)));

        MvcResult first = getMyAnswers(candidate.token(), applicationId);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(first);
        assertThat(json.get("applicationId").asString()).isEqualTo(applicationId);
        assertThat(json.get("state").asString()).isEqualTo("CAPTURED");
        assertThat(json.get("capturedAt").isNull()).isFalse();
        assertThat(json.get("items").size()).isEqualTo(2);
        assertThat(json.get("items").get(0).get("questionText").asString()).isEqualTo("Bạn có thể đi làm từ khi nào?");
        assertThat(json.get("items").get(0).get("answerText").asString()).isEqualTo("Đầu tháng 11");
        assertThat(json.get("items").get(1).get("answerYesNo").asBoolean()).isFalse();

        // HR doi noi dung + loai cau 1, xoa cau 2 -> F3 tra y het (R-S2, R-S3 cua FR-H10).
        mockMvc
                .perform(put("/api/hr/jobs/" + jobId + "/screening-questions/" + q1)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"YES_NO\",\"questionText\":\"Cau hoi da sua\"}"))
                .andExpect(status().isOk());
        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId + "/screening-questions/" + q2)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());

        MvcResult second = getMyAnswers(candidate.token(), applicationId);
        assertThat(text(second)).isEqualTo(text(first));
    }

    @Test
    void t13_noQuestionsAndNotCaptured_states() throws Exception {
        String hrToken = hrWithCompany();
        Candidate candidate = newCandidate("u09-f3-states");

        String noQuestions = apply(candidate, createOpenJob(hrToken), List.of());
        JsonNode noQuestionsJson = body(getMyAnswers(candidate.token(), noQuestions));
        assertThat(noQuestionsJson.get("state").asString()).isEqualTo("NO_QUESTIONS");
        assertThat(noQuestionsJson.get("capturedAt").isNull()).isFalse();
        assertThat(noQuestionsJson.get("items").size()).isZero();

        // Gia lap don nop truoc FR-U09: screening_captured_at NULL.
        String notCaptured = apply(candidate, createOpenJob(hrToken), List.of());
        jdbcTemplate.update(
                "UPDATE job_applications SET screening_captured_at = NULL WHERE id = ?", UUID.fromString(notCaptured));
        JsonNode notCapturedJson = body(getMyAnswers(candidate.token(), notCaptured));
        assertThat(notCapturedJson.get("state").asString()).isEqualTo("NOT_CAPTURED");
        assertThat(notCapturedJson.get("capturedAt").isNull()).isTrue();
        assertThat(notCapturedJson.get("items").size()).isZero();
    }

    // ---- T13 am ----

    @Test
    void t13_otherCandidatesApplicationAndMissing_return404WithSameCode() throws Exception {
        String hrToken = hrWithCompany();
        Candidate owner = newCandidate("u09-f3-owner");
        Candidate stranger = newCandidate("u09-f3-stranger");
        String applicationId = apply(owner, createOpenJob(hrToken), List.of());

        MvcResult foreign = getMyAnswers(stranger.token(), applicationId);
        MvcResult missing = getMyAnswers(owner.token(), UUID.randomUUID().toString());

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(foreign).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(body(missing).get("error").asString()).isEqualTo(body(foreign).get("error").asString());
    }

    @Test
    void t13_hrToken403_noToken401() throws Exception {
        String hrToken = hrWithCompany();
        Candidate candidate = newCandidate("u09-f3-auth");
        String applicationId = apply(candidate, createOpenJob(hrToken), List.of());

        assertThat(getMyAnswers(hrToken, applicationId).getResponse().getStatus()).isEqualTo(403);
        MvcResult anonymous = getMyAnswers(null, applicationId);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(anonymous).get("error").asString()).isEqualTo("UNAUTHENTICATED");
    }

    // ---- T16 (phan F3) ----

    @Test
    void t16_noForbiddenKeys() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        String q1 = addQuestion(hrToken, jobId,
                "{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Muc luong?\",\"options\":[\"A\",\"B\"]}");
        Candidate candidate = newCandidate("u09-f3-keys");
        String applicationId = apply(candidate, jobId, List.of(answer(q1, "A", null)));

        List<String> keys = new ArrayList<>();
        collectKeys(body(getMyAnswers(candidate.token(), applicationId)), keys);
        assertThat(keys).contains("options").doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }
}

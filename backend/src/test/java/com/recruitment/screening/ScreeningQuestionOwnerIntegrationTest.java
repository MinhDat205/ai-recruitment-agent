package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

// FR-H10 S1-S5 - T1, T2, T3, T4, T6, T7, T8, T9, T16 (phan S1/S2) cua REQUIREMENT muc 7.1. T5 (song song), T15 (rang
// buoc DB), T17 (revision, can rollback that cua service) o lop rieng KHONG @Transactional.
// @Transactional: moi @Test rollback rieng; MockMvc chay cung thread nen request tham gia transaction cua test. Lop tu
// tao toan bo du lieu qua API/repository (email duy nhat); helper chep theo RubricOwnerIntegrationTest (tien le du an:
// khong tach tien ich test dung chung).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScreeningQuestionOwnerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Set<String> FORBIDDEN_KEYS =
            Set.of("verdict", "label", "isQualified", "passed", "recommendation");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- Helper ----

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws Exception {
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody = """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test"}
                """.formatted(email);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private void createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private String createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van vi tri %s",
                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title, title);
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    // HR co cong ty + mot Job; tra [token, jobId].
    private String[] hrWithJob(String prefix) throws Exception {
        String token = registerAndLoginHr(prefix);
        createCompany(token, "Cong ty " + UUID.randomUUID());
        return new String[] {token, createJob(token, "Nhan vien CSKH")};
    }

    private String url(String jobId) {
        return "/api/hr/jobs/" + jobId + "/screening-questions";
    }

    // Map cho phep gia tri null (khoa co mat, gia tri null) - khac voi khoa vang mat.
    private static String questionJson(String type, Object text, Object options, Object required) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("questionType", type);
        map.put("questionText", text);
        map.put("options", options);
        map.put("required", required);
        return JSON.writeValueAsString(map);
    }

    private static String shortText(String text) {
        return questionJson("SHORT_TEXT", text, null, null);
    }

    private static String singleChoice(List<String> options) {
        return questionJson("SINGLE_CHOICE", "Muc luong mong muon?", options, null);
    }

    private MvcResult perform(String method, String path, String token, String json) throws Exception {
        var builder = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        if (token != null) {
            builder = builder.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private MvcResult add(String token, String jobId, String json) throws Exception {
        return perform("POST", url(jobId), token, json);
    }

    private String addOk(String token, String jobId, String json) throws Exception {
        MvcResult result = add(token, jobId, json);
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        return body(result).get("id").asString();
    }

    private JsonNode list(String token, String jobId) throws Exception {
        MvcResult result = perform("GET", url(jobId), token, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result);
    }

    private void assertError(MvcResult result, int status, String error, String message) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(status);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo(error);
        if (message != null) {
            assertThat(json.get("message").asString()).isEqualTo(message);
        }
    }

    private void assertInvalid(MvcResult result, String message) throws Exception {
        assertError(result, 400, "INVALID_SCREENING_QUESTION", message);
    }

    private static List<String> options(int count, int length) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "PA" + i + "-";
            result.add(prefix + "x".repeat(Math.max(0, length - prefix.length())));
        }
        return result;
    }

    private static void collectKeys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                keys.add(name);
                collectKeys(node.get(name), keys);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectKeys(child, keys);
            }
        }
    }

    // ---- T1 ----

    @Test
    void t1_addThreeTypes_listReturnsInsertionOrderAndMaxQuestions() throws Exception {
        String[] hr = hrWithJob("t1");
        addOk(hr[0], hr[1], questionJson("SHORT_TEXT", "Ban co the bat dau di lam tu khi nao?", null, true));
        addOk(hr[0], hr[1], singleChoice(List.of("Duoi 10 trieu", "10-14 trieu", "Tren 14 trieu")));
        addOk(hr[0], hr[1], questionJson("YES_NO", "Ban san sang lam ca toi khong?", null, null));

        JsonNode json = list(hr[0], hr[1]);
        assertThat(json.get("jobId").asString()).isEqualTo(hr[1]);
        assertThat(json.get("maxQuestions").asInt()).isEqualTo(10);
        JsonNode questions = json.get("questions");
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).get("questionType").asString()).isEqualTo("SHORT_TEXT");
        assertThat(questions.get(0).get("required").asBoolean()).isTrue();
        assertThat(questions.get(0).get("options")).isEmpty();
        assertThat(questions.get(1).get("questionType").asString()).isEqualTo("SINGLE_CHOICE");
        assertThat(questions.get(1).get("options").get(1).asString()).isEqualTo("10-14 trieu");
        assertThat(questions.get(2).get("questionType").asString()).isEqualTo("YES_NO");
        assertThat(questions.get(2).get("required").asBoolean()).as("R-Q5 mac dinh khong bat buoc").isFalse();
        assertThat(questions.get(0).get("displayOrder").asInt()).isLessThan(questions.get(1).get("displayOrder").asInt());
        assertThat(questions.get(1).get("displayOrder").asInt()).isLessThan(questions.get(2).get("displayOrder").asInt());
    }

    // ---- T2 ----

    @Test
    void t2_questionText_nullMissingEmptyOrBlank_returnsInvalidScreeningQuestion() throws Exception {
        String[] hr = hrWithJob("t2a");
        String empty = "Vui lòng nhập nội dung câu hỏi.";
        assertInvalid(add(hr[0], hr[1], questionJson("SHORT_TEXT", null, null, null)), empty);
        assertInvalid(add(hr[0], hr[1], "{\"questionType\":\"SHORT_TEXT\"}"), empty);
        assertInvalid(add(hr[0], hr[1], shortText("")), empty);
        assertInvalid(add(hr[0], hr[1], shortText("   \n\t ")), empty);
        assertThat(list(hr[0], hr[1]).get("questions")).isEmpty();
    }

    @Test
    void t2_questionTextLength_isMeasuredAfterTrim() throws Exception {
        String[] hr = hrWithJob("t2b");
        addOk(hr[0], hr[1], shortText("a".repeat(299)));
        addOk(hr[0], hr[1], shortText("b".repeat(300)));
        addOk(hr[0], hr[1], shortText(" " + "c".repeat(300) + " "));
        String tooLong = "Nội dung câu hỏi tối đa 300 ký tự.";
        assertInvalid(add(hr[0], hr[1], shortText("d".repeat(301))), tooLong);
        assertInvalid(add(hr[0], hr[1], shortText(" " + "e".repeat(301))), tooLong);

        JsonNode questions = list(hr[0], hr[1]).get("questions");
        assertThat(questions).hasSize(3);
        assertThat(questions.get(2).get("questionText").asString())
                .as("luu gia tri da bo khoang trang hai dau")
                .isEqualTo("c".repeat(300));
    }

    @Test
    void t2_missingQuestionType_returnsBeanValidationMap() throws Exception {
        String[] hr = hrWithJob("t2c");
        MvcResult result = add(hr[0], hr[1], "{\"questionText\":\"Cau hoi\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).has("questionType")).isTrue();
    }

    // ---- T3 ----

    @Test
    void t3_optionCount_boundaries() throws Exception {
        String[] hr = hrWithJob("t3a");
        String count = "Câu hỏi một lựa chọn cần từ 2 đến 10 phương án.";
        assertInvalid(add(hr[0], hr[1], singleChoice(options(1, 10))), count);
        addOk(hr[0], hr[1], singleChoice(options(2, 10)));
        addOk(hr[0], hr[1], singleChoice(options(10, 10)));
        assertInvalid(add(hr[0], hr[1], singleChoice(options(11, 10))), count);
        assertInvalid(add(hr[0], hr[1], questionJson("SINGLE_CHOICE", "Cau hoi", null, null)), count);
        assertInvalid(
                add(hr[0], hr[1], "{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Cau hoi\"}"), count);
        assertInvalid(add(hr[0], hr[1], singleChoice(List.of())), count);
    }

    @Test
    void t3_optionLength_isMeasuredAfterTrim() throws Exception {
        String[] hr = hrWithJob("t3b");
        addOk(hr[0], hr[1], singleChoice(List.of("a".repeat(99), "b".repeat(100))));
        String questionId = addOk(hr[0], hr[1], singleChoice(List.of(" " + "c".repeat(100) + " ", "Khac")));
        String tooLong = "Mỗi phương án tối đa 100 ký tự.";
        assertInvalid(add(hr[0], hr[1], singleChoice(List.of("d".repeat(101), "Khac"))), tooLong);
        assertInvalid(add(hr[0], hr[1], singleChoice(List.of(" " + "e".repeat(101), "Khac"))), tooLong);

        JsonNode saved = null;
        for (JsonNode q : list(hr[0], hr[1]).get("questions")) {
            if (q.get("id").asString().equals(questionId)) {
                saved = q;
            }
        }
        assertThat(saved).isNotNull();
        assertThat(saved.get("options").get(0).asString()).isEqualTo("c".repeat(100));
    }

    @Test
    void t3_emptyNullDuplicateAndNotAllowedOptions() throws Exception {
        String[] hr = hrWithJob("t3c");
        String empty = "Phương án không được để trống.";
        List<String> withNull = new ArrayList<>();
        withNull.add("Co");
        withNull.add(null);
        assertInvalid(add(hr[0], hr[1], singleChoice(withNull)), empty);
        assertInvalid(add(hr[0], hr[1], singleChoice(List.of("Co", ""))), empty);
        assertInvalid(add(hr[0], hr[1], singleChoice(List.of("Co", "   "))), empty);
        assertInvalid(
                add(hr[0], hr[1], singleChoice(List.of("Ca A", " ca a "))), "Các phương án không được trùng nhau.");
        String notAllowed = "Chỉ câu hỏi một lựa chọn mới có phương án.";
        assertInvalid(add(hr[0], hr[1], questionJson("SHORT_TEXT", "Cau hoi", List.of("x"), null)), notAllowed);
        assertInvalid(add(hr[0], hr[1], questionJson("YES_NO", "Cau hoi", List.of("x", "y"), null)), notAllowed);
        assertThat(list(hr[0], hr[1]).get("questions")).isEmpty();
    }

    // ---- T4 ----

    @Test
    void t4_limit_nineThenTenthOkEleventhRejected_deleteFreesSlot() throws Exception {
        String[] hr = hrWithJob("t4");
        String firstId = null;
        for (int i = 1; i <= 9; i++) {
            String id = addOk(hr[0], hr[1], shortText("Cau " + i));
            if (i == 1) {
                firstId = id;
            }
        }
        addOk(hr[0], hr[1], shortText("Cau 10"));
        assertError(
                add(hr[0], hr[1], shortText("Cau 11")),
                400,
                "SCREENING_QUESTION_LIMIT_REACHED",
                "Mỗi tin tuyển dụng có tối đa 10 câu hỏi sàng lọc.");

        assertThat(perform("DELETE", url(hr[1]) + "/" + firstId, hr[0], null).getResponse().getStatus())
                .isEqualTo(204);
        addOk(hr[0], hr[1], shortText("Cau 11 sau khi xoa"));
        assertThat(list(hr[0], hr[1]).get("questions")).hasSize(10);
    }

    // ---- T6 ----

    @Test
    void t6_reorder_validPermutationAndInvalidSets() throws Exception {
        String[] hr = hrWithJob("t6");
        String a = addOk(hr[0], hr[1], shortText("Cau A"));
        String b = addOk(hr[0], hr[1], shortText("Cau B"));
        String c = addOk(hr[0], hr[1], shortText("Cau C"));
        String deleted = addOk(hr[0], hr[1], shortText("Cau D"));
        perform("DELETE", url(hr[1]) + "/" + deleted, hr[0], null);
        String otherJob = createJob(hr[0], "Tin khac");
        String otherJobQuestion = addOk(hr[0], otherJob, shortText("Cau tin khac"));

        MvcResult ok = perform("PUT", url(hr[1]) + "/order", hr[0], orderJson(c, a, b));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        JsonNode questions = body(ok).get("questions");
        assertThat(List.of(
                        questions.get(0).get("id").asString(),
                        questions.get(1).get("id").asString(),
                        questions.get(2).get("id").asString()))
                .containsExactly(c, a, b);
        assertThat(list(hr[0], hr[1]).get("questions").get(0).get("id").asString()).isEqualTo(c);

        String message = "Danh sách câu hỏi đã thay đổi, vui lòng tải lại.";
        List<String> invalid = List.of(
                orderJson(a, b),
                orderJson(a, b, c, UUID.randomUUID().toString()),
                orderJson(a, a, b, c),
                orderJson(a, b, otherJobQuestion),
                orderJson(a, b, c, deleted));
        for (String json : invalid) {
            assertError(perform("PUT", url(hr[1]) + "/order", hr[0], json), 400, "INVALID_SCREENING_ORDER", message);
        }
    }

    private static String orderJson(String... ids) {
        return JSON.writeValueAsString(Map.of("questionIds", List.of(ids)));
    }

    // ---- T7 ----

    @Test
    void t7_delete_isSoftAndHiddenAfterwards() throws Exception {
        String[] hr = hrWithJob("t7");
        String id = addOk(hr[0], hr[1], shortText("Se bi xoa"));
        addOk(hr[0], hr[1], shortText("Con lai"));

        assertThat(perform("DELETE", url(hr[1]) + "/" + id, hr[0], null).getResponse().getStatus())
                .isEqualTo(204);

        Instant deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM job_screening_questions WHERE id = ?", Instant.class, UUID.fromString(id));
        assertThat(deletedAt).as("hang con trong DB, chi dat deleted_at").isNotNull();
        JsonNode questions = list(hr[0], hr[1]).get("questions");
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).get("questionText").asString()).isEqualTo("Con lai");

        String notFound = "Không tìm thấy câu hỏi sàng lọc.";
        assertError(
                perform("PUT", url(hr[1]) + "/" + id, hr[0], shortText("Sua cau da xoa")),
                404,
                "SCREENING_QUESTION_NOT_FOUND",
                notFound);
        assertError(perform("DELETE", url(hr[1]) + "/" + id, hr[0], null), 404, "SCREENING_QUESTION_NOT_FOUND", notFound);
    }

    // ---- T8 ----

    @Test
    void t8_access_otherCompanyMissingJobNoCompanyWrongJobCandidateAnonymous() throws Exception {
        String[] owner = hrWithJob("t8-owner");
        String questionId = addOk(owner[0], owner[1], shortText("Cau cua chu"));
        String[] other = hrWithJob("t8-other");
        String noCompanyToken = registerAndLoginHr("t8-nocompany");
        String candidateToken = registerAndLoginCandidate("t8-cand");
        String missingJob = UUID.randomUUID().toString();
        String body = shortText("Cau moi");
        String orderBody = orderJson(questionId);

        // HR cong ty khac -> 403 o ca 5 endpoint.
        assertThat(perform("GET", url(owner[1]), other[0], null).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform("POST", url(owner[1]), other[0], body).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform("PUT", url(owner[1]) + "/" + questionId, other[0], body).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(perform("DELETE", url(owner[1]) + "/" + questionId, other[0], null).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(perform("PUT", url(owner[1]) + "/order", other[0], orderBody).getResponse().getStatus())
                .isEqualTo(403);

        // Job khong ton tai -> 404 JOB_NOT_FOUND.
        assertError(perform("GET", url(missingJob), owner[0], null), 404, "JOB_NOT_FOUND", null);
        assertError(perform("POST", url(missingJob), owner[0], body), 404, "JOB_NOT_FOUND", null);

        // HR chua co cong ty -> 404 COMPANY_NOT_FOUND, ke ca khi jobId khong ton tai (cong ty kiem TRUOC, R-A2).
        assertError(perform("GET", url(missingJob), noCompanyToken, null), 404, "COMPANY_NOT_FOUND", null);
        assertError(perform("GET", url(owner[1]), noCompanyToken, null), 404, "COMPANY_NOT_FOUND", null);
        assertError(perform("POST", url(missingJob), noCompanyToken, body), 404, "COMPANY_NOT_FOUND", null);

        // Cau hoi cua Job khac tren duong dan -> 404 SCREENING_QUESTION_NOT_FOUND.
        String sameOwnerOtherJob = createJob(owner[0], "Tin thu hai");
        assertError(
                perform("PUT", url(sameOwnerOtherJob) + "/" + questionId, owner[0], body),
                404,
                "SCREENING_QUESTION_NOT_FOUND",
                null);
        assertError(
                perform("DELETE", url(sameOwnerOtherJob) + "/" + questionId, owner[0], null),
                404,
                "SCREENING_QUESTION_NOT_FOUND",
                null);

        // Ung vien -> 403; khong token -> 401.
        assertThat(perform("GET", url(owner[1]), candidateToken, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform("POST", url(owner[1]), candidateToken, body).getResponse().getStatus()).isEqualTo(403);
        MvcResult anonymous = perform("GET", url(owner[1]), null, null);
        assertError(anonymous, 401, "UNAUTHENTICATED", null);

        // Khong thao tac nao o tren lam doi bo cau hoi cua chu.
        assertThat(list(owner[0], owner[1]).get("questions")).hasSize(1);
    }

    // ---- T9 ----

    @Test
    void t9_editableWhenJobOpenWithApplicationAndRubricLocked() throws Exception {
        String[] hr = hrWithJob("t9");
        String a = addOk(hr[0], hr[1], shortText("Cau A"));
        String b = addOk(hr[0], hr[1], shortText("Cau B"));

        UUID jobId = UUID.fromString(hr[1]);
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.OPEN);
        job.setPublishedAt(Instant.now());
        jobRepository.saveAndFlush(job);
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow();
        rubric.setLocked(true);
        rubricRepository.saveAndFlush(rubric);
        createApplication(jobId);

        assertThat(add(hr[0], hr[1], shortText("Cau C")).getResponse().getStatus()).isEqualTo(201);
        assertThat(perform("PUT", url(hr[1]) + "/" + a, hr[0], shortText("Cau A da sua")).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(perform("PUT", url(hr[1]) + "/order", hr[0], orderJson(b, a, list(hr[0], hr[1])
                                .get("questions").get(2).get("id").asString()))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(perform("DELETE", url(hr[1]) + "/" + b, hr[0], null).getResponse().getStatus()).isEqualTo(204);
        assertThat(list(hr[0], hr[1]).get("questions")).hasSize(2);
    }

    private void createApplication(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        User candidate = new User();
        candidate.setEmail(uniqueEmail("t9-cand"));
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

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        jobApplicationRepository.saveAndFlush(application);
    }

    // ---- T16 (S1, S2) ----

    @Test
    void t16_responsesHaveNoForbiddenKeys() throws Exception {
        String[] hr = hrWithJob("t16");
        MvcResult created = add(hr[0], hr[1], singleChoice(List.of("Co", "Khong ro")));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);

        Set<String> keys = new HashSet<>();
        collectKeys(body(created), keys);
        collectKeys(list(hr[0], hr[1]), keys);
        assertThat(keys).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
        assertThat(keys).contains("options", "questions", "revision", "maxQuestions");
    }
}

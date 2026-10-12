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
import java.util.Arrays;
import java.util.HashMap;
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

// FR-U09 F2 (POST /api/candidates/applications) - T3, T4, T5, T6, T7, T9, T17 (phan F2).
// KHONG @Transactional toan lop (tien le ApplicationIntegrationTest): ca nop trung (409 APPLICATION_DUPLICATE) la vi pham
// constraint, Postgres huy ca transaction bao ngoai va cac khang dinh sau se sai vi ly do khac; dong thoi phai thay du
// lieu da COMMIT that (ban chup ghi cung transaction voi don). Moi test tu tao HR/cong ty/tin/ung vien ngau nhien, chi
// khang dinh tren du lieu cua chinh test, khong don sau test (L2 cua FR-H10).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationScreeningCaptureIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PDF_CONTENT = "%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8);

    // Cau co dinh R-V1 / muc 4.6 - viet nguyen van (khong doc tu enum) de test bat duoc sai chu.
    private static final String MSG_UNKNOWN = "Câu trả lời không khớp với câu hỏi sàng lọc của tin tuyển dụng.";
    private static final String MSG_DUPLICATE = "Mỗi câu hỏi sàng lọc chỉ được trả lời một lần.";
    private static final String MSG_TYPE = "Câu trả lời không đúng loại câu hỏi sàng lọc.";
    private static final String MSG_REQUIRED = "Vui lòng trả lời đầy đủ các câu hỏi sàng lọc bắt buộc.";
    private static final String MSG_TOO_LONG = "Câu trả lời sàng lọc tối đa 500 ký tự.";
    private static final String MSG_OPTION = "Phương án đã chọn không có trong câu hỏi sàng lọc.";
    private static final String MSG_CHANGED =
            "Nhà tuyển dụng vừa cập nhật câu hỏi sàng lọc. Vui lòng kiểm tra lại câu trả lời rồi nộp lại.";

    private static final String EMOJI = "😀"; // ngoai BMP: 1 code point, String.length() = 2
    private static final List<String> SALARY_OPTIONS = List.of("Dưới 10 triệu", "10–14 triệu", "Trên 14 triệu");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- du lieu ----

    private record Candidate(String token, String resumeId) {
    }

    // Tin co ba cau: Q1 SHORT_TEXT bat buoc, Q2 YES_NO, Q3 SINGLE_CHOICE khong bat buoc (Q2 bat buoc tuy tin).
    private record ScreenedJob(String hrToken, String jobId, String q1, String q2, String q3) {
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
        String email = uniqueEmail("u09-cap-hr");
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

    private String createJob(String hrToken) throws Exception {
        String body =
                """
                {
                  "job": {"title":"Nhan vien CSKH %s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao ung vien.","senderName":"Phong Nhan Su"}
                }
                """
                        .formatted(UUID.randomUUID());
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asString();
    }

    // Mo tin bang luong that (rubric du trong so roi PATCH status), nhu ApplicationIntegrationTest.
    private String createOpenJob(String hrToken) throws Exception {
        String jobId = createJob(hrToken);
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

    private String addQuestion(String hrToken, String jobId, String type, String text, List<String> options, boolean required)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("questionType", type);
        request.put("questionText", text);
        request.put("options", options);
        request.put("required", required);
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/screening-questions")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asString();
    }

    private void deleteQuestion(String hrToken, String jobId, String questionId) throws Exception {
        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId + "/screening-questions/" + questionId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());
    }

    private ScreenedJob createScreenedJob(boolean yesNoRequired) throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        String q1 = addQuestion(hrToken, jobId, "SHORT_TEXT", "Bạn có thể bắt đầu đi làm từ khi nào?", null, true);
        String q2 = addQuestion(hrToken, jobId, "YES_NO", "Bạn có sẵn sàng làm ca tối không?", null, yesNoRequired);
        String q3 = addQuestion(hrToken, jobId, "SINGLE_CHOICE", "Mức lương mong muốn của bạn?", SALARY_OPTIONS, false);
        return new ScreenedJob(hrToken, jobId, q1, q2, q3);
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
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return new Candidate(token, body(result).get("id").asString());
    }

    private int revision(String jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, UUID.fromString(jobId));
    }

    // HashMap (khong Map.of) vi gia tri null co chu dich.
    private static Map<String, Object> answer(String questionId, String text, Boolean yesNo) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("questionId", questionId);
        answer.put("answerText", text);
        answer.put("answerYesNo", yesNo);
        return answer;
    }

    private static List<Object> answers(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private MvcResult apply(Candidate candidate, String jobId, Integer revision, List<Object> answers, Boolean consent)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jobId", jobId);
        request.put("resumeId", candidate.resumeId());
        request.put("aiConsent", true);
        request.put("screeningRevision", revision);
        request.put("answers", answers);
        request.put("talentPoolConsent", consent);
        return applyRaw(candidate.token(), JSON.writeValueAsString(request));
    }

    private MvcResult applyRaw(String token, String json) throws Exception {
        return mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
    }

    private static String createdId(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        return body(result).get("id").asString();
    }

    private static void assertError(MvcResult result, int status, String error, String message) throws Exception {
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(raw).isEqualTo(status);
        JsonNode json = JSON.readTree(raw);
        assertThat(json.get("error").asString()).isEqualTo(error);
        if (message != null) {
            assertThat(json.get("message").asString()).isEqualTo(message);
        }
    }

    private void assertAnswerError(MvcResult result, String message) throws Exception {
        assertError(result, 400, "INVALID_SCREENING_ANSWER", message);
    }

    private int applicationCount(Candidate candidate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_applications WHERE resume_id = ?",
                Integer.class,
                UUID.fromString(candidate.resumeId()));
    }

    private int snapshotCount(String applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_screening_answers WHERE application_id = ?",
                Integer.class,
                UUID.fromString(applicationId));
    }

    private int hrNotificationCount(String jobId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM notifications n
                WHERE n.user_id = (SELECT c.owner_id FROM companies c JOIN jobs j ON j.company_id = c.id WHERE j.id = ?)
                """,
                Integer.class,
                UUID.fromString(jobId));
    }

    private Map<String, Object> applicationRow(String applicationId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT ai_consent_at, screening_captured_at, talent_pool_consent, talent_pool_consent_at,
                       talent_pool_consent_revoked_at
                FROM job_applications WHERE id = ?
                """,
                UUID.fromString(applicationId));
    }

    // ---- T3: ban chup ghi that trong cung transaction nop don ----

    @Test
    void t3_applyWithThreeQuestions_writesSnapshotRowsInSameTransaction() throws Exception {
        ScreenedJob job = createScreenedJob(false);
        Candidate candidate = newCandidate("u09-t3");

        MvcResult result = apply(
                candidate,
                job.jobId(),
                revision(job.jobId()),
                answers(
                        answer(job.q1(), "  Đầu tháng 11/2026,\nsau khi bàn giao.  ", null),
                        answer(job.q3(), " 10–14 triệu ", null)),
                null);
        String applicationId = createdId(result);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, question_id, display_order, question_type_snapshot, question_text_snapshot,
                       options_snapshot::text AS options_json, required_snapshot, answer_text, answer_yes_no
                FROM application_screening_answers WHERE application_id = ? ORDER BY display_order
                """,
                UUID.fromString(applicationId));
        assertThat(rows).as("moi cau dang hoat dong mot dong, ke ca cau bo trong").hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("id")).as("id sinh boi @UuidGenerator").isNotNull());

        Map<String, Object> first = rows.get(0);
        assertThat(first.get("question_id")).isEqualTo(UUID.fromString(job.q1()));
        assertThat(first.get("display_order")).isEqualTo(1);
        assertThat(first.get("question_type_snapshot")).isEqualTo("SHORT_TEXT");
        assertThat(first.get("question_text_snapshot")).isEqualTo("Bạn có thể bắt đầu đi làm từ khi nào?");
        assertThat(first.get("options_json")).isNull();
        assertThat(first.get("required_snapshot")).isEqualTo(true);
        assertThat(first.get("answer_text")).as("bo khoang trang hai dau, giu xuong dong").isEqualTo(
                "Đầu tháng 11/2026,\nsau khi bàn giao.");
        assertThat(first.get("answer_yes_no")).isNull();

        Map<String, Object> second = rows.get(1);
        assertThat(second.get("question_id")).isEqualTo(UUID.fromString(job.q2()));
        assertThat(second.get("question_type_snapshot")).isEqualTo("YES_NO");
        assertThat(second.get("required_snapshot")).isEqualTo(false);
        assertThat(second.get("answer_text")).isNull();
        assertThat(second.get("answer_yes_no")).as("cau khong bat buoc bo trong").isNull();

        Map<String, Object> third = rows.get(2);
        assertThat(third.get("question_id")).isEqualTo(UUID.fromString(job.q3()));
        assertThat(third.get("question_type_snapshot")).isEqualTo("SINGLE_CHOICE");
        assertThat(JSON.readValue((String) third.get("options_json"), List.class)).isEqualTo(SALARY_OPTIONS);
        assertThat(third.get("answer_text")).as("luu chu cua phuong an").isEqualTo("10–14 triệu");

        Map<String, Object> application = applicationRow(applicationId);
        assertThat(application.get("screening_captured_at")).isNotNull();
        assertThat(application.get("screening_captured_at")).isEqualTo(application.get("ai_consent_at"));

        // S6 cua HR doc dung ban chup vua ghi.
        MvcResult hrView = mockMvc
                .perform(get("/api/hr/applications/" + applicationId + "/screening-answers")
                        .header("Authorization", "Bearer " + job.hrToken()))
                .andReturn();
        assertThat(hrView.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(hrView);
        assertThat(json.get("state").asString()).isEqualTo("CAPTURED");
        assertThat(json.get("items").size()).isEqualTo(3);
        assertThat(json.get("items").get(0).get("answerText").asString())
                .isEqualTo("Đầu tháng 11/2026,\nsau khi bàn giao.");
        assertThat(json.get("items").get(1).get("answerYesNo").isNull()).isTrue();
        assertThat(json.get("items").get(2).get("answerText").asString()).isEqualTo("10–14 triệu");

        // L10 - F3 cua chinh ung vien tra cung ban chup, state CAPTURED.
        MvcResult candidateView = mockMvc
                .perform(get("/api/candidates/applications/" + applicationId + "/screening-answers")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andReturn();
        assertThat(candidateView.getResponse().getStatus()).isEqualTo(200);
        JsonNode mine = body(candidateView);
        assertThat(mine.get("state").asString()).isEqualTo("CAPTURED");
        assertThat(mine.get("items")).isEqualTo(json.get("items"));
    }

    // ---- T4: tin 0 cau ----

    @Test
    void t4_jobWithoutQuestions_currentRevision_capturedWithNoRows() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        Candidate candidate = newCandidate("u09-t4a");

        String applicationId = createdId(apply(candidate, jobId, revision(jobId), answers(), false));

        assertThat(snapshotCount(applicationId)).isZero();
        assertThat(applicationRow(applicationId).get("screening_captured_at")).isNotNull();
        MvcResult hrView = mockMvc
                .perform(get("/api/hr/applications/" + applicationId + "/screening-answers")
                        .header("Authorization", "Bearer " + hrToken))
                .andReturn();
        assertThat(body(hrView).get("state").asString()).isEqualTo("NO_QUESTIONS");
    }

    @Test
    void t4_legacyJsonWithoutNewFields_accepted_noQuestionsAndNoConsent() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        Candidate candidate = newCandidate("u09-t4b");

        String applicationId = createdId(applyRaw(
                candidate.token(),
                "{\"jobId\":\"%s\",\"resumeId\":\"%s\",\"aiConsent\":true}".formatted(jobId, candidate.resumeId())));

        assertThat(snapshotCount(applicationId)).isZero();
        Map<String, Object> application = applicationRow(applicationId);
        assertThat(application.get("screening_captured_at")).isEqualTo(application.get("ai_consent_at"));
        assertThat(application.get("talent_pool_consent")).isEqualTo(false);
        assertThat(application.get("talent_pool_consent_at")).isNull();
        assertThat(application.get("talent_pool_consent_revoked_at")).isNull();
        MvcResult hrView = mockMvc
                .perform(get("/api/hr/applications/" + applicationId + "/screening-answers")
                        .header("Authorization", "Bearer " + hrToken))
                .andReturn();
        assertThat(body(hrView).get("state").asString()).isEqualTo("NO_QUESTIONS");
    }

    // ---- T5: 409 SCREENING_QUESTIONS_CHANGED ----

    @Test
    void t5_nullRevision_whenJobHasQuestions_conflict() throws Exception {
        ScreenedJob job = createScreenedJob(false);
        Candidate candidate = newCandidate("u09-t5a");
        int notificationsBefore = hrNotificationCount(job.jobId());

        assertError(
                apply(candidate, job.jobId(), null, answers(answer(job.q1(), "Ngay", null)), null),
                409,
                "SCREENING_QUESTIONS_CHANGED",
                MSG_CHANGED);

        assertThat(applicationCount(candidate)).isZero();
        assertThat(hrNotificationCount(job.jobId())).isEqualTo(notificationsBefore);
    }

    @Test
    void t5_staleRevisionAfterEachHrWrite_conflict_thenCurrentRevisionSucceeds() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        String q1 = addQuestion(hrToken, jobId, "SHORT_TEXT", "Cau 1", null, false);
        String q2 = addQuestion(hrToken, jobId, "SHORT_TEXT", "Cau 2", null, false);
        Candidate candidate = newCandidate("u09-t5b");
        int notificationsBefore = hrNotificationCount(jobId);

        // Them
        int stale = revision(jobId);
        String q3 = addQuestion(hrToken, jobId, "YES_NO", "Cau 3", null, false);
        assertError(apply(candidate, jobId, stale, answers(), null), 409, "SCREENING_QUESTIONS_CHANGED", MSG_CHANGED);

        // Sua
        stale = revision(jobId);
        mockMvc
                .perform(put("/api/hr/jobs/" + jobId + "/screening-questions/" + q1)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Cau 1 da sua\"}"))
                .andExpect(status().isOk());
        assertError(apply(candidate, jobId, stale, answers(), null), 409, "SCREENING_QUESTIONS_CHANGED", MSG_CHANGED);

        // Xoa
        stale = revision(jobId);
        deleteQuestion(hrToken, jobId, q3);
        assertError(apply(candidate, jobId, stale, answers(), null), 409, "SCREENING_QUESTIONS_CHANGED", MSG_CHANGED);

        // Doi thu tu
        stale = revision(jobId);
        mockMvc
                .perform(put("/api/hr/jobs/" + jobId + "/screening-questions/order")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[\"%s\",\"%s\"]}".formatted(q2, q1)))
                .andExpect(status().isOk());
        assertError(apply(candidate, jobId, stale, answers(), null), 409, "SCREENING_QUESTIONS_CHANGED", MSG_CHANGED);

        // Moi lan bi chan: khong don, khong lich su, khong ban chup, khong thong bao.
        assertThat(applicationCount(candidate)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM application_status_history h
                        JOIN job_applications a ON a.id = h.application_id WHERE a.resume_id = ?
                        """,
                        Integer.class,
                        UUID.fromString(candidate.resumeId())))
                .isZero();
        assertThat(hrNotificationCount(jobId)).isEqualTo(notificationsBefore);

        // Doi chung duong: revision hien tai thi nop duoc.
        String applicationId = createdId(apply(candidate, jobId, revision(jobId), answers(), null));
        assertThat(snapshotCount(applicationId)).isEqualTo(2);
    }

    @Test
    void t5_revisionMismatch_whenJobHasZeroQuestions_conflict_butNullAccepted() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        String temp = addQuestion(hrToken, jobId, "SHORT_TEXT", "Tam", null, false);
        deleteQuestion(hrToken, jobId, temp);
        assertThat(revision(jobId)).isEqualTo(2);
        Candidate candidate = newCandidate("u09-t5c");

        assertError(apply(candidate, jobId, 0, answers(), null), 409, "SCREENING_QUESTIONS_CHANGED", MSG_CHANGED);
        assertThat(applicationCount(candidate)).isZero();

        // R-R1 - null = client khong thay cau hoi nao; tin hien co 0 cau thi nhan.
        String applicationId = createdId(apply(candidate, jobId, null, answers(), null));
        assertThat(snapshotCount(applicationId)).isZero();
    }

    // ---- T6: bien va hinh dang (B2) ----

    @Test
    void t6_shortTextLengthBoundaries_countedAfterStripByCodePoint() throws Exception {
        ScreenedJob job = createScreenedJob(true);
        int rev = revision(job.jobId());

        String a499 = createdId(apply(newCandidate("u09-t6-499"), job.jobId(), rev,
                answers(answer(job.q1(), "a".repeat(499), null), answer(job.q2(), null, true)), null));
        String a500 = createdId(apply(newCandidate("u09-t6-500"), job.jobId(), rev,
                answers(answer(job.q1(), "b".repeat(500), null), answer(job.q2(), null, true)), null));
        String padded = createdId(apply(newCandidate("u09-t6-pad"), job.jobId(), rev,
                answers(answer(job.q1(), " " + "c".repeat(500) + " ", null), answer(job.q2(), null, true)), null));
        String emoji500 = createdId(apply(newCandidate("u09-t6-emo"), job.jobId(), rev,
                answers(answer(job.q1(), EMOJI.repeat(500), null), answer(job.q2(), null, false)), null));

        assertThat(firstAnswerLength(a499)).isEqualTo(499);
        assertThat(firstAnswerLength(a500)).isEqualTo(500);
        assertThat(firstAnswerLength(padded)).as("luu gia tri da bo khoang trang").isEqualTo(500);
        assertThat(firstAnswerLength(emoji500)).as("500 code point, String.length() = 1000").isEqualTo(500);

        Candidate rejected = newCandidate("u09-t6-long");
        assertAnswerError(apply(rejected, job.jobId(), rev,
                answers(answer(job.q1(), "d".repeat(501), null), answer(job.q2(), null, true)), null), MSG_TOO_LONG);
        assertAnswerError(apply(rejected, job.jobId(), rev,
                answers(answer(job.q1(), " " + "e".repeat(501), null), answer(job.q2(), null, true)), null), MSG_TOO_LONG);
        assertAnswerError(apply(rejected, job.jobId(), rev,
                answers(answer(job.q1(), EMOJI.repeat(501), null), answer(job.q2(), null, true)), null), MSG_TOO_LONG);
        assertThat(applicationCount(rejected)).isZero();
    }

    private int firstAnswerLength(String applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT char_length(answer_text) FROM application_screening_answers WHERE application_id = ? AND display_order = 1",
                Integer.class,
                UUID.fromString(applicationId));
    }

    @Test
    void t6_requiredQuestionsMissingOrBlank_rejected() throws Exception {
        ScreenedJob job = createScreenedJob(true);
        int rev = revision(job.jobId());
        Candidate candidate = newCandidate("u09-t6-req");
        Map<String, Object> yes = answer(job.q2(), null, true);

        assertAnswerError(apply(candidate, job.jobId(), rev, answers(yes), null), MSG_REQUIRED);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(answer(job.q1(), null, null), yes), null),
                MSG_REQUIRED);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(answer(job.q1(), "", null), yes), null),
                MSG_REQUIRED);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(answer(job.q1(), "   ", null), yes), null),
                MSG_REQUIRED);
        // YES_NO bat buoc: co phan tu nhung answerYesNo null, va khong co phan tu.
        assertAnswerError(apply(candidate, job.jobId(), rev,
                answers(answer(job.q1(), "Ngay", null), answer(job.q2(), null, null)), null), MSG_REQUIRED);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(answer(job.q1(), "Ngay", null)), null),
                MSG_REQUIRED);
        assertThat(applicationCount(candidate)).isZero();
    }

    @Test
    void t6_singleChoiceOutsideOptions_rejected_paddedOptionAccepted() throws Exception {
        ScreenedJob job = createScreenedJob(true);
        int rev = revision(job.jobId());
        Candidate candidate = newCandidate("u09-t6-opt");
        Map<String, Object> q1 = answer(job.q1(), "Ngay", null);
        Map<String, Object> q2 = answer(job.q2(), null, true);

        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, answer(job.q3(), "Khác", null)), null),
                MSG_OPTION);
        assertAnswerError(apply(candidate, job.jobId(), rev,
                answers(q1, q2, answer(job.q3(), "dưới 10 triệu", null)), null), MSG_OPTION);
        assertThat(applicationCount(candidate)).isZero();

        String applicationId = createdId(apply(candidate, job.jobId(), rev,
                answers(q1, q2, answer(job.q3(), "  Dưới 10 triệu  ", null)), null));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT answer_text FROM application_screening_answers WHERE application_id = ? AND display_order = 3",
                        String.class,
                        UUID.fromString(applicationId)))
                .isEqualTo("Dưới 10 triệu");
    }

    @Test
    void t6_unknownOrDuplicateQuestionIds_rejected() throws Exception {
        ScreenedJob job = createScreenedJob(true);
        String extra = addQuestion(job.hrToken(), job.jobId(), "SHORT_TEXT", "Cau se xoa", null, false);
        deleteQuestion(job.hrToken(), job.jobId(), extra);
        ScreenedJob other = createScreenedJob(true);
        int rev = revision(job.jobId());
        Candidate candidate = newCandidate("u09-t6-unk");
        Map<String, Object> q1 = answer(job.q1(), "Ngay", null);
        Map<String, Object> q2 = answer(job.q2(), null, true);

        assertAnswerError(apply(candidate, job.jobId(), rev,
                answers(q1, q2, answer(UUID.randomUUID().toString(), "x", null)), null), MSG_UNKNOWN);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, answer(other.q1(), "x", null)), null),
                MSG_UNKNOWN);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, answer(extra, "x", null)), null),
                MSG_UNKNOWN);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, answer(null, "x", null)), null),
                MSG_UNKNOWN);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, null), null), MSG_UNKNOWN);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, answer(job.q1(), "Lan hai", null)), null),
                MSG_DUPLICATE);
        assertThat(applicationCount(candidate)).isZero();
    }

    @Test
    void t6_answerShapeNotMatchingQuestionType_rejected() throws Exception {
        ScreenedJob job = createScreenedJob(true);
        int rev = revision(job.jobId());
        Candidate candidate = newCandidate("u09-t6-type");
        Map<String, Object> q1 = answer(job.q1(), "Ngay", null);
        Map<String, Object> q2 = answer(job.q2(), null, true);

        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, answer(job.q2(), "Có", true)), null), MSG_TYPE);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(answer(job.q1(), "Ngay", true), q2), null),
                MSG_TYPE);
        assertAnswerError(apply(candidate, job.jobId(), rev, answers(q1, q2, answer(job.q3(), null, false)), null),
                MSG_TYPE);
        assertThat(applicationCount(candidate)).isZero();
    }

    // ---- T7: thu tu loi (R-V3, R-V2) ----

    @Test
    void t7_errorPrecedence() throws Exception {
        ScreenedJob job = createScreenedJob(true);
        int rev = revision(job.jobId());
        List<Object> invalid = answers(answer(job.q1(), "", null));
        List<Object> valid = answers(answer(job.q1(), "Ngay", null), answer(job.q2(), null, true));
        Candidate candidate = newCandidate("u09-t7");
        Candidate stranger = newCandidate("u09-t7-other");

        // Tin chua mo + cau tra loi sai -> 404 JOB_NOT_FOUND.
        String draftJob = createJob(job.hrToken());
        assertError(apply(candidate, draftJob, rev, invalid, null), 404, "JOB_NOT_FOUND", null);

        // CV cua nguoi khac + cau tra loi sai -> 404 RESUME_NOT_FOUND.
        assertError(
                apply(new Candidate(candidate.token(), stranger.resumeId()), job.jobId(), rev, invalid, null),
                404,
                "RESUME_NOT_FOUND",
                null);

        // Revision cu + cau tra loi sai -> 409.
        assertError(apply(candidate, job.jobId(), rev - 1, invalid, null), 409, "SCREENING_QUESTIONS_CHANGED",
                MSG_CHANGED);

        // Mot cau vi pham hai quy tac -> loi dung truoc trong R-V2 (TYPE_MISMATCH truoc REQUIRED_MISSING).
        assertAnswerError(apply(candidate, job.jobId(), rev,
                answers(answer(job.q1(), null, true), answer(job.q2(), null, true)), null), MSG_TYPE);
        // UNKNOWN duoc tim tren CA danh sach truoc DUPLICATE.
        assertAnswerError(apply(candidate, job.jobId(), rev,
                answers(answer(job.q1(), "a", null), answer(job.q1(), "b", null), answer(UUID.randomUUID().toString(), "c", null)),
                null), MSG_UNKNOWN);

        String first = createdId(apply(candidate, job.jobId(), rev, valid, null));
        assertThat(snapshotCount(first)).isEqualTo(3);

        // Da nop + cau tra loi dung -> 409 trung don; ban chup cua don dau khong doi.
        assertError(apply(candidate, job.jobId(), rev, valid, null), 409, "APPLICATION_DUPLICATE", null);
        assertThat(snapshotCount(first)).isEqualTo(3);
        // Da nop + cau tra loi sai -> 400 (kiem truoc INSERT).
        assertAnswerError(apply(candidate, job.jobId(), rev, invalid, null), MSG_REQUIRED);
        assertThat(applicationCount(candidate)).isEqualTo(1);
    }

    // ---- T9: co dong y luc nop ----

    @Test
    void t9_talentPoolConsent_trueFalseAbsent() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        int rev = revision(jobId);

        Candidate agreedCandidate = newCandidate("u09-t9-yes");
        String agreedId = createdId(apply(agreedCandidate, jobId, rev, answers(), true));
        Map<String, Object> agreed = applicationRow(agreedId);
        assertThat(agreed.get("talent_pool_consent")).isEqualTo(true);
        assertThat(agreed.get("talent_pool_consent_at")).isNotNull().isEqualTo(agreed.get("ai_consent_at"));
        assertThat(agreed.get("talent_pool_consent_revoked_at")).isNull();
        // L10 - E1 tra state qua API, khong chi doc DB.
        JsonNode agreedConsent = detailConsent(agreedCandidate, agreedId);
        assertThat(agreedConsent.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(agreedConsent.get("consentedAt").isNull()).isFalse();
        assertThat(agreedConsent.get("revokedAt").isNull()).isTrue();

        for (Boolean consent : Arrays.asList(false, null)) {
            Candidate declinedCandidate = newCandidate("u09-t9-no");
            String declinedId = createdId(apply(declinedCandidate, jobId, rev, answers(), consent));
            Map<String, Object> declined = applicationRow(declinedId);
            assertThat(declined.get("talent_pool_consent")).as("consent=%s", consent).isEqualTo(false);
            assertThat(declined.get("talent_pool_consent_at")).isNull();
            assertThat(declined.get("talent_pool_consent_revoked_at")).isNull();
            JsonNode declinedConsent = detailConsent(declinedCandidate, declinedId);
            assertThat(declinedConsent.get("state").asString()).as("consent=%s", consent).isEqualTo("NOT_GIVEN");
            assertThat(declinedConsent.get("consentedAt").isNull()).isTrue();
            assertThat(declinedConsent.get("revokedAt").isNull()).isTrue();
        }
    }

    private JsonNode detailConsent(Candidate candidate, String applicationId) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/candidates/applications/" + applicationId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result).get("talentPoolConsent");
    }

    // ---- T17 (phan F2): thong bao cho HR khong doi ----

    @Test
    void t17_submittedNotification_unchangedByAnswersAndConsent() throws Exception {
        String hrToken = hrWithCompany();
        String jobId = createOpenJob(hrToken);
        String q1 = addQuestion(hrToken, jobId, "SHORT_TEXT", "Cau khong bat buoc", null, false);
        int rev = revision(jobId);

        String withAnswers = createdId(apply(newCandidate("u09-t17-a"), jobId, rev,
                answers(answer(q1, "Tra loi co the chua thong tin rieng", null)), true));
        String withoutAnswers = createdId(apply(newCandidate("u09-t17-b"), jobId, rev, answers(), false));

        Map<String, Object> first = notificationOf(withAnswers);
        Map<String, Object> second = notificationOf(withoutAnswers);
        assertThat(first.get("type")).isEqualTo("APPLICATION_SUBMITTED");
        assertThat(second.get("type")).isEqualTo("APPLICATION_SUBMITTED");
        assertThat(first.get("user_id")).isEqualTo(second.get("user_id"));
        assertThat(first.get("title")).isEqualTo(second.get("title"));
        assertThat(first.get("body")).as("cung ten ung vien, cung tin").isEqualTo(second.get("body"));
        assertThat((String) first.get("body")).doesNotContain("Tra loi co the chua thong tin rieng");
        assertThat(first.get("link")).isEqualTo("/hr/applications/" + withAnswers);
        assertThat(second.get("link")).isEqualTo("/hr/applications/" + withoutAnswers);
    }

    private Map<String, Object> notificationOf(String applicationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id, type, title, body, link FROM notifications WHERE entity_id = ?",
                UUID.fromString(applicationId));
        assertThat(rows).as("dung mot thong bao cho don").hasSize(1);
        return rows.get(0);
    }
}

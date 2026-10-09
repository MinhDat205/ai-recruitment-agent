package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 dot 2 (muc 12 L1) - T1, T7, T8, phan tin chu cua T9, T24. Lop tu tao TOAN BO du lieu qua API/
// repository (email duy nhat), khong doc seed. Bo helper chep theo ApplicationCandidateDetailControllerIntegrationTest
// (tien le du an: khong tach tien ich test dung chung).
// @Transactional cap lop: moi @Test rollback rieng. Lop nay KHONG khang dinh thu tu thoi gian giua cac tin
// (T5 nam o MessageReadOrderIntegrationTest, khong boc @Transactional - CLAUDE.md muc 3c).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageSendIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---- helper (chep theo ApplicationCandidateDetailControllerIntegrationTest) ----

    // Phan truoc @ = prefix + "-" + UUID (36 ky tu) phai <= 64 ky tu -> prefix toi da 27 ky tu, ke ca hau to
    // helper tu noi them ("-hr", "-cand").
    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
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
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung Test","phone":"0900000000"}
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

    private void addCriterion(String token, String jobId) throws Exception {
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
    }

    private void openJob(String token, String jobId) throws Exception {
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
    }

    private String uploadResume(String candidateToken) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private String apply(String candidateToken, String jobId, String resumeId) throws Exception {
        String body = """
                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                """.formatted(jobId, resumeId);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private record Fixture(String hrToken, String candidateToken, String applicationId) {

        String hrPath() {
            return "/api/hr/applications/" + applicationId + "/messages";
        }

        String candidatePath() {
            return "/api/candidates/applications/" + applicationId + "/messages";
        }
    }

    private Fixture createApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, candidateToken, applicationId);
    }

    // M2 dang multipart/form-data (giao dien gui FormData) - chi field body, khong file.
    private MvcResult sendText(String token, String path, String text) throws Exception {
        var request = multipart(path).header("Authorization", "Bearer " + token);
        if (text != null) {
            request = request.param("body", text);
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode getThread(String token, String path) throws Exception {
        MvcResult result = mockMvc
                .perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private void setStatus(String applicationId, ApplicationStatus status) {
        JobApplication application =
                jobApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        application.setStatus(status);
        jobApplicationRepository.saveAndFlush(application);
    }

    private long countMessages(String applicationId) {
        return entityManager
                .createQuery(
                        "SELECT COUNT(m) FROM ApplicationMessage m WHERE m.applicationId = :id", Long.class)
                .setParameter("id", UUID.fromString(applicationId))
                .getSingleResult();
    }

    private void assertError(MvcResult result, int httpStatus, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(httpStatus);
        assertThat(body(result).get("error").asString()).isEqualTo(code);
    }

    // ---- T1 ----

    @Test
    void hrSendsText_candidateSeesItAsNotMine_hrSeesItAsMine() throws Exception {
        Fixture f = createApplication("c06-t1-hr");

        MvcResult sent = sendText(f.hrToken(), f.hrPath(), "Chào bạn, hồ sơ đang được xem xét.");
        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = body(sent);
        assertThat(created.get("mine").asBoolean()).isTrue();
        assertThat(created.get("senderRole").asString()).isEqualTo("HR");

        JsonNode candidateMessages = getThread(f.candidateToken(), f.candidatePath()).get("messages");
        assertThat(candidateMessages).hasSize(1);
        JsonNode seenByCandidate = candidateMessages.get(0);
        assertThat(seenByCandidate.get("mine").asBoolean()).isFalse();
        assertThat(seenByCandidate.get("senderRole").asString()).isEqualTo("HR");
        assertThat(seenByCandidate.get("body").asString()).isEqualTo("Chào bạn, hồ sơ đang được xem xét.");
        assertThat(seenByCandidate.get("attachment").isNull()).isTrue();
        assertThat(seenByCandidate.get("id").asString()).isEqualTo(created.get("id").asString());

        JsonNode hrMessages = getThread(f.hrToken(), f.hrPath()).get("messages");
        assertThat(hrMessages).hasSize(1);
        assertThat(hrMessages.get(0).get("mine").asBoolean()).isTrue();
    }

    @Test
    void candidateSendsFirst_hrSeesItAsNotMine_candidateSeesItAsMine() throws Exception {
        Fixture f = createApplication("c06-t1-uv");

        MvcResult sent = sendText(f.candidateToken(), f.candidatePath(), "Em muốn hỏi về tiến độ hồ sơ ạ.");
        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(sent).get("senderRole").asString()).isEqualTo("CANDIDATE");
        assertThat(body(sent).get("mine").asBoolean()).isTrue();

        JsonNode seenByHr = getThread(f.hrToken(), f.hrPath()).get("messages").get(0);
        assertThat(seenByHr.get("mine").asBoolean()).isFalse();
        assertThat(seenByHr.get("senderRole").asString()).isEqualTo("CANDIDATE");
        assertThat(seenByHr.get("body").asString()).isEqualTo("Em muốn hỏi về tiến độ hồ sơ ạ.");

        JsonNode seenByCandidate = getThread(f.candidateToken(), f.candidatePath()).get("messages").get(0);
        assertThat(seenByCandidate.get("mine").asBoolean()).isTrue();
    }

    // ---- T7 - nguong 4000 (String.length()) ----

    @Test
    void bodyLengthBoundary_3999And4000Accepted_4001Rejected() throws Exception {
        Fixture f = createApplication("c06-t7-len");

        assertThat(sendText(f.hrToken(), f.hrPath(), "a".repeat(3999)).getResponse().getStatus()).isEqualTo(201);
        assertThat(sendText(f.hrToken(), f.hrPath(), "b".repeat(4000)).getResponse().getStatus()).isEqualTo(201);
        assertError(sendText(f.hrToken(), f.hrPath(), "c".repeat(4001)), 400, "MESSAGE_TOO_LONG");
        assertThat(countMessages(f.applicationId())).isEqualTo(2);
    }

    @Test
    void whitespaceOnlyOrMissingBody_isEmpty() throws Exception {
        Fixture f = createApplication("c06-t7-empty");

        assertError(sendText(f.hrToken(), f.hrPath(), "   \r\n\t  "), 400, "MESSAGE_EMPTY");
        assertError(sendText(f.candidateToken(), f.candidatePath(), ""), 400, "MESSAGE_EMPTY");
        assertError(sendText(f.candidateToken(), f.candidatePath(), null), 400, "MESSAGE_EMPTY");
        assertThat(body(sendText(f.hrToken(), f.hrPath(), "   ")).get("message").asString())
                .isEqualTo("Tin nhắn chưa có nội dung");
        assertThat(countMessages(f.applicationId())).isZero();
    }

    // ---- T8 - nguyen van, chi doi CRLF/CR ----

    @Test
    void bodyKeptVerbatim_onlyLineBreaksNormalized() throws Exception {
        Fixture f = createApplication("c06-t8-raw");

        MvcResult sent = sendText(f.hrToken(), f.hrPath(), "  <b>x</b> & y\r\ndòng 2\r\n");
        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(sent).get("body").asString()).isEqualTo("  <b>x</b> & y\ndòng 2\n");

        sendText(f.hrToken(), f.hrPath(), "a\rb");

        JsonNode messages = getThread(f.candidateToken(), f.candidatePath()).get("messages");
        List<String> bodies = List.of(messages.get(0).get("body").asString(), messages.get(1).get("body").asString());
        assertThat(bodies).containsExactlyInAnyOrder("  <b>x</b> & y\ndòng 2\n", "a\nb");
    }

    @Test
    void bodyOf3999CharsPlusCrlf_isAcceptedBecauseNormalizedLengthIs4000() throws Exception {
        Fixture f = createApplication("c06-t8-crlf");

        MvcResult sent = sendText(f.candidateToken(), f.candidatePath(), "x".repeat(3999) + "\r\n");
        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(sent).get("body").asString()).hasSize(4000).endsWith("x\n");
    }

    // ---- T9 - trang thai don (phan tin chu) ----

    @Test
    void canSendInEveryStatusExceptWithdrawn_bothSides() throws Exception {
        Fixture f = createApplication("c06-t9-ok");

        for (ApplicationStatus s : List.of(
                ApplicationStatus.PENDING,
                ApplicationStatus.INTERVIEW_INVITED,
                ApplicationStatus.HIRED,
                ApplicationStatus.REJECTED)) {
            setStatus(f.applicationId(), s);
            assertThat(sendText(f.hrToken(), f.hrPath(), "HR " + s).getResponse().getStatus())
                    .as("HR gui o %s", s)
                    .isEqualTo(201);
            assertThat(sendText(f.candidateToken(), f.candidatePath(), "UV " + s).getResponse().getStatus())
                    .as("UV gui o %s", s)
                    .isEqualTo(201);
            assertThat(getThread(f.hrToken(), f.hrPath()).get("canSend").asBoolean()).isTrue();
            assertThat(getThread(f.candidateToken(), f.candidatePath()).get("canSend").asBoolean()).isTrue();
        }
        assertThat(countMessages(f.applicationId())).isEqualTo(8);
    }

    @Test
    void withdrawnApplication_isReadOnlyForBothSides_andNothingIsStored() throws Exception {
        Fixture f = createApplication("c06-t9-wd");
        sendText(f.hrToken(), f.hrPath(), "Tin trước khi rút đơn");
        setStatus(f.applicationId(), ApplicationStatus.WITHDRAWN);

        MvcResult byHr = sendText(f.hrToken(), f.hrPath(), "Xin chào");
        assertError(byHr, 409, "CONVERSATION_READ_ONLY");
        assertThat(body(byHr).get("message").asString()).isEqualTo("Đơn đã rút, không thể gửi thêm tin nhắn");
        assertError(sendText(f.candidateToken(), f.candidatePath(), "Xin chào"), 409, "CONVERSATION_READ_ONLY");
        assertThat(countMessages(f.applicationId())).isEqualTo(1);

        JsonNode hrThread = getThread(f.hrToken(), f.hrPath());
        assertThat(hrThread.get("canSend").asBoolean()).isFalse();
        assertThat(hrThread.get("messages")).hasSize(1);
        assertThat(getThread(f.candidateToken(), f.candidatePath()).get("canSend").asBoolean()).isFalse();
    }

    // R-M5 - trang thai don duoc kiem TRUOC noi dung; quyen duoc kiem TRUOC ca hai.
    @Test
    void checkOrder_statusBeforeContent_permissionBeforeContent() throws Exception {
        Fixture f = createApplication("c06-t9-order");
        setStatus(f.applicationId(), ApplicationStatus.WITHDRAWN);
        assertError(sendText(f.hrToken(), f.hrPath(), "   "), 409, "CONVERSATION_READ_ONLY");
        assertError(sendText(f.candidateToken(), f.candidatePath(), null), 409, "CONVERSATION_READ_ONLY");

        Fixture other = createApplication("c06-t9-other");
        assertThat(sendText(f.hrToken(), other.hrPath(), "   ").getResponse().getStatus()).isEqualTo(403);
        assertError(sendText(f.candidateToken(), other.candidatePath(), "   "), 404, "APPLICATION_NOT_FOUND");
        assertThat(countMessages(other.applicationId())).isZero();
    }

    // ---- T24 - urlencoded, khong co phan file ----

    @Test
    void urlEncodedRequestWithBodyOnly_isAccepted() throws Exception {
        Fixture f = createApplication("c06-t24");
        String text = "Xin chào, tôi muốn hỏi về tiến độ hồ sơ.";

        MvcResult result = mockMvc
                .perform(post(f.candidatePath())
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("body=" + URLEncoder.encode(text, StandardCharsets.UTF_8)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("body").asString()).isEqualTo(text);
        assertThat(body(result).get("attachment").isNull()).isTrue();
        assertThat(countMessages(f.applicationId())).isEqualTo(1);
    }
}

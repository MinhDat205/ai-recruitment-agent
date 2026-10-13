package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Mau y het ApplicationStatusControllerIntegrationTest (cung mot bo helper dang ky/dang nhap/tao
// du lieu, khong tach thanh tien ich dung chung - dung tien le cua toan bo cac test file trong du
// an nay). @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterviewInvitationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewTemplateRepository interviewTemplateRepository;

    @Autowired
    private InterviewInvitationRepository interviewInvitationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    private String registerAndLoginCandidate(String prefix, String fullName) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody = """
                {"email":"%s","password":"password123","fullName":"%s"}
                """.formatted(email, fullName);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    // Tra ve id cong ty (can de goi PUT doi ten sau nay, khac voi ban createCompany "khong tra id"
    // cua ApplicationStatusControllerIntegrationTest - tien le du an cho phep moi file test tu do
    // dieu chinh helper theo nhu cau rieng).
    private String createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        MvcResult result = mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void renameCompany(String token, String companyId, String newName) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(newName);
        mockMvc
                .perform(put("/api/hr/companies/" + companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // Body co placeholder {{candidateName}}/{{jobTitle}}/{{companyName}} de kiem render o preview.
    private String createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van - {{jobTitle}}",
                    "body":"Kinh chao {{candidateName}}, ban duoc moi phong van tai {{companyName}}.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title);
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void addCriterion(String token, String jobId, String requestBody) throws Exception {
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
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
        String body =
                """
                {"jobId":"%s","resumeId":"%s","aiConsent":true,"coverLetter":"Toi rat quan tam vi tri nay"}
                """
                        .formatted(jobId, resumeId);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private MvcResult preview(String token, String applicationId) throws Exception {
        return mockMvc
                .perform(get("/api/hr/applications/" + applicationId + "/interview-invitation/preview")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult sendInvitation(String token, String applicationId, Instant scheduledAt) throws Exception {
        // scheduledAt = null -> BO HAN field nay khoi khung 1 (khong phai gui chuoi "null") - FR-H12 L4: ca "thieu ngay
        // gio" nay gio la khung 1 thieu scheduledAt -> 400 INVALID_INTERVIEW_SCHEDULE (kiem o service, R-S8).
        String scheduledAtField = scheduledAt == null ? "" : "\"scheduledAt\":\"" + scheduledAt + "\",";
        String body =
                """
                {"slots":[{%s"location":"Van phong cong ty"}],"subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                """
                        .formatted(scheduledAtField);
        return mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult sendInvitation(String token, String applicationId) throws Exception {
        return sendInvitation(token, applicationId, Instant.now().plus(7, ChronoUnit.DAYS));
    }

    // Dung cho test bien @Size cua subject/content (Dot 3, muc 7) - scheduledAt luon hop le
    // (tuong lai 7 ngay), CHI subject/content thay doi de kiem dung nguong.
    private MvcResult sendInvitationWithSubjectAndContent(String token, String applicationId, String subject, String content)
            throws Exception {
        String body =
                """
                {"slots":[{"scheduledAt":"%s","location":"Van phong cong ty"}],"subject":"%s","content":"%s"}
                """
                        .formatted(Instant.now().plus(7, ChronoUnit.DAYS), subject, content);
        return mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    // Dung mot job/company/candidate/application PENDING moi cho moi test.
    private record Fixture(String hrToken, String companyId, String jobId, String applicationId) {
    }

    private Fixture createPendingApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        String companyId = createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand", "Nguyen Van Ung Vien");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, companyId, jobId, applicationId);
    }

    // ---- Preview ----

    @Test
    void preview_returnsRenderedSubjectAndContentWithCandidateName() throws Exception {
        Fixture fixture = createPendingApplication("preview-render");

        MvcResult result = preview(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat(extractJsonField(json, "candidateName")).isEqualTo("Nguyen Van Ung Vien");
        assertThat(extractJsonField(json, "content")).contains("Nguyen Van Ung Vien");
        assertThat(extractJsonField(json, "content")).doesNotContain("{{candidateName}}");
        assertThat(json).contains("\"companyNameMismatch\":false");
    }

    @Test
    void preview_companyNameChangedSinceTemplateCreated_returnsMismatchWarning() throws Exception {
        Fixture fixture = createPendingApplication("preview-mismatch");
        String originalCompanyName;
        {
            InterviewTemplate template = interviewTemplateRepository.findByJobId(UUID.fromString(fixture.jobId())).orElseThrow();
            originalCompanyName = template.getCompanyName();
        }
        renameCompany(fixture.hrToken(), fixture.companyId(), uniqueName("Cong ty Doi Ten"));

        MvcResult result = preview(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("\"companyNameMismatch\":true");
        // Noi dung van render bang ten cong ty DA DONG BANG trong template, khong phai ten moi.
        assertThat(extractJsonField(json, "templateCompanyName")).isEqualTo(originalCompanyName);
        assertThat(extractJsonField(json, "content")).contains(originalCompanyName);
    }

    @Test
    void preview_jobWithoutTemplate_returnsNotFound() throws Exception {
        Fixture fixture = createPendingApplication("preview-no-template");
        InterviewTemplate template =
                interviewTemplateRepository.findByJobId(UUID.fromString(fixture.jobId())).orElseThrow();
        interviewTemplateRepository.delete(template);

        MvcResult result = preview(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void preview_byHrOfAnotherCompany_returns403() throws Exception {
        Fixture fixture = createPendingApplication("preview-wrong-company");
        String otherHrToken = registerAndLoginHr("preview-other-hr");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult result = preview(otherHrToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    // ---- Gui loi moi ----

    @Test
    void send_validSchedule_returnsCreatedAndTransitionsStatus() throws Exception {
        Fixture fixture = createPendingApplication("send-valid");

        MvcResult result = sendInvitation(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        List<InterviewInvitation> invitations =
                interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId()));
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).getRenderedContent()).isEqualTo("Xin chao, moi ban tham gia phong van.");
        assertThat(invitations.get(0).getSentBy()).isNotNull();
        assertThat(invitations.get(0).getSentAt()).isNotNull();
    }

    @Test
    void send_pastScheduledAt_returns400() throws Exception {
        Fixture fixture = createPendingApplication("send-past");

        MvcResult result = sendInvitation(fixture.hrToken(), fixture.applicationId(), Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId())))
                .isEmpty();
    }

    @Test
    void send_missingScheduledAt_returns400() throws Exception {
        Fixture fixture = createPendingApplication("send-missing");

        MvcResult result = sendInvitation(fixture.hrToken(), fixture.applicationId(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("INVALID_INTERVIEW_SCHEDULE")
                .contains("Khung giờ 1: vui lòng chọn thời điểm phỏng vấn.");
        assertThat(interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId())))
                .isEmpty();
    }

    @Test
    void send_byHrOfAnotherCompany_returns403() throws Exception {
        Fixture fixture = createPendingApplication("send-wrong-company");
        String otherHrToken = registerAndLoginHr("send-other-hr");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult result = sendInvitation(otherHrToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void send_jobWithoutTemplate_returnsNotFound() throws Exception {
        Fixture fixture = createPendingApplication("send-no-template");
        InterviewTemplate template =
                interviewTemplateRepository.findByJobId(UUID.fromString(fixture.jobId())).orElseThrow();
        interviewTemplateRepository.delete(template);

        MvcResult result = sendInvitation(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void send_applicationAlreadyInterviewInvited_returns400() throws Exception {
        Fixture fixture = createPendingApplication("send-twice");
        MvcResult first = sendInvitation(fixture.hrToken(), fixture.applicationId());
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = sendInvitation(fixture.hrToken(), fixture.applicationId());

        assertThat(second.getResponse().getStatus()).isEqualTo(400);
        // Van chi co 1 dong - lan gui thu hai bi chan (don khong con PENDING), khong tao them.
        assertThat(interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId())))
                .hasSize(1);
    }

    @Test
    void send_thenTemplateEditedAfterward_invitationContentUnchanged() throws Exception {
        Fixture fixture = createPendingApplication("send-template-edited");
        sendInvitation(fixture.hrToken(), fixture.applicationId());

        List<InterviewInvitation> before =
                interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId()));
        String contentBefore = before.get(0).getRenderedContent();

        String updateTemplateBody =
                """
                {"subject":"Noi dung moi","body":"Noi dung mau da bi HR sua sau khi gui","senderName":"Phong Nhan Su Moi"}
                """;
        mockMvc
                .perform(put("/api/hr/jobs/" + fixture.jobId() + "/interview-template")
                        .header("Authorization", "Bearer " + fixture.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTemplateBody))
                .andExpect(status().isOk());

        List<InterviewInvitation> after =
                interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId()));
        assertThat(after.get(0).getRenderedContent()).isEqualTo(contentBefore);
        assertThat(after.get(0).getRenderedContent()).doesNotContain("Noi dung mau da bi HR sua sau khi gui");
    }

    // ---- Bien @Size cua subject/content (Dot 3, muc 7 - bo sung con thieu tu Dot 2) ----
    // Dung quy uoc bat buoc cua du an: nguong-1 / dung nguong / nguong+1. subject khop cot that
    // VARCHAR(255) (InterviewInvitationSendRequest.java), content la chan tang ung dung 10000 ky
    // tu (cot rendered_content la TEXT, khong gioi han o DB).

    @Test
    void send_subjectAt254Chars_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("subject-254");

        MvcResult result = sendInvitationWithSubjectAndContent(
                fixture.hrToken(), fixture.applicationId(), "A".repeat(254), "Noi dung hop le");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void send_subjectAt255Chars_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("subject-255");

        MvcResult result = sendInvitationWithSubjectAndContent(
                fixture.hrToken(), fixture.applicationId(), "A".repeat(255), "Noi dung hop le");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void send_subjectAt256Chars_returns400() throws Exception {
        Fixture fixture = createPendingApplication("subject-256");

        MvcResult result = sendInvitationWithSubjectAndContent(
                fixture.hrToken(), fixture.applicationId(), "A".repeat(256), "Noi dung hop le");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId())))
                .isEmpty();
    }

    @Test
    void send_contentAt9999Chars_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("content-9999");

        MvcResult result = sendInvitationWithSubjectAndContent(
                fixture.hrToken(), fixture.applicationId(), "Tieu de hop le", "A".repeat(9999));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void send_contentAt10000Chars_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("content-10000");

        MvcResult result = sendInvitationWithSubjectAndContent(
                fixture.hrToken(), fixture.applicationId(), "Tieu de hop le", "A".repeat(10000));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void send_contentAt10001Chars_returns400() throws Exception {
        Fixture fixture = createPendingApplication("content-10001");

        MvcResult result = sendInvitationWithSubjectAndContent(
                fixture.hrToken(), fixture.applicationId(), "Tieu de hop le", "A".repeat(10001));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(UUID.fromString(fixture.applicationId())))
                .isEmpty();
    }

    // =====================================================================================================
    // FR-H12 - khung gio (I2), gui lai (I3), doc phia HR (I1). Lop nay @Transactional: MockMvc chay cung luong nen
    // service tham gia transaction cua test - KHONG thay thong bao AFTER_COMMIT. Phan thong bao (T1, T5, T7, T8) o
    // InterviewInvitationResendIntegrationTest (khong @Transactional).
    // =====================================================================================================

    private static final String SUBJECT = "Thu moi phong van";
    private static final String CONTENT = "Xin chao, moi ban tham gia phong van.";

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String slot(Instant scheduledAt, String location) {
        return "{\"scheduledAt\":\"" + scheduledAt + "\",\"location\":" + json(location) + "}";
    }

    private static String slot(Instant scheduledAt) {
        return slot(scheduledAt, "Tang 3, 45 Bach Dang");
    }

    private static String bodyWithSlots(String... slots) {
        return "{\"slots\":[" + String.join(",", slots) + "],\"subject\":" + json(SUBJECT) + ",\"content\":"
                + json(CONTENT) + "}";
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private MvcResult postJson(String path, String token, String body) throws Exception {
        return mockMvc
                .perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult send(Fixture fixture, String body) throws Exception {
        return postJson("/api/hr/applications/" + fixture.applicationId() + "/interview-invitation", fixture.hrToken(), body);
    }

    private MvcResult resend(Fixture fixture, String body) throws Exception {
        return postJson(
                "/api/hr/applications/" + fixture.applicationId() + "/interview-invitation/resend", fixture.hrToken(), body);
    }

    private MvcResult list(String token, String applicationId) throws Exception {
        var request = get("/api/hr/applications/" + applicationId + "/interview-invitations");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static void assertScheduleError(MvcResult result, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo("INVALID_INTERVIEW_SCHEDULE");
        assertThat(json.get("message").asString()).isEqualTo(message);
    }

    private List<Map<String, Object>> invitationRows(String applicationId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, subject, rendered_content, sent_at, sent_by, confirmed_slot_id, confirmed_at, superseded_at
                FROM interview_invitations WHERE application_id = ? ORDER BY sent_at
                """,
                UUID.fromString(applicationId));
    }

    private List<Map<String, Object>> slotRows(Object invitationId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, display_order, scheduled_at, location
                FROM interview_invitation_slots WHERE invitation_id = ? ORDER BY display_order
                """,
                invitationId);
    }

    private int slotCountOfApplication(String applicationId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id WHERE i.application_id = ?
                """,
                Integer.class,
                UUID.fromString(applicationId));
    }

    private String statusOf(String applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM job_applications WHERE id = ?", String.class, UUID.fromString(applicationId));
    }

    private int historyCount(String applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class,
                UUID.fromString(applicationId));
    }

    private static Instant instant(Object timestamp) {
        return timestamp == null ? null : ((java.sql.Timestamp) timestamp).toInstant();
    }

    // Mot ca 400 cua I2: khong dong giay moi/khung nao, don van PENDING.
    private void assertNothingWritten(Fixture fixture) {
        assertThat(invitationRows(fixture.applicationId())).isEmpty();
        assertThat(slotCountOfApplication(fixture.applicationId())).isZero();
        assertThat(statusOf(fixture.applicationId())).isEqualTo("PENDING");
    }

    private Fixture createInvitedApplication(String prefix, String... slots) throws Exception {
        Fixture fixture = createPendingApplication(prefix);
        assertThat(send(fixture, bodyWithSlots(slots)).getResponse().getStatus()).isEqualTo(201);
        return fixture;
    }

    private void changeStatus(Fixture fixture, String newStatus) throws Exception {
        mockMvc.perform(patch("/api/hr/applications/" + fixture.applicationId() + "/status")
                        .header("Authorization", "Bearer " + fixture.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + newStatus + "\"}"))
                .andExpect(status().isOk());
    }

    // ---- T1 (phan DB/response; phan thong bao o InterviewInvitationResendIntegrationTest) ----

    @Test
    void t1_sendOneSlot_confirmedImmediately_statusInvitedOneHistoryRow() throws Exception {
        Fixture fixture = createPendingApplication("t1-one");
        int historyBefore = historyCount(fixture.applicationId());

        MvcResult result = send(fixture, bodyWithSlots(slot(inDays(3))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        List<Map<String, Object>> invitations = invitationRows(fixture.applicationId());
        assertThat(invitations).hasSize(1);
        Map<String, Object> invitation = invitations.get(0);
        assertThat(invitation.get("superseded_at")).isNull();
        assertThat(invitation.get("rendered_content")).isEqualTo(CONTENT);
        List<Map<String, Object>> slots = slotRows(invitation.get("id"));
        assertThat(slots).hasSize(1);
        assertThat(invitation.get("confirmed_slot_id")).isEqualTo(slots.get(0).get("id"));
        assertThat(instant(invitation.get("confirmed_at"))).isEqualTo(instant(invitation.get("sent_at")));
        assertThat(statusOf(fixture.applicationId())).isEqualTo("INTERVIEW_INVITED");
        assertThat(historyCount(fixture.applicationId())).isEqualTo(historyBefore + 1);

        JsonNode json = body(result);
        assertThat(json.get("active").asBoolean()).isTrue();
        assertThat(json.get("renderedContent").asString()).isEqualTo(CONTENT);
        assertThat(json.get("confirmedSlotId").asString()).isEqualTo(slots.get(0).get("id").toString());
        assertThat(json.get("slots").size()).isEqualTo(1);
        assertThat(json.get("slots").get(0).get("displayOrder").asInt()).isEqualTo(1);
    }

    // ---- T2 ----

    @Test
    void t2_sendTwoSlots_lateBeforeEarly_unconfirmed_keepsOrderAsSent() throws Exception {
        Fixture fixture = createPendingApplication("t2-two");
        Instant late = inDays(5);
        Instant early = inDays(2);

        MvcResult result = send(fixture, bodyWithSlots(slot(late, "Phong A"), slot(early, "Phong B")));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> invitation = invitationRows(fixture.applicationId()).get(0);
        assertThat(invitation.get("confirmed_slot_id")).isNull();
        assertThat(invitation.get("confirmed_at")).isNull();
        List<Map<String, Object>> slots = slotRows(invitation.get("id"));
        assertThat(slots).extracting(row -> ((Number) row.get("display_order")).intValue()).containsExactly(1, 2);
        assertThat(slots).extracting(row -> instant(row.get("scheduled_at"))).containsExactly(late, early);
        assertThat(slots).extracting(row -> row.get("location")).containsExactly("Phong A", "Phong B");
    }

    @Test
    void t2_sendFiveSlots_returnsCreated_unconfirmed() throws Exception {
        Fixture fixture = createPendingApplication("t2-five");

        MvcResult result = send(fixture, bodyWithSlots(
                slot(inDays(1)), slot(inDays(2)), slot(inDays(3)), slot(inDays(4)), slot(inDays(5))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> invitation = invitationRows(fixture.applicationId()).get(0);
        assertThat(invitation.get("confirmed_slot_id")).isNull();
        assertThat(slotRows(invitation.get("id")))
                .extracting(row -> ((Number) row.get("display_order")).intValue())
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void t2_sendSixSlots_returns400_nothingWritten() throws Exception {
        Fixture fixture = createPendingApplication("t2-six");

        MvcResult result = send(fixture, bodyWithSlots(
                slot(inDays(1)), slot(inDays(2)), slot(inDays(3)), slot(inDays(4)), slot(inDays(5)), slot(inDays(6))));

        assertScheduleError(result, "Giấy mời có tối đa 5 khung giờ.");
        assertNothingWritten(fixture);
    }

    @Test
    void t2_slotsNullMissingOrEmpty_returns400_nothingWritten() throws Exception {
        Fixture fixture = createPendingApplication("t2-empty");
        String message = "Vui lòng thêm ít nhất một khung giờ phỏng vấn.";

        assertScheduleError(send(fixture, "{\"slots\":null,\"subject\":\"S\",\"content\":\"C\"}"), message);
        assertScheduleError(send(fixture, "{\"subject\":\"S\",\"content\":\"C\"}"), message);
        assertScheduleError(send(fixture, "{\"slots\":[],\"subject\":\"S\",\"content\":\"C\"}"), message);
        assertNothingWritten(fixture);
    }

    // ---- T3 ----

    @Test
    void t3_slotOneMinuteAgoOrNow_returns400_futureMessage() throws Exception {
        Fixture fixture = createPendingApplication("t3-past");
        String message = "Khung giờ 1: thời điểm phỏng vấn phải ở trong tương lai.";

        assertScheduleError(send(fixture, bodyWithSlots(slot(Instant.now().minus(1, ChronoUnit.MINUTES)))), message);
        assertScheduleError(send(fixture, bodyWithSlots(slot(Instant.now()))), message);
        assertNothingWritten(fixture);
    }

    @Test
    void t3_slotTwoMinutesAhead_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("t3-two-min");

        MvcResult result = send(fixture, bodyWithSlots(slot(Instant.now().plus(2, ChronoUnit.MINUTES))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void t3_secondsAreTruncatedBeforeSaving() throws Exception {
        Fixture fixture = createPendingApplication("t3-truncate");
        Instant tenOClock = inDays(3).truncatedTo(ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);

        MvcResult result = send(fixture, bodyWithSlots(slot(tenOClock.plusSeconds(45).plusMillis(123))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Object invitationId = invitationRows(fixture.applicationId()).get(0).get("id");
        assertThat(instant(slotRows(invitationId).get(0).get("scheduled_at"))).isEqualTo(tenOClock);
        assertThat(body(result).get("slots").get(0).get("scheduledAt").asString()).isEqualTo(tenOClock.toString());
    }

    @Test
    void t3_twoSlotsInSameMinute_returns400_duplicateMessage() throws Exception {
        Fixture fixture = createPendingApplication("t3-dup");
        Instant tenOClock = inDays(3).truncatedTo(ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);

        MvcResult result = send(fixture, bodyWithSlots(slot(tenOClock.plusSeconds(10)), slot(tenOClock.plusSeconds(50))));

        assertScheduleError(result, "Khung giờ 2 trùng thời điểm với khung giờ 1.");
        assertNothingWritten(fixture);
    }

    @Test
    void t3_slotsOneMinuteApart_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("t3-one-min-apart");
        Instant tenOClock = inDays(3).truncatedTo(ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);

        MvcResult result = send(fixture, bodyWithSlots(slot(tenOClock), slot(tenOClock.plus(1, ChronoUnit.MINUTES))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void t3_errorInThirdSlot_reportsSlotThree() throws Exception {
        Fixture fixture = createPendingApplication("t3-third");

        MvcResult result = send(fixture, bodyWithSlots(
                slot(inDays(1)), slot(inDays(2)), slot(Instant.now().minus(1, ChronoUnit.DAYS))));

        assertScheduleError(result, "Khung giờ 3: thời điểm phỏng vấn phải ở trong tương lai.");
        assertNothingWritten(fixture);
    }

    @Test
    void t3_nullSlotElement_returns400_not500() throws Exception {
        Fixture fixture = createPendingApplication("t3-null-element");

        assertScheduleError(send(fixture, bodyWithSlots("null")), "Khung giờ 1: vui lòng chọn thời điểm phỏng vấn.");
        assertScheduleError(
                send(fixture, bodyWithSlots("{\"scheduledAt\":null,\"location\":\"Tang 3\"}")),
                "Khung giờ 1: vui lòng chọn thời điểm phỏng vấn.");
        assertNothingWritten(fixture);
    }

    @Test
    void t3_locationNullMissingEmptyOrBlank_returns400() throws Exception {
        Fixture fixture = createPendingApplication("t3-location-missing");
        String message = "Khung giờ 1: vui lòng nhập địa điểm hoặc link phỏng vấn.";
        Instant at = inDays(3);

        assertScheduleError(send(fixture, bodyWithSlots(slot(at, null))), message);
        assertScheduleError(send(fixture, bodyWithSlots("{\"scheduledAt\":\"" + at + "\"}")), message);
        assertScheduleError(send(fixture, bodyWithSlots(slot(at, ""))), message);
        assertScheduleError(send(fixture, bodyWithSlots(slot(at, "  "))), message);
        assertNothingWritten(fixture);
    }

    @Test
    void t3_locationIsStrippedBeforeSaving() throws Exception {
        Fixture fixture = createPendingApplication("t3-strip");

        MvcResult result = send(fixture, bodyWithSlots(slot(inDays(3), " a ")));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Object invitationId = invitationRows(fixture.applicationId()).get(0).get("id");
        assertThat(slotRows(invitationId).get(0).get("location")).isEqualTo("a");
    }

    @Test
    void t3_locationAt500Chars_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("t3-loc-500");

        assertThat(send(fixture, bodyWithSlots(slot(inDays(3), "A".repeat(500)))).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void t3_locationAt501Chars_returns400() throws Exception {
        Fixture fixture = createPendingApplication("t3-loc-501");

        assertScheduleError(
                send(fixture, bodyWithSlots(slot(inDays(3), "A".repeat(501)))), "Khung giờ 1: địa điểm tối đa 500 ký tự.");
        assertNothingWritten(fixture);
    }

    @Test
    void t3_location500CharsWithSurroundingSpaces_returnsCreated() throws Exception {
        Fixture fixture = createPendingApplication("t3-loc-500-spaces");

        MvcResult result = send(fixture, bodyWithSlots(slot(inDays(3), " " + "A".repeat(500) + " ")));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Object invitationId = invitationRows(fixture.applicationId()).get(0).get("id");
        assertThat(slotRows(invitationId).get(0).get("location")).isEqualTo("A".repeat(500));
    }

    @Test
    void t3_locationWithNewline_returns400() throws Exception {
        Fixture fixture = createPendingApplication("t3-newline");

        assertScheduleError(
                send(fixture, bodyWithSlots(slot(inDays(3), "Tang 3\nPhong 2"))),
                "Khung giờ 1: địa điểm chỉ được nằm trên một dòng.");
        assertScheduleError(
                send(fixture, bodyWithSlots(slot(inDays(3), "Tang 3\rPhong 2"))),
                "Khung giờ 1: địa điểm chỉ được nằm trên một dòng.");
        assertNothingWritten(fixture);
    }

    // L9 - bo khoang trang hai dau TRUOC khi kiem xuong dong: xuong dong o cuoi bi cat, khong bi tu choi.
    @Test
    void t3_locationWithTrailingNewline_isStrippedAndSaved() throws Exception {
        Fixture fixture = createPendingApplication("t3-trailing-newline");

        MvcResult result = send(fixture, bodyWithSlots(slot(inDays(3), "Tầng 3\n")));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Object invitationId = invitationRows(fixture.applicationId()).get(0).get("id");
        assertThat(slotRows(invitationId).get(0).get("location")).isEqualTo("Tầng 3");
    }

    // ---- T6 ----

    @Test
    void t6_resendOneSlot_newInvitationConfirmedImmediately() throws Exception {
        Fixture fixture = createInvitedApplication("t6", slot(inDays(2)), slot(inDays(3)));

        MvcResult result = resend(fixture, bodyWithSlots(slot(inDays(4))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> active = invitationRows(fixture.applicationId()).stream()
                .filter(row -> row.get("superseded_at") == null)
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> slots = slotRows(active.get("id"));
        assertThat(slots).hasSize(1);
        assertThat(active.get("confirmed_slot_id")).isEqualTo(slots.get(0).get("id"));
        assertThat(instant(active.get("confirmed_at"))).isEqualTo(instant(active.get("sent_at")));
        assertThat(body(result).get("active").asBoolean()).isTrue();
    }

    // ---- T7 (phan DB; "khong thong bao" o InterviewInvitationResendIntegrationTest) ----

    private void assertResendNotAllowed(Fixture fixture, int invitationsBefore) throws Exception {
        MvcResult result = resend(fixture, bodyWithSlots(slot(inDays(4))));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asString()).isEqualTo("INVITATION_RESEND_NOT_ALLOWED");
        assertThat(body(result).get("message").asString())
                .isEqualTo("Chỉ gửi lại giấy mời được khi đơn đang ở trạng thái Đã mời phỏng vấn.");
        List<Map<String, Object>> invitations = invitationRows(fixture.applicationId());
        assertThat(invitations).hasSize(invitationsBefore);
        assertThat(invitations).allMatch(row -> row.get("superseded_at") == null);
    }

    @Test
    void t7_resendOnPendingApplication_returns409_nothingWritten() throws Exception {
        Fixture fixture = createPendingApplication("t7-pending");

        assertResendNotAllowed(fixture, 0);
        assertThat(slotCountOfApplication(fixture.applicationId())).isZero();
        assertThat(statusOf(fixture.applicationId())).isEqualTo("PENDING");
    }

    @Test
    void t7_resendOnHiredApplication_returns409_oldInvitationStillActive() throws Exception {
        Fixture fixture = createInvitedApplication("t7-hired", slot(inDays(2)));
        changeStatus(fixture, "HIRED");

        assertResendNotAllowed(fixture, 1);
        assertThat(statusOf(fixture.applicationId())).isEqualTo("HIRED");
    }

    @Test
    void t7_resendOnRejectedApplication_returns409_oldInvitationStillActive() throws Exception {
        Fixture fixture = createInvitedApplication("t7-rejected", slot(inDays(2)));
        changeStatus(fixture, "REJECTED");

        assertResendNotAllowed(fixture, 1);
        assertThat(statusOf(fixture.applicationId())).isEqualTo("REJECTED");
    }

    @Test
    void t7_resendOnWithdrawnApplication_returns409_oldInvitationStillActive() throws Exception {
        Fixture fixture = createInvitedApplication("t7-withdrawn", slot(inDays(2)));
        // Lop @Transactional dung chung persistence context voi MockMvc - cap nhat bang JDBC khong lam moi entity trong
        // cache, nen flush truoc va clear sau cau UPDATE.
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE job_applications SET status = 'WITHDRAWN' WHERE id = ?", UUID.fromString(fixture.applicationId()));
        entityManager.clear();

        assertResendNotAllowed(fixture, 1);
        assertThat(statusOf(fixture.applicationId())).isEqualTo("WITHDRAWN");
    }

    // ---- T8 ----

    @Test
    void t8_resendWithInvalidSlots_returns400_oldInvitationStillActive() throws Exception {
        Fixture fixture = createInvitedApplication("t8", slot(inDays(2)));
        Instant at = inDays(3);

        assertScheduleError(
                resend(fixture, "{\"slots\":[],\"subject\":\"S\",\"content\":\"C\"}"),
                "Vui lòng thêm ít nhất một khung giờ phỏng vấn.");
        assertScheduleError(
                resend(fixture, bodyWithSlots(slot(Instant.now().minus(1, ChronoUnit.DAYS)))),
                "Khung giờ 1: thời điểm phỏng vấn phải ở trong tương lai.");
        assertScheduleError(
                resend(fixture, bodyWithSlots(slot(at), slot(at.plusSeconds(30)))),
                "Khung giờ 2 trùng thời điểm với khung giờ 1.");
        assertScheduleError(
                resend(fixture, bodyWithSlots(slot(at, "   "))),
                "Khung giờ 1: vui lòng nhập địa điểm hoặc link phỏng vấn.");

        List<Map<String, Object>> invitations = invitationRows(fixture.applicationId());
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).get("superseded_at")).isNull();
        assertThat(slotCountOfApplication(fixture.applicationId())).isEqualTo(1);
    }

    // ---- T9 ----

    @Test
    void t9_resendWhenMultiSlotInvitationAlreadyConfirmed_returnsCreated_oldKeepsConfirmation() throws Exception {
        Fixture fixture = createInvitedApplication("t9", slot(inDays(2)), slot(inDays(3)));
        Map<String, Object> old = invitationRows(fixture.applicationId()).get(0);
        Object chosenSlotId = slotRows(old.get("id")).get(1).get("id");
        // Gia lap FR-U10: ung vien da chot khung 2.
        Instant confirmedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        // Lop @Transactional dung chung persistence context voi MockMvc - cap nhat bang JDBC khong lam moi entity trong
        // cache, nen flush truoc va clear sau cau UPDATE.
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE interview_invitations SET confirmed_slot_id = ?, confirmed_at = ? WHERE id = ?",
                chosenSlotId,
                java.sql.Timestamp.from(confirmedAt),
                old.get("id"));
        entityManager.clear();

        MvcResult result = resend(fixture, bodyWithSlots(slot(inDays(5)), slot(inDays(6))));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> oldAfter = invitationRows(fixture.applicationId()).stream()
                .filter(row -> row.get("id").equals(old.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(oldAfter.get("superseded_at")).isNotNull();
        assertThat(oldAfter.get("confirmed_slot_id")).isEqualTo(chosenSlotId);
        assertThat(instant(oldAfter.get("confirmed_at"))).isEqualTo(confirmedAt);
        assertThat(body(result).get("confirmedSlotId").isNull()).isTrue();
    }

    // ---- T11 ----

    @Test
    void t11_listAndResend_byHrOfAnotherCompany_return403() throws Exception {
        Fixture fixture = createInvitedApplication("t11-other", slot(inDays(2)));
        String otherHrToken = registerAndLoginHr("t11-other-hr");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult listResult = list(otherHrToken, fixture.applicationId());
        MvcResult resendResult = postJson(
                "/api/hr/applications/" + fixture.applicationId() + "/interview-invitation/resend",
                otherHrToken,
                bodyWithSlots(slot(inDays(4))));

        assertThat(listResult.getResponse().getStatus()).isEqualTo(403);
        assertThat(resendResult.getResponse().getStatus()).isEqualTo(403);
        assertThat(invitationRows(fixture.applicationId())).hasSize(1);
    }

    @Test
    void t11_listAndResend_applicationDoesNotExist_return404ApplicationNotFound() throws Exception {
        String hrToken = registerAndLoginHr("t11-missing-hr");
        createCompany(hrToken, uniqueName("Cong ty"));
        String missing = UUID.randomUUID().toString();

        MvcResult listResult = list(hrToken, missing);
        MvcResult resendResult = postJson(
                "/api/hr/applications/" + missing + "/interview-invitation/resend", hrToken, bodyWithSlots(slot(inDays(4))));

        assertThat(listResult.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(listResult).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(resendResult.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(resendResult).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    @Test
    void t11_listAndResend_hrWithoutCompany_return404CompanyNotFound_evenForMissingApplication() throws Exception {
        String hrToken = registerAndLoginHr("t11-no-company");
        String missing = UUID.randomUUID().toString();

        MvcResult listResult = list(hrToken, missing);
        MvcResult resendResult = postJson(
                "/api/hr/applications/" + missing + "/interview-invitation/resend", hrToken, bodyWithSlots(slot(inDays(4))));

        assertThat(listResult.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(listResult).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
        assertThat(resendResult.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(resendResult).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
    }

    @Test
    void t11_listAndResend_candidateToken_return403_noToken_return401() throws Exception {
        Fixture fixture = createInvitedApplication("t11-roles", slot(inDays(2)));
        String candidateToken = registerAndLoginCandidate("t11-cand", "Ung Vien Khac");
        String resendPath = "/api/hr/applications/" + fixture.applicationId() + "/interview-invitation/resend";

        assertThat(list(candidateToken, fixture.applicationId()).getResponse().getStatus()).isEqualTo(403);
        assertThat(postJson(resendPath, candidateToken, bodyWithSlots(slot(inDays(4)))).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(list(null, fixture.applicationId()).getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post(resendPath)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyWithSlots(slot(inDays(4)))))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        assertThat(invitationRows(fixture.applicationId())).hasSize(1);
    }

    // ---- T12 ----

    @Test
    void t12_listWithoutInvitation_returnsEmptyListAndMaxSlots() throws Exception {
        Fixture fixture = createPendingApplication("t12-empty");

        MvcResult result = list(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("applicationId").asString()).isEqualTo(fixture.applicationId());
        assertThat(json.get("maxSlots").asInt()).isEqualTo(5);
        assertThat(json.get("invitations").size()).isZero();
    }

    @Test
    void t12_listAfterSendAndTwoResends_activeFirstThenSupersededDescending() throws Exception {
        Fixture fixture = createInvitedApplication("t12-three", slot(inDays(2)));
        String firstId = invitationRows(fixture.applicationId()).get(0).get("id").toString();
        MvcResult second = resend(fixture, bodyWithSlots(slot(inDays(6), "Phong 2"), slot(inDays(4), "Phong 1")));
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        MvcResult third = resend(fixture, bodyWithSlots(slot(inDays(7))));
        assertThat(third.getResponse().getStatus()).isEqualTo(201);

        MvcResult result = list(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode invitations = body(result).get("invitations");
        assertThat(invitations.size()).isEqualTo(3);
        assertThat(invitations.get(0).get("id").asString()).isEqualTo(body(third).get("id").asString());
        assertThat(invitations.get(0).get("active").asBoolean()).isTrue();
        assertThat(invitations.get(0).get("supersededAt").isNull()).isTrue();
        assertThat(invitations.get(1).get("id").asString()).isEqualTo(body(second).get("id").asString());
        assertThat(invitations.get(1).get("active").asBoolean()).isFalse();
        assertThat(invitations.get(2).get("id").asString()).isEqualTo(firstId);
        assertThat(invitations.get(2).get("active").asBoolean()).isFalse();
        assertThat(Instant.parse(invitations.get(1).get("supersededAt").asString()))
                .isAfter(Instant.parse(invitations.get(2).get("supersededAt").asString()));
        // Khung giu dung thu tu gui (khung muon gui truoc).
        JsonNode secondSlots = invitations.get(1).get("slots");
        assertThat(secondSlots.get(0).get("displayOrder").asInt()).isEqualTo(1);
        assertThat(secondSlots.get(0).get("location").asString()).isEqualTo("Phong 2");
        assertThat(secondSlots.get(1).get("displayOrder").asInt()).isEqualTo(2);
        assertThat(secondSlots.get(1).get("location").asString()).isEqualTo("Phong 1");

        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String forbidden : List.of("verdict", "label", "isQualified", "passed", "recommendation")) {
            assertThat(raw).as(forbidden).doesNotContain("\"" + forbidden + "\"");
        }
    }

    @Test
    void preview_returnsMaxSlots() throws Exception {
        Fixture fixture = createPendingApplication("preview-max-slots");

        MvcResult result = preview(fixture.hrToken(), fixture.applicationId());

        assertThat(body(result).get("maxSlots").asInt()).isEqualTo(5);
    }

    // ---- T17 ----

    @Test
    void t17_noPutPatchDeleteRoutesForInvitations() throws Exception {
        Fixture fixture = createInvitedApplication("t17", slot(inDays(2)));
        String base = "/api/hr/applications/" + fixture.applicationId();
        List<String> paths = List.of(
                base + "/interview-invitation",
                base + "/interview-invitations",
                base + "/interview-invitation/resend",
                base + "/interview-invitations/" + invitationRows(fixture.applicationId()).get(0).get("id"));

        for (String path : paths) {
            for (var request : List.of(put(path), patch(path), delete(path))) {
                int status = mockMvc.perform(request.header("Authorization", "Bearer " + fixture.hrToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyWithSlots(slot(inDays(4)))))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                assertThat(status).as(path).isIn(404, 405);
            }
        }
        assertThat(invitationRows(fixture.applicationId())).hasSize(1);
        assertThat(slotCountOfApplication(fixture.applicationId())).isEqualTo(1);
    }
}

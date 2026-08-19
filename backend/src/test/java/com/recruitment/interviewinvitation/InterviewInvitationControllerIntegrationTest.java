package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
                  "job": {"title":"%s","description":"Mo ta cong viec"},
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
        // scheduledAt = null -> BO HAN field nay khoi JSON (khong phai gui chuoi "null") de kiem
        // dung case "thieu ngay gio" qua @NotNull, khong lan sang loi parse JSON khac.
        String scheduledAtField = scheduledAt == null ? "" : "\"scheduledAt\":\"" + scheduledAt + "\",";
        String body =
                """
                {%s"location":"Van phong cong ty","subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
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
}

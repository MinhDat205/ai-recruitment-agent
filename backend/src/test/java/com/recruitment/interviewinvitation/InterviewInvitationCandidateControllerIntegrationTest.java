package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

// FR-U03 (E3, candidate-view-invitation) - ung vien doc lai giay moi phong van cua chinh minh. Mau
// helper y het InterviewInvitationControllerIntegrationTest (khong tach dung chung, tien le du an),
// them candidateToken vao Fixture vi test nay can goi API bang token ung vien.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterviewInvitationCandidateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private String createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        return name;
    }

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

    private void sendInvitation(String hrToken, String applicationId) throws Exception {
        String body =
                """
                {"scheduledAt":"%s","location":"Van phong cong ty","subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                """
                        .formatted(Instant.now().plus(7, ChronoUnit.DAYS));
        mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void changeStatus(String hrToken, String applicationId, String newStatus) throws Exception {
        mockMvc
                .perform(patch("/api/hr/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(newStatus)))
                .andExpect(status().isOk());
    }

    private MvcResult getInvitation(String candidateToken, String applicationId) throws Exception {
        return mockMvc
                .perform(get("/api/candidates/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + candidateToken))
                .andReturn();
    }

    private record Fixture(String hrToken, String candidateToken, String jobId, String applicationId) {
    }

    private Fixture createPendingApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand", "Nguyen Van Ung Vien");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, candidateToken, jobId, applicationId);
    }

    private Fixture createInterviewInvitedApplication(String prefix) throws Exception {
        Fixture fixture = createPendingApplication(prefix);
        sendInvitation(fixture.hrToken(), fixture.applicationId());
        return fixture;
    }

    @Test
    void get_ownApplication_returnsInvitationWithFiveFieldsOnly() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-own");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat(extractJsonField(json, "location")).isEqualTo("Van phong cong ty");
        assertThat(extractJsonField(json, "subject")).isEqualTo("Thu moi phong van");
        assertThat(extractJsonField(json, "renderedContent")).isEqualTo("Xin chao, moi ban tham gia phong van.");
        assertThat(json).contains("\"scheduledAt\"");
        assertThat(json).contains("\"sentAt\"");
        // Dung 5 field - khong lan bat ky du lieu scoring nao (totalScore/rank/...) hay id noi bo
        // (id/applicationId/sentBy cua InterviewInvitationResponse phia HR).
        assertThat(json).doesNotContain("totalScore").doesNotContain("rank").doesNotContain("criterionScore");
        assertThat(json).doesNotContain("\"id\"").doesNotContain("\"applicationId\"").doesNotContain("\"sentBy\"");
    }

    @Test
    void get_applicationOfAnotherCandidate_returns403() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-other-candidate");
        String otherCandidateToken = registerAndLoginCandidate("get-other-cand", "Nguoi Khac");

        MvcResult result = getInvitation(otherCandidateToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void get_applicationDoesNotExist_returns404() throws Exception {
        Fixture fixture = createPendingApplication("get-not-exist");

        MvcResult result = getInvitation(fixture.candidateToken(), UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void get_applicationPendingWithoutInvitation_returns404() throws Exception {
        Fixture fixture = createPendingApplication("get-no-invitation");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void get_applicationHiredAfterInterview_stillReturnsInvitation() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-hired");
        changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("Xin chao, moi ban tham gia phong van.");
    }

    @Test
    void get_applicationRejectedAfterInterview_stillReturnsInvitation() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-rejected");
        changeStatus(fixture.hrToken(), fixture.applicationId(), "REJECTED");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("Xin chao, moi ban tham gia phong van.");
    }
}

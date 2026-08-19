package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
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

// Mau y het ApplicationOwnerControllerIntegrationTest (cung mot bo helper dang ky/dang nhap/tao du
// lieu, khong tach thanh tien ich dung chung - dung tien le cua toan bo cac test file trong du an
// nay). @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationStatusControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationStatusHistoryRepository statusHistoryRepository;

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
                  "job": {"title":"%s","description":"Mo ta cong viec"},
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

    private MvcResult changeStatus(String token, String applicationId, String newStatus) throws Exception {
        String body = """
                {"status":"%s"}
                """.formatted(newStatus);
        return mockMvc
                .perform(patch("/api/hr/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    // FR-H07 Dot 2: duong DUY NHAT de dua don sang INTERVIEW_INVITED - PATCH .../status truc tiep
    // sang trang thai nay bi chan (xem ApplicationStatusController). scheduledAt mac dinh 7 ngay
    // sau "hien tai" de chac chan luon o tuong lai.
    private MvcResult sendInterviewInvitation(String token, String applicationId) throws Exception {
        return sendInterviewInvitation(token, applicationId, Instant.now().plus(7, ChronoUnit.DAYS));
    }

    private MvcResult sendInterviewInvitation(String token, String applicationId, Instant scheduledAt) throws Exception {
        String body =
                """
                {"scheduledAt":"%s","location":"Van phong cong ty","subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                """
                        .formatted(scheduledAt.toString());
        return mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    // Dung mot job/candidate/application PENDING moi cho moi test - tra ve (hrToken, applicationId).
    private record Fixture(String hrToken, String applicationId) {
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
        return new Fixture(hrToken, applicationId);
    }

    // ---- Case duong ----

    // FR-H07 Dot 2: INTERVIEW_INVITED bat buoc kem lich hen (SRS: "Da moi phong van (co lich hen)")
    // - PATCH .../status truc tiep sang trang thai nay khong con hop le, phai qua
    // POST .../interview-invitation (xem InterviewInvitationControllerIntegrationTest cho case
    // duong tuong ung).
    @Test
    void changeStatus_pendingToInterviewInvitedViaPatch_returns400() throws Exception {
        Fixture fixture = createPendingApplication("pending-to-invited");

        MvcResult result = changeStatus(fixture.hrToken(), fixture.applicationId(), "INTERVIEW_INVITED");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        List<ApplicationStatusHistory> history = statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(
                UUID.fromString(fixture.applicationId()));
        assertThat(history).hasSize(1);
    }

    @Test
    void changeStatus_pendingToRejected_returnsOk() throws Exception {
        Fixture fixture = createPendingApplication("pending-to-rejected");

        MvcResult result = changeStatus(fixture.hrToken(), fixture.applicationId(), "REJECTED");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(extractJsonField(result.getResponse().getContentAsString(), "status")).isEqualTo("REJECTED");
    }

    @Test
    void changeStatus_interviewInvitedToHired_returnsOk() throws Exception {
        Fixture fixture = createPendingApplication("invited-to-hired");
        sendInterviewInvitation(fixture.hrToken(), fixture.applicationId());

        MvcResult result = changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(extractJsonField(result.getResponse().getContentAsString(), "status")).isEqualTo("HIRED");

        List<ApplicationStatusHistory> history = statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(
                UUID.fromString(fixture.applicationId()));
        // NULL->PENDING, PENDING->INTERVIEW_INVITED, INTERVIEW_INVITED->HIRED.
        assertThat(history).hasSize(3);
    }

    @Test
    void changeStatus_interviewInvitedToRejected_returnsOk() throws Exception {
        Fixture fixture = createPendingApplication("invited-to-rejected");
        sendInterviewInvitation(fixture.hrToken(), fixture.applicationId());

        MvcResult result = changeStatus(fixture.hrToken(), fixture.applicationId(), "REJECTED");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(extractJsonField(result.getResponse().getContentAsString(), "status")).isEqualTo("REJECTED");
    }

    // ---- Case am ----

    @Test
    void changeStatus_pendingToHired_returns400() throws Exception {
        Fixture fixture = createPendingApplication("pending-to-hired");

        MvcResult result = changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        List<ApplicationStatusHistory> history = statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(
                UUID.fromString(fixture.applicationId()));
        assertThat(history).hasSize(1);
    }

    @Test
    void changeStatus_hiredToAnything_returns400() throws Exception {
        Fixture fixture = createPendingApplication("hired-terminal");
        sendInterviewInvitation(fixture.hrToken(), fixture.applicationId());
        changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");

        MvcResult result = changeStatus(fixture.hrToken(), fixture.applicationId(), "REJECTED");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    // REJECTED la trang thai cuoi - khong quay lai duoc INTERVIEW_INVITED, ke ca qua duong that
    // (POST .../interview-invitation) chu khong chi qua PATCH bi chan boi guard rieng.
    @Test
    void changeStatus_rejectedToInterviewInvited_returns400() throws Exception {
        Fixture fixture = createPendingApplication("rejected-terminal");
        changeStatus(fixture.hrToken(), fixture.applicationId(), "REJECTED");

        MvcResult result = sendInterviewInvitation(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        List<ApplicationStatusHistory> history = statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(
                UUID.fromString(fixture.applicationId()));
        // Chi 1 dong PENDING->REJECTED them vao NULL->PENDING - lan gui loi moi bi chan boi ban
        // chuyen tiep (REJECTED khong phai nguon hop le), khong duoc ghi.
        assertThat(history).hasSize(2);
    }

    @Test
    void changeStatus_byHrOfAnotherCompany_returns403() throws Exception {
        Fixture fixture = createPendingApplication("wrong-company");
        String otherHrToken = registerAndLoginHr("other-hr");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        // Dung REJECTED (khong phai INTERVIEW_INVITED) - INTERVIEW_INVITED bi chan boi guard o
        // controller VO DIEU KIEN (xem changeStatus_pendingToInterviewInvitedViaPatch_returns400),
        // se che mat duong 403 dang can kiem o day.
        MvcResult result = changeStatus(otherHrToken, fixture.applicationId(), "REJECTED");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void changeStatus_applicationNotFound_returns404() throws Exception {
        String hrToken = registerAndLoginHr("not-found-hr");
        createCompany(hrToken, uniqueName("Cong ty Not Found"));

        MvcResult result = changeStatus(hrToken, UUID.randomUUID().toString(), "REJECTED");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}

package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
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

// KHONG @Transactional: voi @SpringBootTest + MockMvc, test va service chay chung mot transaction
// neu bao ngoai bang @Transactional - INSERT (du da saveAndFlush) va viec transaction bi danh dau
// rollback-only ngay khi vi pham constraint se lam hong assertion sau do (vd count() o test job
// DRAFT se nem UnexpectedRollbackException), va co the che giau dung hanh vi 409 can kiem chung.
// Cach ly giua cac test dua vao du lieu ngau nhien (email, ten cong ty, tieu de job gan
// UUID.randomUUID()), dung cach uniqueEmail() cua CompanyOwnerIntegrationTest dang lam.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    private static final byte[] PDF_CONTENT = "%PDF-1.4 noi dung CV gia lap".getBytes();

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueTitle(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
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
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien"}
                """
                        .formatted(email);
        mockMvc
                .perform(
                        post("/api/auth/register/candidate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private void createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(
                        post("/api/hr/companies")
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
                .perform(
                        post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void fillRubricToFullWeight(String token, String jobId) throws Exception {
        String body = """
                {"name":"Kinh nghiem","weight":100}
                """;
        mockMvc
                .perform(
                        post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated());
    }

    private void openJob(String token, String jobId) throws Exception {
        mockMvc
                .perform(
                        patch("/api/hr/jobs/" + jobId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"status":"OPEN"}
                                        """))
                .andExpect(status().isOk());
    }

    private String createOpenJob(String hrToken, String titlePrefix) throws Exception {
        String jobId = createJob(hrToken, uniqueTitle(titlePrefix));
        fillRubricToFullWeight(hrToken, jobId);
        openJob(hrToken, jobId);
        return jobId;
    }

    private String uploadResume(String candidateToken) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF_CONTENT);
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private MvcResult apply(String candidateToken, String jobId, String resumeId, boolean aiConsent) throws Exception {
        String body =
                """
                {"jobId":"%s","resumeId":"%s","aiConsent":%s,"coverLetter":"Toi rat quan tam vi tri nay"}
                """
                        .formatted(jobId, resumeId, aiConsent);
        return mockMvc
                .perform(
                        post("/api/candidates/applications")
                                .header("Authorization", "Bearer " + candidateToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    @Test
    void apply_happyPath_returns201() throws Exception {
        String hrToken = registerAndLoginHr("hr-apply-ok");
        createCompany(hrToken, "Cong ty Apply Ok " + UUID.randomUUID());
        String jobId = createOpenJob(hrToken, "Job Apply Ok");

        String candidateToken = registerAndLoginCandidate("candidate-apply-ok");
        String resumeId = uploadResume(candidateToken);

        MvcResult result = apply(candidateToken, jobId, resumeId, true);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"PENDING\"");
    }

    @Test
    void apply_secondTimeSameCycle_returnsConflict() throws Exception {
        String hrToken = registerAndLoginHr("hr-apply-dup");
        createCompany(hrToken, "Cong ty Apply Dup " + UUID.randomUUID());
        String jobId = createOpenJob(hrToken, "Job Apply Dup");

        String candidateToken = registerAndLoginCandidate("candidate-apply-dup");
        String resumeId = uploadResume(candidateToken);

        MvcResult first = apply(candidateToken, jobId, resumeId, true);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = apply(candidateToken, jobId, resumeId, true);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("APPLICATION_DUPLICATE");
    }

    @Test
    void apply_withAiConsentFalse_returnsBadRequest() throws Exception {
        String hrToken = registerAndLoginHr("hr-apply-noconsent");
        createCompany(hrToken, "Cong ty Apply NoConsent " + UUID.randomUUID());
        String jobId = createOpenJob(hrToken, "Job Apply NoConsent");

        String candidateToken = registerAndLoginCandidate("candidate-apply-noconsent");
        String resumeId = uploadResume(candidateToken);

        MvcResult result = apply(candidateToken, jobId, resumeId, false);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void apply_withResumeOfAnotherCandidate_returnsNotFound() throws Exception {
        String hrToken = registerAndLoginHr("hr-apply-wrongresume");
        createCompany(hrToken, "Cong ty Apply WrongResume " + UUID.randomUUID());
        String jobId = createOpenJob(hrToken, "Job Apply WrongResume");

        String candidateAToken = registerAndLoginCandidate("candidate-apply-a");
        String candidateBToken = registerAndLoginCandidate("candidate-apply-b");
        String resumeOfB = uploadResume(candidateBToken);

        MvcResult result = apply(candidateAToken, jobId, resumeOfB, true);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("RESUME_NOT_FOUND");
    }

    @Test
    void apply_toDraftJob_doesNotCreateApplication() throws Exception {
        String hrToken = registerAndLoginHr("hr-apply-draft");
        createCompany(hrToken, "Cong ty Apply Draft " + UUID.randomUUID());
        // Khong goi openJob() - job con DRAFT.
        String jobId = createJob(hrToken, uniqueTitle("Job Apply Draft"));

        String candidateToken = registerAndLoginCandidate("candidate-apply-draft");
        String resumeId = uploadResume(candidateToken);

        long countBefore = jobApplicationRepository.count();

        MvcResult result = apply(candidateToken, jobId, resumeId, true);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("JOB_NOT_FOUND");
        assertThat(jobApplicationRepository.count()).isEqualTo(countBefore);
    }
}

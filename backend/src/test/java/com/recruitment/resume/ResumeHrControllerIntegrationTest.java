package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

// Mau y het ApplicationOwnerControllerIntegrationTest (cung bo helper dang ky/dang nhap/tao du
// lieu, khong tach thanh tien ich dung chung - dung tien le cua toan bo cac test file trong du an).
// @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResumeHrControllerIntegrationTest {

    // %PDF - dung magic bytes that de vuot qua kiem tra dinh dang, khop ResumeIntegrationTest.
    private static final byte[] VALID_PDF_CONTENT = "%PDF-1.4 noi dung CV that gia lap".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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

    private String uploadResume(String candidateToken, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv-ung-vien.pdf", "application/pdf", content);
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

    private MvcResult download(String hrToken, String applicationId) throws Exception {
        return mockMvc
                .perform(get("/api/hr/applications/" + applicationId + "/resume/download")
                        .header("Authorization", "Bearer " + hrToken))
                .andReturn();
    }

    // Dung khi can mot JobApplication tro toi mot Resume co fileUrl KHONG tung duoc StorageService
    // ghi that (khac cach upload() qua HTTP luon ghi file that xuong dia) - dung de dung tinh huong
    // "file khong con tren dia" (Diem 5, yeu cau bat buoc) ma khong dong toi he thong file that.
    private String createApplicationWithMissingFile(String companyOwnerToken, String jobId) throws Exception {
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();

        User candidate = new User();
        candidate.setEmail(uniqueEmail("cand-missing-file"));
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName(uniqueName("Ung Vien File Mat"));
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        // Key KHONG TUNG duoc store() ghi - LocalStorageService.load() se tra Optional.empty().
        resume.setFileUrl("resumes/khong-ton-tai-" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv-da-mat.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        return application.getId().toString();
    }

    // ---- Case duong ----

    @Test
    void download_ownerHr_succeedsWithExactFileContent() throws Exception {
        String hrToken = registerAndLoginHr("hr-owner");
        createCompany(hrToken, uniqueName("Cong ty Chu"));
        String jobId = createJob(hrToken, uniqueName("Job Chu"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-owner", "Ung Vien Chinh Chu");
        String resumeId = uploadResume(candidateToken, VALID_PDF_CONTENT);
        String applicationId = apply(candidateToken, jobId, resumeId);

        MvcResult result = download(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(VALID_PDF_CONTENT);
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
    }

    // ---- Case am ----

    @Test
    void download_byHrOfAnotherCompany_returns403() throws Exception {
        String ownerToken = registerAndLoginHr("hr-owner2");
        createCompany(ownerToken, uniqueName("Cong ty Chu 2"));
        String jobId = createJob(ownerToken, uniqueName("Job Chu 2"));
        addCriterion(ownerToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(ownerToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-owner2", "Ung Vien Chu 2");
        String resumeId = uploadResume(candidateToken, VALID_PDF_CONTENT);
        String applicationId = apply(candidateToken, jobId, resumeId);

        String otherHrToken = registerAndLoginHr("hr-other");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult result = download(otherHrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void download_calledByCandidate_returns403() throws Exception {
        String candidateToken = registerAndLoginCandidate("cand-caller", "Ung Vien Goi Sai Quyen");

        MvcResult result = download(candidateToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void download_applicationNotFound_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-notfound");
        createCompany(hrToken, uniqueName("Cong ty Khong Co Don"));

        MvcResult result = download(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("APPLICATION_NOT_FOUND");
    }

    // File da mat khoi dia (vd bi xoa ngoai y du dong resumes van con) - phai tra loi co kiem soat
    // (404, ma loi chuan hoa), KHONG duoc de UncheckedIOException/NoSuchFileException roi thang ve
    // 500 mac dinh khong co body ro rang. "Resume bi xoa mem" (mot kha nang khac nguoi dung nhac
    // toi) KHONG dung duoc lam test - bang resumes KHONG co cot deleted_at, khong co tinh nang xoa
    // (mem hay cung) nao cho resume trong toan bo codebase hien tai (da doc lai V1__init_schema.sql
    // va ResumeService/ResumeRepository de xac nhan, khong doan) - tinh huong do khong the dung
    // duoc qua bat ky duong code that nao, chi co "file mat tren dia" la dung duoc va co nghia.
    @Test
    void download_fileMissingFromDisk_returns404NotInternalServerError() throws Exception {
        String hrToken = registerAndLoginHr("hr-missing-file");
        createCompany(hrToken, uniqueName("Cong ty File Mat"));
        String jobId = createJob(hrToken, uniqueName("Job File Mat"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);
        String applicationId = createApplicationWithMissingFile(hrToken, jobId);

        MvcResult result = download(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("RESUME_NOT_FOUND");
    }
}

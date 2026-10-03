package com.recruitment.jobrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobEmbeddingStateService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeEmbeddingStateService;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.CandidateProfileRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
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

// FR-U15 - thay the JobRecommendationCandidateControllerIntegrationTest cu (da xoa o dot 4, xem
// bang doi chieu muc 7 REQUIREMENT.md). Khong con bang job_recommendations de seed thang - nhanh
// CV duoc dung bang embedding THAT qua JobEmbeddingStateService/ResumeEmbeddingStateService, giong
// khuon JobRecommendationCandidateServiceTest.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobRecommendationCandidateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobEmbeddingStateService jobEmbeddingStateService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ResumeEmbeddingStateService resumeEmbeddingStateService;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Vo hieu hoa job OPEN sot lai tu lop test KHAC (ResumeEmbeddingOrchestratorTest,
    // JobEmbeddingOrchestratorTest, JobEmbeddingPipelineIntegrationTest... - cac file nay KHONG
    // @Transactional, ghi THAT vao Postgres Testcontainers dung chung, khong tu don). Truoc day
    // (JobRecommendationCacheServiceTest cu, da xoa o dot 4) chiu duoc nhiem nho dung assertion
    // khoan dung (contains/doesNotContain, khong containsExactly) vi bang cache cu KHONG LIMIT.
    // Service moi ap RECOMMENDATION_LIMIT = 6 NGAY TRONG CAU QUERY (top-N) - job rac nay du de
    // lap day het LIMIT truoc khi toi job test thuc su can kiem, day han job do ra ngoai ket qua,
    // assertion khoan dung khong con cuu duoc nua (xem bao cao Dot 9 nua dau). Cach ly kieu MOI:
    // soft-delete toan bo job OPEN con lai NGAY TRUOC khi tao fixture cua tung test. Lop nay KHONG
    // @Transactional cap class (goi qua HTTP/MockMvc, transaction do controller tu mo/dong) nen
    // lenh nay COMMIT THAT, khong tu rollback - an toan vi khong co test nao (file nay hay file
    // khac) dua vao viec job OPEN rac phai con sau khi lop nay chay.
    @BeforeEach
    void disableLeftoverOpenJobsFromOtherTestClasses() {
        jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE status = 'OPEN' AND deleted_at IS NULL");
    }

    private static float[] vectorWithSimilarity(double s) {
        float[] v = new float[1536];
        v[0] = (float) s;
        v[1] = (float) Math.sqrt(1 - s * s);
        return v;
    }

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

    private record CandidateSession(String token, UUID candidateId) {
    }

    private CandidateSession registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(
                        post("/api/auth/register/candidate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
        UUID candidateId = userRepository.findByEmail(email).orElseThrow().getId();
        return new CandidateSession(token, candidateId);
    }

    private record HrSession(String token, UUID hrId) {
    }

    private HrSession registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung","phone":"0900000000"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = extractJsonField(login.getResponse().getContentAsString(), "accessToken");
        UUID hrId = userRepository.findByEmail(email).orElseThrow().getId();
        return new HrSession(token, hrId);
    }

    private UUID createOpenJob(String title, UUID companyId, UUID createdBy) {
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(createdBy);
        job.setTitle(title);
        job.setDescription("Mo ta cong viec test");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return jobRepository.save(job).getId();
    }

    private UUID createOpenJobWithEmbedding(String title, UUID companyId, UUID createdBy, double similarity) {
        UUID jobId = createOpenJob(title, companyId, createdBy);
        jobEmbeddingStateService.save(jobId, vectorWithSimilarity(similarity), "text-embedding-3-small");
        return jobId;
    }

    private ResumeParsedPayload samplePayload() {
        return new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van Test", "test@example.com", null, null, null),
                List.of(), List.of(), List.of("Java"), List.of(), List.of(), null, null, null);
    }

    private UUID createResumeWithEmbedding(UUID candidateId, double similarity) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        UUID resumeId = resumeRepository.save(resume).getId();

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("Noi dung CV test");
        data.setData(samplePayload());
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        UUID parsedDataId = resumeParsedDataRepository.saveAndFlush(data).getId();
        resumeEmbeddingStateService.save(parsedDataId, vectorWithSimilarity(similarity));
        return resumeId;
    }

    // resume_id cua job_applications co FK toi resumes(id) - can mot Resume hop le.
    private UUID createPlainResume(UUID candidateId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(false);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    // ===================================================================================
    // Bang doi chieu test cu -> test moi (muc 7 REQUIREMENT.md, 3/7 dong thuoc file nay)
    // ===================================================================================

    @Test
    void getRecommendations_noData_returnsEmptyItemsWithNoDataStatus() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-nodata");

        MvcResult result = mockMvc
                .perform(get("/api/candidates/job-recommendations")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"status\":\"NO_DATA\"");
        assertThat(body).contains("\"items\":[]");
    }

    @Test
    void getRecommendations_cvBranch_returnsOrderedBySimilarityDesc() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-ordered");
        createResumeWithEmbedding(session.candidateId(), 1.0);

        User hr = userRepository.save(hrUser());
        Company company = companyRepository.save(companyFor(hr.getId()));
        UUID jobHighId = createOpenJobWithEmbedding("Java Dev Diem Cao", company.getId(), hr.getId(), 0.8);
        UUID jobLowId = createOpenJobWithEmbedding("Java Dev Diem Thap", company.getId(), hr.getId(), 0.45);

        MvcResult result = mockMvc
                .perform(get("/api/candidates/job-recommendations")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int highIndex = body.indexOf(jobHighId.toString());
        int lowIndex = body.indexOf(jobLowId.toString());
        assertThat(highIndex).isGreaterThanOrEqualTo(0);
        assertThat(lowIndex).isGreaterThan(highIndex);
    }

    // Test am quan trong nhat (ke thua tu test cu) - JSON tra ve KHONG duoc chua similarityScore
    // hay matchScore o bat ky dang nao.
    @Test
    void getRecommendations_responseDoesNotContainSimilarityScoreField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-noscore");
        createResumeWithEmbedding(session.candidateId(), 1.0);
        User hr = userRepository.save(hrUser());
        Company company = companyRepository.save(companyFor(hr.getId()));
        createOpenJobWithEmbedding("Java Dev Kiem Khong Lo Diem", company.getId(), hr.getId(), 0.8);

        MvcResult result = mockMvc
                .perform(get("/api/candidates/job-recommendations")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("similarityScore");
        assertThat(body).doesNotContain("matchScore");
    }

    private User hrUser() {
        User hr = new User();
        hr.setEmail(uniqueEmail("hr"));
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        return hr;
    }

    private Company companyFor(UUID ownerId) {
        Company company = new Company();
        company.setOwnerId(ownerId);
        company.setName("Cong ty Test " + UUID.randomUUID());
        return company;
    }

    // ===================================================================================
    // Test moi can them (muc 7 REQUIREMENT.md, #13 va #14) - #11/#12 nam o JobPublicIntegrationTest.
    // ===================================================================================

    // #13 - Chong lo cho HR (R-P1/R-P3): endpoint HR khong chua khoa matchedConditions; HR goi
    // endpoint gop y cua candidate -> 403.
    @Test
    void getRecommendations_hrEndpointsDoNotLeakMatchedConditions_andHrCannotCallCandidateEndpoint()
            throws Exception {
        CandidateSession candidateSession = registerAndLoginCandidate("cand-noleak");
        UUID resumeId = createPlainResume(candidateSession.candidateId());

        HrSession hrSession = registerAndLoginHr("hr-noleak");
        Company company = companyRepository.save(companyFor(hrSession.hrId()));
        UUID jobId = createOpenJob("Job Khong Lo Du Lieu", company.getId(), hrSession.hrId());

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidateSession.candidateId());
        application.setResumeId(resumeId);
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        jobApplicationRepository.save(application);

        MvcResult ownerListResult = mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + hrSession.token()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(ownerListResult.getResponse().getContentAsString()).doesNotContain("matchedConditions");

        MvcResult searchResult = mockMvc
                .perform(get("/api/hr/candidates").header("Authorization", "Bearer " + hrSession.token()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(searchResult.getResponse().getContentAsString()).doesNotContain("matchedConditions");

        mockMvc
                .perform(get("/api/candidates/job-recommendations")
                        .header("Authorization", "Bearer " + hrSession.token()))
                .andExpect(status().isForbidden());
    }

    // #14 - Khong ghi DB (R-S4): goi API gop y 2 lan lien tiep, dem job_applications/candidate_profiles
    // truoc va sau khong doi.
    @Test
    void getRecommendations_calledTwiceOverHttp_doesNotChangeApplicationOrProfileCounts() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-nowrite");
        createResumeWithEmbedding(session.candidateId(), 1.0);
        User hr = userRepository.save(hrUser());
        Company company = companyRepository.save(companyFor(hr.getId()));
        createOpenJobWithEmbedding("Job Kiem Khong Ghi DB", company.getId(), hr.getId(), 0.6);

        long applicationCountBefore = jobApplicationRepository.count();
        long profileCountBefore = candidateProfileRepository.count();

        mockMvc
                .perform(get("/api/candidates/job-recommendations")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk());
        mockMvc
                .perform(get("/api/candidates/job-recommendations")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk());

        assertThat(jobApplicationRepository.count()).isEqualTo(applicationCountBefore);
        assertThat(candidateProfileRepository.count()).isEqualTo(profileCountBefore);
    }
}

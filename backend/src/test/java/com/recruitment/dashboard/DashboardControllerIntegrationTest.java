package com.recruitment.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusRecorder;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
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

// Mau y het ApplicationOwnerControllerIntegrationTest (cung mot bo helper dang ky/dang nhap/tao
// du lieu, khong tach thanh tien ich dung chung - dung tien le cua toan bo cac test file trong du
// an nay). @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationStatusRecorder applicationStatusRecorder;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

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

    private void deleteJob(String token, String jobId) throws Exception {
        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
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

    // Gia lap ket qua D1 (khong goi LLM that trong test) - mau ApplicationOwnerControllerIntegrationTest.
    private void markResumeParsedDone(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.save(resume);

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("Ung vien co 3 nam kinh nghiem Java trong CV");
        data.setData(new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java"),
                List.of(),
                List.of()));
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.save(data);
    }

    // Ghi thang xuong DB mot luot cham DONE - khong chay lai pipeline D2/D3 that (khong LLM trong
    // test), mau createDoneScoringRunDirectly cua ApplicationOwnerControllerIntegrationTest.
    private void createDoneScoringRun(UUID applicationId, BigDecimal totalScore) {
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Test",
                List.of(new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), "Tieu chi", null, new BigDecimal("100.00"), 5, null)));

        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(totalScore);
        scoringRunRepository.saveAndFlush(run);

        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(run.getId());
        criterionScore.setCriterionNameSnapshot("Tieu chi");
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(new BigDecimal("4.00"));
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    // Ghi thang xuong DB mot buoc chuyen trang thai - tuong duong ApplicationStatusService.changeStatus
    // nhung khong di qua HTTP/ownership (khong can thiet cho muc dich cua test nay: DashboardService
    // chi DOC du lieu da co san, khong kiem lai logic chuyen trang thai - da co
    // ApplicationStatusServiceTest rieng cho dieu do). changedBy=null (he thong) - khong anh huong
    // ket qua truy van (FunnelCountsView/JobPerformanceView khong doc cot nay).
    private void transitionStatus(UUID applicationId, ApplicationStatus from, ApplicationStatus to) {
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        application.setStatus(to);
        jobApplicationRepository.save(application);
        applicationStatusRecorder.record(applicationId, from, to, null, null);
    }

    private MvcResult getStats(String token) throws Exception {
        return mockMvc
                .perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    // ---- Case duong ----

    @Test
    void getStats_singleApplication_countsInStatusBreakdownAndFunnelTotal() throws Exception {
        String hrToken = registerAndLoginHr("hr-single");
        createCompany(hrToken, uniqueName("Cong ty Single"));
        String jobId = createJob(hrToken, uniqueName("Job Single"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-single", "Ung Vien Single");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        apply(candidateToken, jobId, resumeId);

        MvcResult result = getStats(hrToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"totalApplications\":1");
        assertThat(body).contains("\"PENDING\":1");
        assertThat(body).contains("\"INTERVIEW_INVITED\":0");
        assertThat(body).contains("\"HIRED\":0");
        assertThat(body).contains("\"REJECTED\":0");
        assertThat(body).contains("\"WITHDRAWN\":0");
        assertThat(body).contains("\"appliedTotal\":1");
        assertThat(body).contains("\"everInvited\":0");
        assertThat(body).contains("\"everHired\":0");
    }

    // Tieu chi "Xong khi" cua PHASES.md F3 + canh bao "AI hay lam sai": don WITHDRAWN van phai
    // duoc dem dung. Don nay TUNG duoc moi phong van (INTERVIEW_INVITED) truoc khi rut - phai
    // tinh vao everInvited DU status hien tai la WITHDRAWN.
    @Test
    void getStats_invitedThenWithdrawn_countsEverInvitedDespiteCurrentStatusWithdrawn() throws Exception {
        String hrToken = registerAndLoginHr("hr-withdrawn");
        createCompany(hrToken, uniqueName("Cong ty Withdrawn"));
        String jobId = createJob(hrToken, uniqueName("Job Withdrawn"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-withdrawn", "Ung Vien Withdrawn");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        transitionStatus(UUID.fromString(applicationId), ApplicationStatus.PENDING, ApplicationStatus.INTERVIEW_INVITED);
        transitionStatus(
                UUID.fromString(applicationId), ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.WITHDRAWN);

        MvcResult result = getStats(hrToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"totalApplications\":1");
        assertThat(body).contains("\"WITHDRAWN\":1");
        assertThat(body).contains("\"PENDING\":0");
        assertThat(body).contains("\"INTERVIEW_INVITED\":0");
        assertThat(body).contains("\"appliedTotal\":1");
        assertThat(body).contains("\"everInvited\":1");
        assertThat(body).contains("\"everHired\":0");
    }

    @Test
    void getStats_rejectedWithoutInvite_notCountedAsEverInvited() throws Exception {
        String hrToken = registerAndLoginHr("hr-rejected");
        createCompany(hrToken, uniqueName("Cong ty Rejected"));
        String jobId = createJob(hrToken, uniqueName("Job Rejected"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-rejected", "Ung Vien Rejected");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        transitionStatus(UUID.fromString(applicationId), ApplicationStatus.PENDING, ApplicationStatus.REJECTED);

        MvcResult result = getStats(hrToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"REJECTED\":1");
        assertThat(body).contains("\"everInvited\":0");
        assertThat(body).contains("\"everHired\":0");
    }

    // Bo sung theo yeu cau duyet plan (muc 5): don di TRON duong PENDING -> INTERVIEW_INVITED ->
    // HIRED phai duoc dem vao CA everInvited LAN everHired, khong phai chi everHired.
    @Test
    void getStats_invitedThenHired_countsBothEverInvitedAndEverHired() throws Exception {
        String hrToken = registerAndLoginHr("hr-hired");
        createCompany(hrToken, uniqueName("Cong ty Hired"));
        String jobId = createJob(hrToken, uniqueName("Job Hired"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-hired", "Ung Vien Hired");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        transitionStatus(UUID.fromString(applicationId), ApplicationStatus.PENDING, ApplicationStatus.INTERVIEW_INVITED);
        transitionStatus(UUID.fromString(applicationId), ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.HIRED);

        MvcResult result = getStats(hrToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"HIRED\":1");
        assertThat(body).contains("\"everInvited\":1");
        assertThat(body).contains("\"everHired\":1");
    }

    @Test
    void getStats_jobPerformance_jobWithNoApplications_showsZeroCounts() throws Exception {
        String hrToken = registerAndLoginHr("hr-empty-job");
        createCompany(hrToken, uniqueName("Cong ty Empty Job"));
        String jobTitle = uniqueName("Job Empty");
        createJob(hrToken, jobTitle);

        MvcResult result = getStats(hrToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"title\":\"" + jobTitle + "\"");
        assertThat(body).contains("\"totalApplications\":0,\"scoredApplications\":0,\"averageScore\":null");
        assertThat(body).contains("\"everInvitedCount\":0,\"everHiredCount\":0");
    }

    // scoredApplications chi dem luot DONE (khong dem tong so don), averageScore la ROUND(AVG,3)
    // chi tren cac luot DONE - da xac minh thuc nghiem AVG(NUMERIC(6,3)) mo rong scale, ROUND
    // dua ve dung 70.000 (xem comment tren cau SQL o JobRepository).
    @Test
    void getStats_jobPerformance_onlyDoneRunsCountedAsScored_averageRoundedToThreeDecimals() throws Exception {
        String hrToken = registerAndLoginHr("hr-avg");
        createCompany(hrToken, uniqueName("Cong ty Avg"));
        String jobTitle = uniqueName("Job Avg");
        String jobId = createJob(hrToken, jobTitle);
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidate1Token = registerAndLoginCandidate("cand-avg-1", "Ung Vien Avg Mot");
        String resume1Id = uploadResume(candidate1Token);
        markResumeParsedDone(UUID.fromString(resume1Id));
        String application1Id = apply(candidate1Token, jobId, resume1Id);
        createDoneScoringRun(UUID.fromString(application1Id), new BigDecimal("80.000"));

        String candidate2Token = registerAndLoginCandidate("cand-avg-2", "Ung Vien Avg Hai");
        String resume2Id = uploadResume(candidate2Token);
        markResumeParsedDone(UUID.fromString(resume2Id));
        String application2Id = apply(candidate2Token, jobId, resume2Id);
        createDoneScoringRun(UUID.fromString(application2Id), new BigDecimal("60.000"));

        String candidate3Token = registerAndLoginCandidate("cand-avg-3", "Ung Vien Avg Ba");
        String resume3Id = uploadResume(candidate3Token);
        markResumeParsedDone(UUID.fromString(resume3Id));
        apply(candidate3Token, jobId, resume3Id); // chua co luot cham nao

        MvcResult result = getStats(hrToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"title\":\"" + jobTitle + "\"");
        assertThat(body).contains("\"totalApplications\":3,\"scoredApplications\":2,\"averageScore\":70.000");
    }

    @Test
    void getStats_deletedJob_excludedFromDashboard() throws Exception {
        String hrToken = registerAndLoginHr("hr-deleted-job");
        createCompany(hrToken, uniqueName("Cong ty Deleted Job"));
        String jobTitle = uniqueName("Job Se Bi Xoa");
        String jobId = createJob(hrToken, jobTitle);
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-deleted-job", "Ung Vien Deleted Job");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        apply(candidateToken, jobId, resumeId);

        deleteJob(hrToken, jobId);

        MvcResult result = getStats(hrToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"totalApplications\":0");
        assertThat(body).contains("\"appliedTotal\":0");
        assertThat(body).doesNotContain("\"title\":\"" + jobTitle + "\"");
    }

    @Test
    void getStats_scopedToOwnCompany_excludesOtherHrData() throws Exception {
        String hrAToken = registerAndLoginHr("hr-scope-a");
        createCompany(hrAToken, uniqueName("Cong ty A"));
        String jobAId = createJob(hrAToken, uniqueName("Job A"));
        addCriterion(hrAToken, jobAId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrAToken, jobAId);
        String candidateAToken = registerAndLoginCandidate("cand-scope-a", "Ung Vien A");
        String resumeAId = uploadResume(candidateAToken);
        markResumeParsedDone(UUID.fromString(resumeAId));
        apply(candidateAToken, jobAId, resumeAId);

        String hrBToken = registerAndLoginHr("hr-scope-b");
        createCompany(hrBToken, uniqueName("Cong ty B"));
        String jobBTitle = uniqueName("Job B");
        String jobBId = createJob(hrBToken, jobBTitle);
        addCriterion(hrBToken, jobBId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrBToken, jobBId);
        String candidateBToken = registerAndLoginCandidate("cand-scope-b", "Ung Vien B");
        String resumeBId = uploadResume(candidateBToken);
        markResumeParsedDone(UUID.fromString(resumeBId));
        apply(candidateBToken, jobBId, resumeBId);

        MvcResult result = getStats(hrAToken);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"totalApplications\":1");
        assertThat(body).doesNotContain("\"title\":\"" + jobBTitle + "\"");
    }

    // ---- Case am ----

    @Test
    void getStats_calledByCandidate_returns403() throws Exception {
        String candidateToken = registerAndLoginCandidate("cand-forbidden", "Ung Vien Sai Quyen");

        MvcResult result = getStats(candidateToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void getStats_hrWithoutCompany_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-no-company");

        MvcResult result = getStats(hrToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("COMPANY_NOT_FOUND");
    }
}

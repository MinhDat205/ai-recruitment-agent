package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

// Mau y het DashboardControllerIntegrationTest/ApplicationOwnerControllerIntegrationTest - dung
// tien le cua toan bo cac test file trong du an nay (khong tach helper dung chung).
// @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationSearchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

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

    // Lay TAT CA gia tri cua field theo dung thu tu xuat hien trong JSON (dung de doc danh sach
    // "id" trong mang "items" theo dung thu tu tra ve - khong dung cho field co the null vi regex
    // yeu cau gia tri dang chuoi trong dau nhay kep).
    private List<String> extractJsonFieldAll(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
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

    // Job da mo san criteria + OPEN, dung cho cac test khong quan tam noi dung rubric.
    private String createOpenJob(String hrToken, String title) throws Exception {
        String jobId = createJob(hrToken, title);
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);
        return jobId;
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

    // Ghi thang xuong DB mot luot cham DONE voi DUNG MOT tieu chi - khong chay lai pipeline that
    // (khong LLM trong test), mau createDoneScoringRunDirectly cua
    // ApplicationOwnerControllerIntegrationTest, mo rong them tham so criterionScore de test duoc
    // bien cua minCriterionScore.
    private void createDoneScoringRun(
            UUID applicationId, BigDecimal totalScore, String criterionName, BigDecimal criterionScore) {
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Test",
                List.of(new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), criterionName, null, new BigDecimal("100.00"), 5, null)));

        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(totalScore);
        scoringRunRepository.saveAndFlush(run);

        CriterionScore criterionScoreEntity = new CriterionScore();
        criterionScoreEntity.setScoringRunId(run.getId());
        criterionScoreEntity.setCriterionNameSnapshot(criterionName);
        criterionScoreEntity.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScoreEntity.setMaxScoreSnapshot(5);
        criterionScoreEntity.setScore(criterionScore);
        criterionScoreEntity.setReasoning("Ly do gia lap trong test");
        criterionScoreEntity.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScoreEntity);
    }

    private String createApplication(String candidatePrefix, String candidateName, String jobId) throws Exception {
        String candidateToken = registerAndLoginCandidate(candidatePrefix, candidateName);
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        return apply(candidateToken, jobId, resumeId);
    }

    private MvcResult searchCandidates(String token, String queryString) throws Exception {
        String path = queryString.isEmpty() ? "/api/hr/candidates" : "/api/hr/candidates?" + queryString;
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult listCriteria(String token) throws Exception {
        return mockMvc
                .perform(get("/api/hr/candidates/criteria").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    // ---- Case duong ----

    @Test
    void searchCandidates_filterByJobId_onlyReturnsApplicationsOfThatJob() throws Exception {
        String hrToken = registerAndLoginHr("hr-filter-job");
        createCompany(hrToken, uniqueName("Cong ty Filter Job"));
        String jobAId = createOpenJob(hrToken, uniqueName("Job A"));
        String jobBId = createOpenJob(hrToken, uniqueName("Job B"));

        createApplication("cand-fj-a", "Ung Vien Job A", jobAId);
        createApplication("cand-fj-b", "Ung Vien Job B", jobBId);

        MvcResult result = searchCandidates(hrToken, "jobId=" + jobAId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"candidateName\":\"Ung Vien Job A\"");
        assertThat(body).doesNotContain("\"candidateName\":\"Ung Vien Job B\"");
        assertThat(body).contains("\"totalElements\":1");
    }

    @Test
    void searchCandidates_filterByWithdrawnStatus_stillReturnsWithdrawnApplication() throws Exception {
        String hrToken = registerAndLoginHr("hr-filter-status");
        createCompany(hrToken, uniqueName("Cong ty Filter Status"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Status"));
        String applicationId = createApplication("cand-fs", "Ung Vien Rut Don", jobId);

        // Ghi thang trang thai WITHDRAWN xuong DB - khong can chay lai flow rut don that (FR-U06),
        // ApplicationSearchService chi DOC job_applications.status hien tai cho bo loc nay (khac
        // DashboardService dung application_status_history cho pheu chuyen doi).
        JobApplication application =
                jobApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        application.setStatus(ApplicationStatus.WITHDRAWN);
        jobApplicationRepository.save(application);

        MvcResult result = searchCandidates(hrToken, "status=WITHDRAWN");

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"candidateName\":\"Ung Vien Rut Don\"");
        assertThat(body).contains("\"status\":\"WITHDRAWN\"");
    }

    @Test
    void searchCandidates_minTotalScoreBoundary_excludesBelowIncludesAtAndAbove() throws Exception {
        String hrToken = registerAndLoginHr("hr-min-total");
        createCompany(hrToken, uniqueName("Cong ty Min Total"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Min Total"));

        String app4 = createApplication("cand-mt-4", "Ung Vien Diem Bon", jobId);
        createDoneScoringRun(UUID.fromString(app4), new BigDecimal("4.000"), "Tieu chi", new BigDecimal("4.00"));
        String app5 = createApplication("cand-mt-5", "Ung Vien Diem Nam", jobId);
        createDoneScoringRun(UUID.fromString(app5), new BigDecimal("5.000"), "Tieu chi", new BigDecimal("4.00"));
        String app6 = createApplication("cand-mt-6", "Ung Vien Diem Sau", jobId);
        createDoneScoringRun(UUID.fromString(app6), new BigDecimal("6.000"), "Tieu chi", new BigDecimal("4.00"));

        MvcResult result = searchCandidates(hrToken, "minTotalScore=5");

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"candidateName\":\"Ung Vien Diem Bon\"");
        assertThat(body).contains("\"candidateName\":\"Ung Vien Diem Nam\"");
        assertThat(body).contains("\"candidateName\":\"Ung Vien Diem Sau\"");
        assertThat(body).contains("\"totalElements\":2");
    }

    @Test
    void searchCandidates_minCriterionScoreBoundary_excludesBelowIncludesAtAndAbove() throws Exception {
        String hrToken = registerAndLoginHr("hr-min-crit");
        createCompany(hrToken, uniqueName("Cong ty Min Criterion"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Min Criterion"));

        String app3 = createApplication("cand-mc-3", "Ung Vien Docker Ba", jobId);
        createDoneScoringRun(UUID.fromString(app3), new BigDecimal("50.000"), "Docker", new BigDecimal("3.00"));
        String app4 = createApplication("cand-mc-4", "Ung Vien Docker Bon", jobId);
        createDoneScoringRun(UUID.fromString(app4), new BigDecimal("50.000"), "Docker", new BigDecimal("4.00"));
        String app5 = createApplication("cand-mc-5", "Ung Vien Docker Nam", jobId);
        createDoneScoringRun(UUID.fromString(app5), new BigDecimal("50.000"), "Docker", new BigDecimal("5.00"));

        MvcResult result = searchCandidates(hrToken, "criterionName=Docker&minCriterionScore=4");

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"candidateName\":\"Ung Vien Docker Ba\"");
        assertThat(body).contains("\"candidateName\":\"Ung Vien Docker Bon\"");
        assertThat(body).contains("\"candidateName\":\"Ung Vien Docker Nam\"");
        assertThat(body).contains("\"totalElements\":2");
    }

    @Test
    void searchCandidates_sizeBoundary_fiftyKeptFiftyOneClampedZeroDefaults() throws Exception {
        String hrToken = registerAndLoginHr("hr-size");
        createCompany(hrToken, uniqueName("Cong ty Size"));

        assertThat(searchCandidates(hrToken, "size=50").getResponse().getContentAsString())
                .contains("\"size\":50");
        assertThat(searchCandidates(hrToken, "size=51").getResponse().getContentAsString())
                .contains("\"size\":50");
        assertThat(searchCandidates(hrToken, "size=0").getResponse().getContentAsString())
                .contains("\"size\":10");
        assertThat(searchCandidates(hrToken, "").getResponse().getContentAsString())
                .contains("\"size\":10");
    }

    // Tieu chi duyet Dot 3: 12 don CUNG total_score va CUNG applied_at (transaction-scoped now())
    // trong 1 transaction (@Transactional cap class). Phan trang phai ON DINH: hop cua trang 1+2
    // (size=5) khong trung, khong thieu so voi 10 dong dau cua MOT trang lon (size=50).
    @Test
    void searchCandidates_pagination_stableAcrossPagesWhenTotalScoreAndAppliedAtTie() throws Exception {
        String hrToken = registerAndLoginHr("hr-tie");
        createCompany(hrToken, uniqueName("Cong ty Tie"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Tie"));

        for (int i = 0; i < 12; i++) {
            String applicationId = createApplication("cand-tie-" + i, "Ung Vien Tie " + i, jobId);
            createDoneScoringRun(
                    UUID.fromString(applicationId), new BigDecimal("50.000"), "Tieu chi", new BigDecimal("4.00"));
        }

        String bigPageBody =
                searchCandidates(hrToken, "size=50").getResponse().getContentAsString();
        List<String> baselineIds = extractJsonFieldAll(bigPageBody, "id");
        assertThat(baselineIds).hasSize(12);

        String page0Body =
                searchCandidates(hrToken, "page=0&size=5").getResponse().getContentAsString();
        String page1Body =
                searchCandidates(hrToken, "page=1&size=5").getResponse().getContentAsString();
        List<String> page0Ids = extractJsonFieldAll(page0Body, "id");
        List<String> page1Ids = extractJsonFieldAll(page1Body, "id");

        assertThat(page0Ids).hasSize(5);
        assertThat(page1Ids).hasSize(5);

        List<String> combined = new ArrayList<>(page0Ids);
        combined.addAll(page1Ids);
        Set<String> combinedUnique = new LinkedHashSet<>(combined);
        assertThat(combinedUnique).hasSize(10); // khong trung id giua hai trang
        assertThat(combined).isEqualTo(baselineIds.subList(0, 10)); // dung thu tu, khong thieu
    }

    @Test
    void searchCandidates_criteriaDropdown_returnsDistinctCriterionNamesInCompanyScope() throws Exception {
        String hrToken = registerAndLoginHr("hr-criteria");
        createCompany(hrToken, uniqueName("Cong ty Criteria"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Criteria"));

        String app1 = createApplication("cand-crit-1", "Ung Vien Criteria Mot", jobId);
        createDoneScoringRun(UUID.fromString(app1), new BigDecimal("50.000"), "Docker", new BigDecimal("4.00"));
        String app2 = createApplication("cand-crit-2", "Ung Vien Criteria Hai", jobId);
        createDoneScoringRun(
                UUID.fromString(app2), new BigDecimal("60.000"), "Kinh nghiem Java", new BigDecimal("3.00"));

        MvcResult result = listCriteria(hrToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"Docker\"");
        assertThat(body).contains("\"Kinh nghiem Java\"");
    }

    // ---- Case am ----

    @Test
    void searchCandidates_criterionNameWithoutMinCriterionScore_returns400() throws Exception {
        String hrToken = registerAndLoginHr("hr-invalid-crit");
        createCompany(hrToken, uniqueName("Cong ty Invalid Criterion"));

        MvcResult result = searchCandidates(hrToken, "criterionName=Docker");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_CANDIDATE_SEARCH_FILTER");
    }

    @Test
    void searchCandidates_minCriterionScoreWithoutCriterionName_returns400() throws Exception {
        String hrToken = registerAndLoginHr("hr-invalid-crit-2");
        createCompany(hrToken, uniqueName("Cong ty Invalid Criterion 2"));

        MvcResult result = searchCandidates(hrToken, "minCriterionScore=4");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_CANDIDATE_SEARCH_FILTER");
    }

    @Test
    void searchCandidates_minTotalScoreGreaterThanMaxTotalScore_returns400() throws Exception {
        String hrToken = registerAndLoginHr("hr-invalid-range");
        createCompany(hrToken, uniqueName("Cong ty Invalid Range"));

        MvcResult result = searchCandidates(hrToken, "minTotalScore=8&maxTotalScore=5");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_CANDIDATE_SEARCH_FILTER");
    }

    @Test
    void searchCandidates_scopedToOwnCompany_excludesOtherHrData() throws Exception {
        String hrAToken = registerAndLoginHr("hr-scope-a");
        createCompany(hrAToken, uniqueName("Cong ty A"));
        String jobAId = createOpenJob(hrAToken, uniqueName("Job A"));
        createApplication("cand-scope-a", "Ung Vien A", jobAId);

        String hrBToken = registerAndLoginHr("hr-scope-b");
        createCompany(hrBToken, uniqueName("Cong ty B"));
        String jobBId = createOpenJob(hrBToken, uniqueName("Job B"));
        createApplication("cand-scope-b", "Ung Vien B", jobBId);

        MvcResult result = searchCandidates(hrAToken, "");

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"candidateName\":\"Ung Vien A\"");
        assertThat(body).doesNotContain("\"candidateName\":\"Ung Vien B\"");
        assertThat(body).contains("\"totalElements\":1");
    }

    @Test
    void searchCandidates_deletedJob_excludedFromResults() throws Exception {
        String hrToken = registerAndLoginHr("hr-deleted-job");
        createCompany(hrToken, uniqueName("Cong ty Deleted Job"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Se Bi Xoa"));
        createApplication("cand-deleted-job", "Ung Vien Deleted Job", jobId);

        deleteJob(hrToken, jobId);

        MvcResult result = searchCandidates(hrToken, "");

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"candidateName\":\"Ung Vien Deleted Job\"");
        assertThat(body).contains("\"totalElements\":0");
    }

    @Test
    void searchCandidates_calledByCandidate_returns403() throws Exception {
        String candidateToken = registerAndLoginCandidate("cand-forbidden", "Ung Vien Sai Quyen");

        MvcResult result = searchCandidates(candidateToken, "");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void searchCandidates_hrWithoutCompany_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-no-company");

        MvcResult result = searchCandidates(hrToken, "");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("COMPANY_NOT_FOUND");
    }
}

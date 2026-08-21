package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
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

// Mau y het ApplicationSearchControllerIntegrationTest/DashboardControllerIntegrationTest - dung
// tien le cua toan bo cac test file trong du an nay (khong tach helper dung chung).
// @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScoringRunAuditControllerIntegrationTest {

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
    private ScoreExplanationRepository scoreExplanationRepository;

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

    private String createApplication(String candidatePrefix, String candidateName, String jobId) throws Exception {
        String candidateToken = registerAndLoginCandidate(candidatePrefix, candidateName);
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        return apply(candidateToken, jobId, resumeId);
    }

    // Ghi thang mot luot cham xuong DB - khong chay lai pipeline that (khong LLM trong test), mau
    // createDoneScoringRunDirectly cua cac test file truoc, mo rong them model/promptVersion/
    // tokenUsage vi Dot 4 hien thi truc tiep cac truong nay.
    private UUID createScoringRun(
            UUID applicationId, ScoringRunStatus status, BigDecimal totalScore, String model, String promptVersion) {
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Test",
                List.of(new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), "Tieu chi", null, new BigDecimal("100.00"), 5, null)));
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(status);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(totalScore);
        run.setModel(model);
        run.setPromptVersion(promptVersion);
        run.setTokenUsage(1200);
        if (status == ScoringRunStatus.FAILED) {
            run.setErrorMessage("LLM_TIMEOUT: Qua thoi gian cho phan hoi tu AI");
        }
        scoringRunRepository.saveAndFlush(run);
        return run.getId();
    }

    private void createCriterionScore(UUID scoringRunId) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(scoringRunId);
        criterionScore.setCriterionNameSnapshot("Tieu chi");
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(new BigDecimal("4.00"));
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    private void createExplanation(UUID scoringRunId, String model, String promptVersion) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(scoringRunId);
        explanation.setSummary("Tom tat gia lap trong test");
        explanation.setStrengths(List.of(new ExplanationPoint("Tieu chi", "Diem manh gia lap")));
        explanation.setWeaknesses(List.of());
        explanation.setMetCriteria(List.of("Tieu chi"));
        explanation.setMissingCriteria(List.of());
        explanation.setModel(model);
        explanation.setPromptVersion(promptVersion);
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    private MvcResult listAudit(String token, String applicationId) throws Exception {
        return mockMvc
                .perform(get("/api/hr/candidates/" + applicationId + "/audit/scoring-runs")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    // ---- Case duong ----

    // Tieu chi duyet Dot 4: don co 2 luot (1 FAILED, 1 DONE), moi nhat truoc, luot FAILED co
    // criterionScores rong (khong co tieu chi nao duoc cham), luot DONE co day du. Kiem luon khong
    // co field ten phan quyet (CLAUDE.md muc 7).
    @Test
    void listAudit_applicationWithFailedAndDoneRuns_returnsBothNewestFirstWithCorrectShape() throws Exception {
        String hrToken = registerAndLoginHr("hr-audit-shape");
        createCompany(hrToken, uniqueName("Cong ty Audit Shape"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Audit Shape"));
        String applicationId = createApplication("cand-audit-shape", "Ung Vien Audit Shape", jobId);

        UUID failedRunId = createScoringRun(
                UUID.fromString(applicationId), ScoringRunStatus.FAILED, null, "claude-sonnet-4-6", "criterion-score-v1");
        UUID doneRunId = createScoringRun(
                UUID.fromString(applicationId),
                ScoringRunStatus.DONE,
                new BigDecimal("75.500"),
                "claude-sonnet-4-6",
                "criterion-score-v1");
        createCriterionScore(doneRunId);

        MvcResult result = listAudit(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();

        // Moi nhat truoc: doneRunId tao SAU failedRunId trong CUNG mot test (khong dam bao
        // createdAt phan biet vi transaction-scoped now() - nhung ScoringRunAuditService sort lai
        // bang Java voi id lam khoa cuoi, xem test rieng ve tie-break ben duoi cho truong hop
        // trung created_at that su).
        assertThat(body).contains("\"scoringRunId\":\"" + doneRunId + "\"");
        assertThat(body).contains("\"scoringRunId\":\"" + failedRunId + "\"");
        assertThat(body).contains("\"status\":\"FAILED\"");
        assertThat(body).contains("\"status\":\"DONE\"");
        assertThat(body).contains("\"totalScore\":75.500");
        assertThat(body).contains("\"model\":\"claude-sonnet-4-6\"");
        assertThat(body).contains("\"promptVersion\":\"criterion-score-v1\"");
        assertThat(body).contains("\"criterionNameSnapshot\":\"Tieu chi\"");
        assertThat(body)
                .doesNotContainIgnoringCase("verdict")
                .doesNotContainIgnoringCase("\"label\"")
                .doesNotContainIgnoringCase("isQualified")
                .doesNotContainIgnoringCase("\"passed\"")
                .doesNotContainIgnoringCase("recommendation");
    }

    @Test
    void listAudit_doneRunWithoutExplanation_explanationIsNull() throws Exception {
        String hrToken = registerAndLoginHr("hr-audit-no-explain");
        createCompany(hrToken, uniqueName("Cong ty Audit No Explain"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Audit No Explain"));
        String applicationId = createApplication("cand-audit-no-explain", "Ung Vien No Explain", jobId);
        UUID runId = createScoringRun(
                UUID.fromString(applicationId),
                ScoringRunStatus.DONE,
                new BigDecimal("50.000"),
                "claude-sonnet-4-6",
                "criterion-score-v1");
        createCriterionScore(runId);

        MvcResult result = listAudit(hrToken, applicationId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"scoringRunId\":\"" + runId + "\"");
        assertThat(body).contains("\"explanation\":null");
    }

    @Test
    void listAudit_doneRunWithExplanation_explanationHasModelAndPromptVersion() throws Exception {
        String hrToken = registerAndLoginHr("hr-audit-explain");
        createCompany(hrToken, uniqueName("Cong ty Audit Explain"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Audit Explain"));
        String applicationId = createApplication("cand-audit-explain", "Ung Vien Explain", jobId);
        UUID runId = createScoringRun(
                UUID.fromString(applicationId),
                ScoringRunStatus.DONE,
                new BigDecimal("60.000"),
                "claude-sonnet-4-6",
                "criterion-score-v1");
        createCriterionScore(runId);
        createExplanation(runId, "claude-sonnet-4-6", "score-explanation-v1");

        MvcResult result = listAudit(hrToken, applicationId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"promptVersion\":\"score-explanation-v1\"");
        // Chi lo trinh (model/promptVersion/generatedAt), KHONG hien noi dung bao cao.
        assertThat(body).doesNotContain("Tom tat gia lap trong test");
        assertThat(body).doesNotContain("strengths");
        assertThat(body).doesNotContain("weaknesses");
    }

    // Sort lai bang Java (Comparator.comparing(createdAt).thenComparing(id).reversed()) phai on
    // dinh khi hai luot TRUNG created_at (cung transaction, now() transaction-scoped) - id lon hon
    // (theo UUID.compareTo cua Java) phai dung TRUOC.
    @Test
    void listAudit_tiedCreatedAt_ordersByIdDescendingAsTiebreak() throws Exception {
        String hrToken = registerAndLoginHr("hr-audit-tie");
        createCompany(hrToken, uniqueName("Cong ty Audit Tie"));
        String jobId = createOpenJob(hrToken, uniqueName("Job Audit Tie"));
        String applicationId = createApplication("cand-audit-tie", "Ung Vien Audit Tie", jobId);

        UUID runA = createScoringRun(
                UUID.fromString(applicationId), ScoringRunStatus.FAILED, null, "claude-sonnet-4-6", "criterion-score-v1");
        UUID runB = createScoringRun(
                UUID.fromString(applicationId), ScoringRunStatus.FAILED, null, "claude-sonnet-4-6", "criterion-score-v1");
        UUID expectedFirst = runA.compareTo(runB) > 0 ? runA : runB;
        UUID expectedSecond = expectedFirst.equals(runA) ? runB : runA;

        MvcResult result = listAudit(hrToken, applicationId);

        String body = result.getResponse().getContentAsString();
        int indexFirst = body.indexOf("\"scoringRunId\":\"" + expectedFirst + "\"");
        int indexSecond = body.indexOf("\"scoringRunId\":\"" + expectedSecond + "\"");
        assertThat(indexFirst).isGreaterThanOrEqualTo(0);
        assertThat(indexSecond).isGreaterThan(indexFirst);
    }

    // ---- Case am ----

    @Test
    void listAudit_applicationOfAnotherCompany_returns403() throws Exception {
        String hrOwnerToken = registerAndLoginHr("hr-audit-owner");
        createCompany(hrOwnerToken, uniqueName("Cong ty Chu Audit"));
        String jobId = createOpenJob(hrOwnerToken, uniqueName("Job Chu Audit"));
        String applicationId = createApplication("cand-audit-owner", "Ung Vien Chu Audit", jobId);

        String hrOtherToken = registerAndLoginHr("hr-audit-other");
        createCompany(hrOtherToken, uniqueName("Cong ty Khac Audit"));

        MvcResult result = listAudit(hrOtherToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listAudit_applicationNotFound_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-audit-notfound");
        createCompany(hrToken, uniqueName("Cong ty Audit NotFound"));

        MvcResult result = listAudit(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("APPLICATION_NOT_FOUND");
    }

    // Xac nhan requireOwnCompany chay TRUOC (thu tu da sua o Dot 4): HR chua co cong ty phai nhan
    // dung 404 COMPANY_NOT_FOUND, khong phai APPLICATION_NOT_FOUND (du applicationId truyen vao la
    // ngau nhien, khong ton tai that).
    @Test
    void listAudit_hrWithoutCompany_returns404CompanyNotFound() throws Exception {
        String hrToken = registerAndLoginHr("hr-audit-no-company");

        MvcResult result = listAudit(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("COMPANY_NOT_FOUND");
    }

    @Test
    void listAudit_calledByCandidate_returns403() throws Exception {
        String candidateToken = registerAndLoginCandidate("cand-audit-forbidden", "Ung Vien Sai Quyen");

        MvcResult result = listAudit(candidateToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}

package com.recruitment.jobapplication;

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
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoreExplanation;
import com.recruitment.scoring.ScoreExplanationRepository;
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

// Mau y het ScoringRunHrControllerIntegrationTest (cung mot bo helper dang ky/dang nhap/tao du
// lieu, khong tach thanh tien ich dung chung - dung tien le cua toan bo cac test file trong du an
// nay). @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationOwnerControllerIntegrationTest {

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

    // Gia lap ket qua D1 (khong goi LLM that trong test).
    private void markResumeParsedDone(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.save(resume);

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("Ung vien co 3 nam kinh nghiem Java va Docker trong CV");
        data.setData(new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java", "Docker"),
                List.of(),
                List.of()));
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.save(data);
    }

    // Ghi thang xuong DB mot lot cham DONE voi DUNG MOT tieu chi, dung cho test shape/sort o Dot 4
    // - KHONG chay lai pipeline D2/D3 that (khong LLM trong test). totalScore duoc GHI THANG (khong
    // tinh qua ScoreAggregator) vi cac test dung ham nay chi kiem hinh dang JSON/thu tu sap xep,
    // khong kiem lai phep cong (da co ScoreAggregatorTest/AggregationOrchestratorTest rieng cho
    // dieu do) - weight/score cua tieu chi duy nhat chi can hop le, khong can khop voi totalScore
    // truyen vao.
    private UUID createDoneScoringRunDirectly(UUID applicationId, String criterionName, BigDecimal totalScore) {
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

        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(run.getId());
        criterionScore.setCriterionNameSnapshot(criterionName);
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(new BigDecimal("4.00"));
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);

        return run.getId();
    }

    // Ghi thang bao cao giai thich (FR-H06, Dot 4) xuong DB - khong chay lai pipeline that (khong
    // LLM trong test), cung tinh than voi createDoneScoringRunDirectly.
    private void createExplanationDirectly(UUID runId, String criterionName) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(runId);
        explanation.setSummary("Tom tat gia lap trong test");
        explanation.setStrengths(List.of(new ExplanationPoint(criterionName, "Diem manh gia lap")));
        explanation.setWeaknesses(List.of());
        explanation.setMetCriteria(List.of(criterionName));
        explanation.setMissingCriteria(List.of());
        explanation.setModel("claude-sonnet-4-6");
        explanation.setPromptVersion("score-explanation-v1");
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    private MvcResult createScoringRun(String token, String applicationId) throws Exception {
        return mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/scoring-runs")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult listApplications(String token, String jobId) throws Exception {
        return mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/applications").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult listApplicationsSorted(String token, String jobId, String sort) throws Exception {
        return mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/applications")
                        .param("sort", sort)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    // ---- Case duong ----

    // Doi ten tu ...WithoutTotalScoreOrRank (D2): D4 (Dot 4, FR-H05) THEM totalScore/rank/
    // criterionScores vao chinh response nay - don CHUA cham phai co ca ba field, gia tri null/[],
    // khong con "khong co field" nhu truoc.
    @Test
    void listApplications_ownerHr_unscoredApplication_returnsNullTotalScoreAndRankWithEmptyCriterionScores()
            throws Exception {
        String hrToken = registerAndLoginHr("hr-basic");
        createCompany(hrToken, uniqueName("Cong ty Basic"));
        String jobId = createJob(hrToken, uniqueName("Job Basic"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-basic", "Nguyen Van Ung Vien");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        apply(candidateToken, jobId, resumeId);

        MvcResult result = listApplications(hrToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"candidateName\":\"Nguyen Van Ung Vien\"");
        assertThat(body).contains("\"resumeParseStatus\":\"DONE\"");
        assertThat(body).contains("\"latestScoringRunId\":null");
        assertThat(body).contains("\"totalScore\":null");
        assertThat(body).contains("\"rank\":null");
        assertThat(body).contains("\"criterionScores\":[]");
        assertThat(body).contains("\"explanationStatus\":null");
        assertThat(body).contains("\"explanation\":null");
    }

    // Shape cua response (Diem 2/4, Dot 4): don da co diem that (khong chi don rong) - JSON PHAI
    // chua evidence tung tieu chi (day la muc tieu chinh cua D4), chua co bao cao (explanation=null,
    // explanationStatus=PENDING vi chua tung thu lan nao), va KHONG duoc chua bat ky field ten phan
    // quyet nao (CLAUDE.md muc 7). Ghi thang xuong DB qua repository - khong chay lai toan bo
    // pipeline D2/D3/D4 that (khong LLM trong test), mau ScoringRunStateServiceTest/
    // AggregationOrchestratorTest.
    @Test
    void listApplications_scoredApplication_responseHasEvidenceButNoVerdictLikeFields() throws Exception {
        String hrToken = registerAndLoginHr("hr-shape");
        createCompany(hrToken, uniqueName("Cong ty Shape"));
        String jobId = createJob(hrToken, uniqueName("Job Shape"));
        addCriterion(hrToken, jobId, """
                {"name":"Kinh nghiem Java","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-shape", "Le Van Co Diem");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        createDoneScoringRunDirectly(UUID.fromString(applicationId), "Kinh nghiem Java", new BigDecimal("80.000"));

        MvcResult result = listApplications(hrToken, jobId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"totalScore\":80.000");
        assertThat(body).contains("\"rank\":1");
        assertThat(body).contains("\"criterionNameSnapshot\":\"Kinh nghiem Java\"");
        assertThat(body).contains("\"reasoning\":");
        assertThat(body).contains("\"evidence\":[{\"quote\":\"doan trich gia lap\",\"section\":\"experience\"}]");
        assertThat(body).contains("\"explanation\":null");
        assertThat(body).contains("\"explanationStatus\":\"PENDING\"");
        assertThat(body)
                .doesNotContainIgnoringCase("verdict")
                .doesNotContainIgnoringCase("\"label\"")
                .doesNotContainIgnoringCase("isQualified")
                .doesNotContainIgnoringCase("\"passed\"")
                .doesNotContainIgnoringCase("recommendation");
    }

    // Shape cua bao cao giai thich (FR-H06, Dot 4) qua tang HTTP that - JSON phai chua du 5 phan
    // (summary/strengths/weaknesses/metCriteria/missingCriteria), explanationStatus null khi da co
    // bao cao, va van khong duoc chua field ten phan quyet nao.
    @Test
    void listApplications_scoredApplicationWithExplanation_responseIncludesExplanationFields() throws Exception {
        String hrToken = registerAndLoginHr("hr-explain");
        createCompany(hrToken, uniqueName("Cong ty Explain"));
        String jobId = createJob(hrToken, uniqueName("Job Explain"));
        addCriterion(hrToken, jobId, """
                {"name":"Kinh nghiem Java","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-explain", "Pham Van Co Bao Cao");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        UUID runId = createDoneScoringRunDirectly(UUID.fromString(applicationId), "Kinh nghiem Java", new BigDecimal("80.000"));
        createExplanationDirectly(runId, "Kinh nghiem Java");

        MvcResult result = listApplications(hrToken, jobId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"summary\":\"Tom tat gia lap trong test\"");
        assertThat(body).contains("\"strengths\":[{\"criterionName\":\"Kinh nghiem Java\",\"point\":\"Diem manh gia lap\"}]");
        assertThat(body).contains("\"weaknesses\":[]");
        assertThat(body).contains("\"metCriteria\":[\"Kinh nghiem Java\"]");
        assertThat(body).contains("\"missingCriteria\":[]");
        assertThat(body).contains("\"explanationStatus\":null");
        assertThat(body)
                .doesNotContainIgnoringCase("verdict")
                .doesNotContainIgnoringCase("\"label\"")
                .doesNotContainIgnoringCase("isQualified")
                .doesNotContainIgnoringCase("\"passed\"")
                .doesNotContainIgnoringCase("recommendation");
    }

    // sort=applied_at,desc (Diem cau hinh Dot 4) - kiem tham so duoc nhan dung (200, van tra day du
    // totalScore/rank cho ca hai don), KHONG kiem THU TU tuong doi giua hai don: test nay chay
    // trong @Transactional cua CA lop (moi @Test mot transaction) - now() cua Postgres la
    // TRANSACTION-SCOPED (giong CURRENT_TIMESTAMP), nen hai don tao trong CUNG mot test nhan CUNG
    // mot applied_at, khien ORDER BY applied_at DESC khong co gi phan biet (hoa, thu tu tie-break
    // khong xac dinh o tang HTTP nay). Thu tu ON DINH khi hoa appliedAt (khoa phu trong logic xep
    // hang) da duoc kiem DAY DU va DANG TIN CAY o ApplicationOwnerServiceTest (KHONG boc trong mot
    // transaction dung chung, moi lan ghi la mot transaction rieng nen appliedAt phan biet duoc
    // that) - khong lap lai kiem tra thu tu o day.
    @Test
    void listApplications_explicitAppliedAtSort_acceptsParamAndStillReturnsScores() throws Exception {
        String hrToken = registerAndLoginHr("hr-sort");
        createCompany(hrToken, uniqueName("Cong ty Sort"));
        String jobId = createJob(hrToken, uniqueName("Job Sort"));
        addCriterion(hrToken, jobId, """
                {"name":"Kinh nghiem Java","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-sort", "Ung Vien Sort");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);
        createDoneScoringRunDirectly(UUID.fromString(applicationId), "Kinh nghiem Java", new BigDecimal("55.000"));

        MvcResult result = listApplicationsSorted(hrToken, jobId, "applied_at,desc");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"totalScore\":55.000");
        assertThat(body).contains("\"rank\":1");
    }

    @Test
    void listApplications_afterCreatingScoringRun_includesLatestScoringRunInfo() throws Exception {
        String hrToken = registerAndLoginHr("hr-latest-run");
        createCompany(hrToken, uniqueName("Cong ty Latest Run"));
        String jobId = createJob(hrToken, uniqueName("Job Latest Run"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-latest-run", "Tran Thi Ung Vien");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        MvcResult createResult = createScoringRun(hrToken, applicationId);
        assertThat(createResult.getResponse().getStatus()).isEqualTo(201);
        String runId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        MvcResult result = listApplications(hrToken, jobId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"latestScoringRunId\":\"" + runId + "\"");
        assertThat(body).contains("\"latestScoringRunStatus\":\"PENDING\"");
        assertThat(body).contains("\"latestScoringRunFinishedAt\":null");
    }

    @Test
    void listApplications_jobWithNoApplications_returnsEmptyList() throws Exception {
        String hrToken = registerAndLoginHr("hr-no-apps");
        createCompany(hrToken, uniqueName("Cong ty Khong Co Don"));
        String jobId = createJob(hrToken, uniqueName("Job Khong Co Don"));

        MvcResult result = listApplications(hrToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString().trim()).isEqualTo("[]");
    }

    // ---- Case am ----

    @Test
    void listApplications_calledByCandidate_returns403() throws Exception {
        String hrToken = registerAndLoginHr("hr-cand-call");
        createCompany(hrToken, uniqueName("Cong ty Cand Call"));
        String jobId = createJob(hrToken, uniqueName("Job Cand Call"));
        String candidateToken = registerAndLoginCandidate("cand-caller", "Ung Vien Goi Sai Quyen");

        MvcResult result = listApplications(candidateToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listApplications_byHrOfAnotherCompany_returns403() throws Exception {
        String hrToken = registerAndLoginHr("hr-owner");
        createCompany(hrToken, uniqueName("Cong ty Chu"));
        String jobId = createJob(hrToken, uniqueName("Job Chu"));

        String otherHrToken = registerAndLoginHr("hr-other");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult result = listApplications(otherHrToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listApplications_jobNotFound_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-job-notfound");
        createCompany(hrToken, uniqueName("Cong ty Job Khong Ton Tai"));

        MvcResult result = listApplications(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("JOB_NOT_FOUND");
    }
}

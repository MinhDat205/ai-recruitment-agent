package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.explanation.ScoreExplanationErrorCode;
import com.recruitment.ai.explanation.ScoreExplanationService;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Mock ChatModel qua LlmTestConfiguration (mau ScoringRunOrchestratorTest, ke hoach D2) - orchestrator
// nay CO goi LLM (khac AggregationOrchestrator, D3, khong goi gi ca).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ScoreExplanationOrchestratorTest {

    private static final String VALID_JSON =
            """
            {"summary": "Ứng viên có kinh nghiệm Java tốt nhưng còn thiếu kinh nghiệm Docker",
             "strengths": [{"criterionName": "Kinh nghiem Java", "point": "Diem cao, nhan dinh tich cuc"}],
             "weaknesses": [{"criterionName": "Kinh nghiem Docker", "point": "Diem 0, khong tim thay bang chung"}]}
            """;

    // Chi nhac DUNG mot tieu chi "Kinh nghiem Java" - dung cho cac test chi insert mot tieu chi nay
    // (khac VALID_JSON o tren, nhac ca "Kinh nghiem Docker" - dung cho test co du ba tieu chi).
    private static final String SINGLE_CRITERION_VALID_JSON =
            """
            {"summary": "Ứng viên có kinh nghiệm Java tốt", "strengths": [{"criterionName": "Kinh nghiem Java", "point": "Diem cao"}], "weaknesses": []}
            """;

    private static final String INVALID_JSON = "day khong phai JSON hop le";

    @Autowired
    private ScoreExplanationOrchestrator orchestrator;

    @Autowired
    private ScoreExplanationStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

    @Autowired
    private ScoreExplanationRepository scoreExplanationRepository;

    @Autowired
    private ScoreExplanationAttemptRepository scoreExplanationAttemptRepository;

    @Autowired
    private ChatModel chatModel;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private ChatResponse fakeResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(ChatResponseMetadata.builder()
                        .model("claude-sonnet-4-6")
                        .usage(new DefaultUsage(200, 100))
                        .build())
                .build();
    }

    private UUID createJobWithRubric() {
        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hr = userRepository.save(hr);

        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle("Backend Developer");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);

        Rubric rubric = new Rubric();
        rubric.setJobId(job.getId());
        rubric.setName("Rubric Backend");
        rubric.setLocked(true);
        rubricRepository.save(rubric);

        return job.getId();
    }

    private UUID createApplicationFor(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        return application.getId();
    }

    private UUID createDoneRun(UUID applicationId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalScore(new BigDecimal("60.000"));
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    private void insertCriterionScore(UUID runId, String name, BigDecimal weight, int maxScore, BigDecimal score) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(runId);
        criterionScore.setCriterionNameSnapshot(name);
        criterionScore.setWeightSnapshot(weight);
        criterionScore.setMaxScoreSnapshot(maxScore);
        criterionScore.setScore(score);
        criterionScore.setReasoning("Ly do gia lap trong test cho tieu chi " + name);
        criterionScore.setEvidence(score.compareTo(BigDecimal.ZERO) == 0
                ? List.of()
                : List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    // ---- Case duong ----

    @Test
    void processOne_doneRunReadyForExplanation_savesExplanationWithHandVerifiedMetAndMissingCriteria() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("50.00"), 5, new BigDecimal("4.00"));
        insertCriterionScore(runId, "Kinh nghiem Docker", new BigDecimal("30.00"), 5, new BigDecimal("0.00"));
        insertCriterionScore(runId, "Tieng Anh", new BigDecimal("20.00"), 5, new BigDecimal("3.00"));
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(runId);

        entityManager.clear();
        List<ScoreExplanation> saved = scoreExplanationRepository.findByScoringRunIdIn(List.of(runId));
        assertThat(saved).hasSize(1);
        ScoreExplanation explanation = saved.get(0);
        assertThat(explanation.getSummary())
                .isEqualTo("Ứng viên có kinh nghiệm Java tốt nhưng còn thiếu kinh nghiệm Docker");
        assertThat(explanation.getStrengths()).hasSize(1);
        assertThat(explanation.getStrengths().get(0).criterionName()).isEqualTo("Kinh nghiem Java");
        assertThat(explanation.getWeaknesses()).hasSize(1);
        assertThat(explanation.getWeaknesses().get(0).criterionName()).isEqualTo("Kinh nghiem Docker");
        assertThat(explanation.getModel()).isEqualTo("claude-sonnet-4-6");
        // Chuoi viet chet, khong tham chieu ScoreExplanationService.PROMPT_VERSION (package-private,
        // khac package voi test nay) - cung tien le voi ScoringRunOrchestratorTest hardcode
        // "criterion-score-v1".
        assertThat(explanation.getPromptVersion()).isEqualTo("score-explanation-v1");

        // Tinh tay: score > 0 -> met (Java, Tieng Anh); score = 0 -> missing (Docker). Q3 - KHONG
        // do LLM sinh, xem ScoreExplanationOrchestrator.doProcess.
        assertThat(explanation.getMetCriteria()).containsExactlyInAnyOrder("Kinh nghiem Java", "Tieng Anh");
        assertThat(explanation.getMissingCriteria()).containsExactly("Kinh nghiem Docker");
    }

    // ---- That bai / attempts ----

    @Test
    void processOne_llmFailsBothAttempts_recordsFailedAttemptAndDoesNotSaveExplanation() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("100.00"), 5, new BigDecimal("4.00"));
        doReturn(fakeResponse(INVALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(runId);

        entityManager.clear();
        assertThat(scoreExplanationRepository.existsByScoringRunId(runId)).isFalse();
        ScoreExplanationAttempt attempt =
                scoreExplanationAttemptRepository.findByScoringRunId(runId).orElseThrow();
        assertThat(attempt.getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getLastError()).isEqualTo(ScoreExplanationErrorCode.LLM_INVALID_JSON.formatted());
    }

    @Test
    void processOne_calledAgainAfterPriorFailure_incrementsAttemptCountToTwo() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("100.00"), 5, new BigDecimal("4.00"));
        doReturn(fakeResponse(INVALID_JSON)).when(chatModel).call(any(Prompt.class));
        orchestrator.processOne(runId);

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoreExplanationAttempt attempt =
                scoreExplanationAttemptRepository.findByScoringRunId(runId).orElseThrow();
        assertThat(attempt.getAttemptCount()).isEqualTo(2);
        assertThat(scoreExplanationRepository.existsByScoringRunId(runId)).isFalse();
    }

    // Loi "khong luong truoc" thuc su (khac loi LLM - da duoc ScoreExplanationService tu boc thanh
    // ScoreExplanationFailedException BEN TRONG explain(), khong con la "khong luong truoc" nua khi
    // toi duoc processOne). Khong dung nghia trong nao co the tao ra tinh huong nay qua du lieu DB
    // that: moi cot CriterionScore doc toi (weightSnapshot/maxScoreSnapshot/score/reasoning) deu
    // NOT NULL o DB, khong co "du lieu hop le nhung thieu" nao de mo phong nhu
    // AggregationOrchestratorTest da lam voi rubric_snapshot=NULL. Dung mot collaborator gia lap CHI
    // cho ScoreExplanationService (mock method explain() nem RuntimeException thuan, khong phai
    // ScoreExplanationFailedException) - moi thu khac (hai repository, state service) van la bean
    // that/DB that, chi khoi tao orchestrator thu cong voi collaborator gia lap nay.
    @Test
    void processOne_unexpectedRuntimeExceptionFromCollaborator_alsoRecordsFailedAttempt() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("100.00"), 5, new BigDecimal("4.00"));

        ScoreExplanationService throwingService = Mockito.mock(ScoreExplanationService.class);
        Mockito.when(throwingService.explain(Mockito.anyList())).thenThrow(new RuntimeException("loi gia lap khong luong truoc"));
        ScoreExplanationOrchestrator orchestratorWithThrowingService =
                new ScoreExplanationOrchestrator(scoringRunRepository, criterionScoreRepository, throwingService, stateService);

        orchestratorWithThrowingService.processOne(runId);

        entityManager.clear();
        assertThat(scoreExplanationRepository.existsByScoringRunId(runId)).isFalse();
        ScoreExplanationAttempt attempt =
                scoreExplanationAttemptRepository.findByScoringRunId(runId).orElseThrow();
        assertThat(attempt.getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getLastError()).isEqualTo(ScoreExplanationErrorCode.LLM_ERROR.formatted());
    }

    // ---- Chong trung ----

    // Nhip poll "thu hai" tren CUNG mot luot da THANH CONG o lan dau (khac D3: D4 KHONG kiem
    // "da co bao cao chua" truoc khi goi LLM - chap nhan mot lan goi LLM thua trong tinh huong hiem
    // nay, xem comment trong ScoreExplanationOrchestrator) - lan goi thu hai VAN goi LLM (ton mot
    // lan goi LLM lang phi, chap nhan duoc), nhung UNIQUE tren scoring_run_id chan INSERT thu hai,
    // ScoreExplanationOrchestrator bat DataIntegrityViolationException va coi la no-op - method
    // khong nem gi ca, chi con dung MOT dong score_explanations.
    @Test
    void processOne_calledTwiceInARowAfterSuccess_secondCallIsNoOpDoesNotThrow() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("100.00"), 5, new BigDecimal("4.00"));
        doReturn(fakeResponse(SINGLE_CRITERION_VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(runId);
        entityManager.clear();
        assertThat(scoreExplanationRepository.findByScoringRunIdIn(List.of(runId))).hasSize(1);

        orchestrator.processOne(runId);

        entityManager.clear();
        assertThat(scoreExplanationRepository.findByScoringRunIdIn(List.of(runId))).hasSize(1);
    }
}

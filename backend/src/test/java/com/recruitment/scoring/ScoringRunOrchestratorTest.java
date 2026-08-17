package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.criterion.CriterionScoringErrorCode;
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
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterion;
import com.recruitment.rubric.RubricCriterionRepository;
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

// Mau y het ResumeParsingOrchestratorTest: mock ChatModel qua LlmTestConfiguration
// (@Primary, default-answer throw), goi thang orchestrator.processOne(), khong cho @Scheduled tick
// (app.scoring.enabled=false o application-test.yml).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ScoringRunOrchestratorTest {

    private static final String VALID_JSON =
            """
            {"score": 4, "reasoning": "Ung vien co kinh nghiem phu hop voi tieu chi nay",
             "evidence": [{"quote": "Co 3 nam kinh nghiem lam viec voi Java, Docker va tieng Anh giao tiep tot.", "section": "experience"}]}
            """;

    private static final String RAW_TEXT =
            "Nguyen Van A\nCo 3 nam kinh nghiem lam viec voi Java, Docker va tieng Anh giao tiep tot.";

    @Autowired
    private ScoringRunOrchestrator orchestrator;

    @Autowired
    private ScoringRunStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private RubricCriterionRepository rubricCriterionRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

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
                        .usage(new DefaultUsage(100, 50))
                        .build())
                .build();
    }

    private UUID createJobWithRubric(List<CriterionSpec> criteria) {
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

        int order = 0;
        for (CriterionSpec spec : criteria) {
            RubricCriterion criterion = new RubricCriterion();
            criterion.setRubricId(rubric.getId());
            criterion.setName(spec.name());
            criterion.setWeight(spec.weight());
            criterion.setMaxScore(spec.maxScore());
            criterion.setDisplayOrder(order++);
            rubricCriterionRepository.saveAndFlush(criterion);
        }

        return job.getId();
    }

    private record CriterionSpec(String name, BigDecimal weight, int maxScore) {
    }

    private UUID createApplicationWithParsedResume(UUID jobId, String rawText) {
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

        if (rawText != null) {
            ResumeParsedData parsedData = new ResumeParsedData();
            parsedData.setResumeId(resume.getId());
            parsedData.setRawText(rawText);
            parsedData.setData(new ResumeParsedPayload(
                    new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()));
            parsedData.setModel("claude-sonnet-4-6");
            parsedData.setPromptVersion("resume-parse-v1");
            resumeParsedDataRepository.save(parsedData);
        }

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

    private UUID createPendingRun(UUID applicationId, UUID jobId) {
        List<RubricCriterion> criteria = rubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc(
                rubricRepository.findByJobId(jobId).orElseThrow().getId());
        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(
                rubricRepository.findByJobId(jobId).orElseThrow(), criteria);

        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        run.setRubricSnapshot(snapshot);
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    @SuppressWarnings("unchecked")
    private Object readTotalScore(UUID scoringRunId) {
        return entityManager
                .createNativeQuery("SELECT total_score FROM scoring_runs WHERE id = ?1")
                .setParameter(1, scoringRunId)
                .getSingleResult();
    }

    @Test
    void processOne_allThreeCriteriaScored_finishesRunKeepsStatusRunningAndSumsTokenUsage() {
        UUID jobId = createJobWithRubric(List.of(
                new CriterionSpec("Kinh nghiem Java", new BigDecimal("40"), 5),
                new CriterionSpec("Kinh nghiem Docker", new BigDecimal("30"), 5),
                new CriterionSpec("Tieng Anh", new BigDecimal("30"), 5)));
        UUID applicationId = createApplicationWithParsedResume(jobId, RAW_TEXT);
        UUID runId = createPendingRun(applicationId, jobId);
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(run.getPromptVersion()).isEqualTo("criterion-score-v1");
        // 3 lan goi, moi lan DefaultUsage(100, 50) => tong 150 token/lan => 450.
        assertThat(run.getTokenUsage()).isEqualTo(450);
        assertThat(readTotalScore(runId)).isNull();

        List<CriterionScore> scores = criterionScoreRepository.findAll().stream()
                .filter(s -> s.getScoringRunId().equals(runId))
                .toList();
        assertThat(scores).hasSize(3);
        assertThat(scores)
                .extracting(CriterionScore::getWeightSnapshot)
                .containsExactlyInAnyOrder(new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("30.00"));

        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void processOne_editingRubricAfterScoring_doesNotChangeAlreadyRecordedWeightSnapshot() {
        UUID jobId = createJobWithRubric(List.of(new CriterionSpec("Kinh nghiem Java", new BigDecimal("100"), 5)));
        UUID applicationId = createApplicationWithParsedResume(jobId, RAW_TEXT);
        UUID runId = createPendingRun(applicationId, jobId);
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(runId);
        entityManager.clear();

        // Sua rubric SAU khi cham xong, di thang qua repository (rubric da khoa nen
        // RubricOwnerService.requireNotLocked se chan qua tang service - o day kiem tang du lieu,
        // gia dinh mot duong ghi khac ngoai y muon van khong lam sai lich su da chup).
        UUID rubricId = rubricRepository.findByJobId(jobId).orElseThrow().getId();
        RubricCriterion criterion =
                rubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc(rubricId).get(0);
        criterion.setWeight(new BigDecimal("55"));
        rubricCriterionRepository.saveAndFlush(criterion);
        entityManager.clear();

        List<CriterionScore> scores = criterionScoreRepository.findAll().stream()
                .filter(s -> s.getScoringRunId().equals(runId))
                .toList();
        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).getWeightSnapshot()).isEqualByComparingTo("100");
    }

    @Test
    void processOne_secondCriterionFailsAfterRetry_marksFailedKeepsFirstRowSkipsThird() {
        UUID jobId = createJobWithRubric(List.of(
                new CriterionSpec("Kinh nghiem Java", new BigDecimal("40"), 5),
                new CriterionSpec("Kinh nghiem Docker", new BigDecimal("30"), 5),
                new CriterionSpec("Tieng Anh", new BigDecimal("30"), 5)));
        UUID applicationId = createApplicationWithParsedResume(jobId, RAW_TEXT);
        UUID runId = createPendingRun(applicationId, jobId);
        // Cuoc 1 (tieu chi 1): hop le. Cuoc 2+3 (tieu chi 2, lan dau + retry): JSON hong ca hai lan
        // -> LLM_INVALID_JSON, dung vong lap, tieu chi 3 KHONG duoc goi (chi 3 cuoc goi tong cong).
        doReturn(fakeResponse(VALID_JSON))
                .doReturn(fakeResponse("khong phai JSON hop le"))
                .doReturn(fakeResponse("van khong phai JSON hop le"))
                .when(chatModel)
                .call(any(Prompt.class));

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getErrorMessage()).isEqualTo(CriterionScoringErrorCode.LLM_INVALID_JSON.formatted());

        List<CriterionScore> scores = criterionScoreRepository.findAll().stream()
                .filter(s -> s.getScoringRunId().equals(runId))
                .toList();
        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).getCriterionNameSnapshot()).isEqualTo("Kinh nghiem Java");

        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    // Chung minh claim() thuc su ngan lai, khong chi doc thay hop ly: KHONG stub lai chatModel sau
    // Mockito.reset o duoi - neu orchestrator xu ly lai lan hai, no se goi LLM that va bi chan boi
    // default-answer throw cua LlmTestConfiguration.
    @Test
    void processOne_runAlreadyRunning_claimFailsAndDoesNothing() {
        UUID jobId = createJobWithRubric(List.of(new CriterionSpec("Kinh nghiem Java", new BigDecimal("100"), 5)));
        UUID applicationId = createApplicationWithParsedResume(jobId, RAW_TEXT);
        UUID runId = createPendingRun(applicationId, jobId);
        boolean claimed = stateService.claim(runId);
        assertThat(claimed).isTrue();

        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(run.getFinishedAt()).isNull();
        assertThat(criterionScoreRepository.findAll().stream()
                        .filter(s -> s.getScoringRunId().equals(runId))
                        .toList())
                .isEmpty();
    }

    // Chung minh luoi an toan cuoi cung trong processOne() (catch RuntimeException bao ngoai
    // doProcess()) hoat dong that: resume_parsed_data "bien mat" (khong duoc tao, du resume da
    // DONE) khien orElseThrow() nem NoSuchElementException tu doProcess() - neu khong duoc bat lai,
    // luot cham se ket vinh vien o RUNNING/finished_at NULL va bi V4 chan moi lan cham lai sau nay.
    @Test
    void processOne_resumeParsedDataMissing_marksFailedInsteadOfStuckRunning() {
        UUID jobId = createJobWithRubric(List.of(new CriterionSpec("Kinh nghiem Java", new BigDecimal("100"), 5)));
        UUID applicationId = createApplicationWithParsedResume(jobId, null);
        UUID runId = createPendingRun(applicationId, jobId);

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getErrorMessage()).isEqualTo(ScoringRunErrorCode.UNEXPECTED_ERROR.formatted());
    }
}

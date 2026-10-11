package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
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
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.RubricSnapshotMapper;
import com.recruitment.scoring.ScoreExplanationOrchestrator;
import com.recruitment.scoring.ScoreExplanationRepository;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunOrchestrator;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-H10 T13 (R-N1) - cau hoi va ban chup cau tra loi sang loc KHONG vao prompt cham diem (FR-H04) lan giai thich
// (FR-H06). Goi THAT processOne cua hai orchestrator, ChatModel mock (LlmTestConfiguration, default-answer throw) bat
// moi Prompt. Moi nhanh: bat duoc >= 1 prompt (nhanh that su goi ChatModel) VA khong message nao (system lan user)
// chua chuoi danh dau.
// KHONG @Transactional - khuon ScoringRunOrchestratorTest/ScoreExplanationOrchestratorTest (orchestrator tu mo
// transaction ngan, can thay du lieu da commit). Du lieu ngau nhien rieng, khong don sau test (L2).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ScreeningAiIsolationIntegrationTest {

    private static final String MARKER = "SCREENING-MARKER-7f3a";

    private static final String RAW_TEXT =
            "Nguyen Van A\nCo 3 nam kinh nghiem lam viec voi Java, Docker va tieng Anh giao tiep tot.";

    // Evidence la chuoi con cua RAW_TEXT de luot cham di het nhanh thanh cong.
    private static final String SCORING_JSON =
            """
            {"score": 4, "reasoning": "Ung vien co kinh nghiem phu hop voi tieu chi nay",
             "evidence": [{"quote": "Co 3 nam kinh nghiem lam viec voi Java, Docker va tieng Anh giao tiep tot.", "section": "experience"}]}
            """;

    private static final String EXPLANATION_JSON =
            """
            {"summary": "Ứng viên có kinh nghiệm Java tốt", "strengths": [{"criterionName": "Kinh nghiem Java", "point": "Diem cao"}], "weaknesses": []}
            """;

    @Autowired
    private ScoringRunOrchestrator scoringRunOrchestrator;

    @Autowired
    private ScoreExplanationOrchestrator scoreExplanationOrchestrator;

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
    private ScoreExplanationRepository scoreExplanationRepository;

    @Autowired
    private ScreeningQuestionRepository screeningQuestionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatModel chatModel;

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

    private UUID createJobWithRubric() {
        User hr = new User();
        hr.setEmail("t13-hr-" + UUID.randomUUID() + "@example.com");
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

        RubricCriterion criterion = new RubricCriterion();
        criterion.setRubricId(rubric.getId());
        criterion.setName("Kinh nghiem Java");
        criterion.setWeight(new BigDecimal("100"));
        criterion.setMaxScore(5);
        criterion.setDisplayOrder(0);
        rubricCriterionRepository.saveAndFlush(criterion);

        return job.getId();
    }

    private UUID createApplicationWithParsedResume(UUID jobId) {
        User candidate = new User();
        candidate.setEmail("t13-cand-" + UUID.randomUUID() + "@example.com");
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

        ResumeParsedData parsedData = new ResumeParsedData();
        parsedData.setResumeId(resume.getId());
        parsedData.setRawText(RAW_TEXT);
        parsedData.setData(new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null));
        parsedData.setModel("claude-sonnet-4-6");
        parsedData.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.save(parsedData);

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        return jobApplicationRepository.save(application).getId();
    }

    // Job co cau hoi DANG HOAT DONG chua chuoi danh dau (noi dung + phuong an), don co ban chup chua chuoi danh dau
    // o noi dung cau hoi, phuong an va cau tra loi; screening_captured_at da dat (don CAPTURED).
    private void addScreeningDataWithMarker(UUID jobId, UUID applicationId) {
        ScreeningQuestion shortText = new ScreeningQuestion();
        shortText.setJobId(jobId);
        shortText.setQuestionType(ScreeningQuestionType.SHORT_TEXT);
        shortText.setQuestionText("Cau hoi " + MARKER);
        shortText.setRequired(true);
        shortText.setDisplayOrder(1);
        shortText = screeningQuestionRepository.saveAndFlush(shortText);

        ScreeningQuestion choice = new ScreeningQuestion();
        choice.setJobId(jobId);
        choice.setQuestionType(ScreeningQuestionType.SINGLE_CHOICE);
        choice.setQuestionText("Lua chon " + MARKER);
        choice.setOptions(List.of("Phuong an " + MARKER, "Khac"));
        choice.setDisplayOrder(2);
        choice = screeningQuestionRepository.saveAndFlush(choice);

        jdbcTemplate.update(
                "UPDATE job_applications SET screening_captured_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC),
                applicationId);
        String insert =
                """
                INSERT INTO application_screening_answers
                    (application_id, question_id, display_order, question_type_snapshot, question_text_snapshot,
                     options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """;
        jdbcTemplate.update(insert, applicationId, shortText.getId(), 1, "SHORT_TEXT", "Cau hoi " + MARKER, null,
                true, "Tra loi " + MARKER, null);
        jdbcTemplate.update(insert, applicationId, choice.getId(), 2, "SINGLE_CHOICE", "Lua chon " + MARKER,
                "[\"Phuong an " + MARKER + "\",\"Khac\"]", false, "Phuong an " + MARKER, null);
    }

    private List<Prompt> capturedPrompts() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        return captor.getAllValues();
    }

    private static void assertNoMarker(List<Prompt> prompts) {
        assertThat(prompts).as("nhanh phai that su goi ChatModel").isNotEmpty();
        for (Prompt prompt : prompts) {
            assertThat(prompt.getInstructions()).isNotEmpty();
            for (Message message : prompt.getInstructions()) {
                assertThat(message.getText())
                        .as("message %s", message.getMessageType())
                        .doesNotContain(MARKER);
            }
        }
    }

    // (a) FR-H04 - processOne -> CriterionScoringService.score.
    @Test
    void t13_scoringPrompts_doNotContainScreeningData() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationWithParsedResume(jobId);
        addScreeningDataWithMarker(jobId, applicationId);
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow();
        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(
                rubric, rubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc(rubric.getId()));
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        run.setRubricSnapshot(snapshot);
        UUID runId = scoringRunRepository.saveAndFlush(run).getId();
        doReturn(fakeResponse(SCORING_JSON)).when(chatModel).call(any(Prompt.class));

        scoringRunOrchestrator.processOne(runId);

        assertNoMarker(capturedPrompts());
        // Nhanh chay het: tieu chi da duoc cham.
        assertThat(criterionScoreRepository.findByScoringRunId(runId)).hasSize(1);
    }

    // (b) FR-H06 - processOne cho luot DONE cua chinh don co ban chup.
    @Test
    void t13_explanationPrompts_doNotContainScreeningData() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationWithParsedResume(jobId);
        addScreeningDataWithMarker(jobId, applicationId);
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalScore(new BigDecimal("80.000"));
        UUID runId = scoringRunRepository.saveAndFlush(run).getId();
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(runId);
        criterionScore.setCriterionNameSnapshot("Kinh nghiem Java");
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(new BigDecimal("4.00"));
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
        doReturn(fakeResponse(EXPLANATION_JSON)).when(chatModel).call(any(Prompt.class));

        scoreExplanationOrchestrator.processOne(runId);

        assertNoMarker(capturedPrompts());
        // Nhanh chay het: giai thich da duoc luu.
        assertThat(scoreExplanationRepository.findByScoringRunIdIn(List.of(runId))).hasSize(1);
    }
}

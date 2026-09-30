package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.embedding.EmbeddingTextFormat;
import com.recruitment.common.LlmRetryPolicy;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// FR-C05 R-R5, R-R6 (REQUIREMENT muc 7.7). KHONG @Transactional o class: orchestrator claim/ghi bang
// cac transaction ngan rieng nhu luc chay that, test doc lai sau khi tung transaction da commit.
// ChatModel mock (LlmTestConfiguration, default-answer throw) - khong goi LLM that; EmbeddingModel khong
// duoc stub, bat ky loi goi nao cung do.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ResumeReparseOrchestratorTest {

    // Co ky tu co dau, khoang trang Unicode (U+00A0), tab va xuong dong - de phat hien bat ky buoc nao
    // "chuan hoa" lai raw_text khi ghi.
    private static final String RAW_TEXT =
            "Nguyễn Văn A — Backend Developer\n\tKinh nghiệm: 01/2020 – Hiện tại\r\nĐịa chỉ: Bình Dương  ";

    private static final String V2_JSON_TEMPLATE =
            """
            {"contact": {"fullName": "Nguyễn Văn A", "email": "a@example.com"},
             "education": [],
             "experience": [{"company": "Công ty ABC", "title": "Backend Developer", "startDate": "01/2020", "endDate": "06/2020", "description": "API"}],
             "skills": ["Java"],
             "certifications": [],
             "projects": [],
             "currentTitle": "Backend Developer",
             "industryCode": "%s",
             "locationText": "Bình Dương"}
            """;

    @Autowired
    private ResumeReparseOrchestrator orchestrator;

    @Autowired
    private ResumeReparseStateService stateService;

    @Autowired
    private ResumeReparseRequestRepository requestRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

    @Autowired
    private LlmRetryPolicy retryPolicy;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetAiMocks() {
        Mockito.reset(chatModel, embeddingModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private record Seeded(UUID candidateId, UUID resumeId, UUID parsedDataId, UUID scoringRunId) {
    }

    private ChatResponse fakeResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(ChatResponseMetadata.builder()
                        .model("claude-sonnet-4-6")
                        .usage(new DefaultUsage(300, 200))
                        .build())
                .build();
    }

    private UUID createUser(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user).getId();
    }

    // CV v1 DONE co embedding, co mot luot cham diem cu (criterion_scores + score_explanations). file_url
    // tro toi file KHONG ton tai: trich xuat lai thanh cong chung minh khong doc lai file (R-R5).
    private Seeded seedV1ResumeWithScoring() {
        UUID candidateId = createUser(Role.CANDIDATE);
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/khong-ton-tai-" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        UUID resumeId = resumeRepository.save(resume).getId();

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText(RAW_TEXT);
        data.setData(new ResumeParsedPayload(
                null,
                List.of(),
                List.of(new ResumeParsedPayload.Experience("Công ty ABC", "Backend", "01/2020", "Hiện tại", "API")),
                List.of("Java"),
                List.of(),
                List.of(),
                null,
                null,
                null));
        data.setModel("claude-sonnet-4-5");
        data.setPromptVersion("resume-parse-v1");
        data.setTokenUsage(111);
        UUID parsedDataId = resumeParsedDataRepository.saveAndFlush(data).getId();

        float[] vector = new float[1536];
        Arrays.fill(vector, 0.01f);
        transactionTemplate.executeWithoutResult(status ->
                resumeParsedDataRepository.updateEmbedding(parsedDataId, EmbeddingTextFormat.toVectorText(vector)));

        UUID scoringRunId = seedScoringRun(candidateId, resumeId);
        return new Seeded(candidateId, resumeId, parsedDataId, scoringRunId);
    }

    private UUID seedScoringRun(UUID candidateId, UUID resumeId) {
        UUID hrId = createUser(Role.HR);
        Company company = new Company();
        company.setOwnerId(hrId);
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hrId);
        job.setTitle("Backend Developer");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);

        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setCandidateId(candidateId);
        application.setResumeId(resumeId);
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        ScoringRun run = new ScoringRun();
        run.setApplicationId(application.getId());
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalScore(new BigDecimal("80.000"));
        run = scoringRunRepository.saveAndFlush(run);

        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(run.getId());
        criterionScore.setCriterionNameSnapshot("Kinh nghiem Java");
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(new BigDecimal("4.00"));
        criterionScore.setReasoning("Co kinh nghiem backend");
        criterionScore.setEvidence(List.of(new EvidenceEntry("Backend Developer", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);

        jdbcTemplate.update(
                "INSERT INTO score_explanations (scoring_run_id, summary, model, prompt_version) "
                        + "VALUES (?, 'Tom tat gia lap', 'claude-sonnet-4-6', 'score-explanation-v1')",
                run.getId());
        return run.getId();
    }

    private UUID createRequest(UUID resumeId) {
        ResumeReparseRequest request = new ResumeReparseRequest();
        request.setResumeId(resumeId);
        request.setStatus(ResumeReparseRequestStatus.PENDING);
        return requestRepository.saveAndFlush(request).getId();
    }

    private String scoringSnapshot(UUID scoringRunId) {
        String scores = jdbcTemplate.queryForObject(
                "SELECT md5(string_agg(t::text, '|' ORDER BY t.id)) FROM criterion_scores t WHERE scoring_run_id = ?",
                String.class,
                scoringRunId);
        String explanations = jdbcTemplate.queryForObject(
                "SELECT md5(string_agg(t::text, '|' ORDER BY t.id)) FROM score_explanations t WHERE scoring_run_id = ?",
                String.class,
                scoringRunId);
        return scores + "/" + explanations;
    }

    private String parsedDataSnapshot(UUID parsedDataId) {
        return jdbcTemplate.queryForObject(
                "SELECT md5(t::text) FROM resume_parsed_data t WHERE id = ?", String.class, parsedDataId);
    }

    private byte[] rawTextBytes(UUID parsedDataId) {
        return jdbcTemplate
                .queryForObject("SELECT raw_text FROM resume_parsed_data WHERE id = ?", String.class, parsedDataId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private ResumeReparseRequest reloadRequest(UUID requestId) {
        entityManager.clear();
        return requestRepository.findById(requestId).orElseThrow();
    }

    private ResumeParsedData reloadParsed(UUID parsedDataId) {
        entityManager.clear();
        return resumeParsedDataRepository.findById(parsedDataId).orElseThrow();
    }

    // ---- Thanh cong ----

    @Test
    void processOne_success_updatesInPlaceKeepsRawTextClearsEmbeddingAndLeavesScoringUntouched() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        byte[] rawBefore = rawTextBytes(seeded.parsedDataId());
        String scoringBefore = scoringSnapshot(seeded.scoringRunId());
        assertThat(resumeParsedDataRepository.hasEmbedding(seeded.parsedDataId())).isTrue();

        // Trong LUC goi LLM: parse_status van DONE (CV khong bien khoi form ung tuyen), yeu cau dang RUNNING.
        AtomicReference<String> parseStatusDuringLlm = new AtomicReference<>();
        AtomicReference<String> requestStatusDuringLlm = new AtomicReference<>();
        AtomicReference<Prompt> sentPrompt = new AtomicReference<>();
        doAnswer(invocation -> {
                    sentPrompt.set(invocation.getArgument(0));
                    parseStatusDuringLlm.set(jdbcTemplate.queryForObject(
                            "SELECT parse_status FROM resumes WHERE id = ?", String.class, seeded.resumeId()));
                    requestStatusDuringLlm.set(jdbcTemplate.queryForObject(
                            "SELECT status FROM resume_reparse_requests WHERE id = ?", String.class, requestId));
                    return fakeResponse(V2_JSON_TEMPLATE.formatted("IT_SOFTWARE"));
                })
                .when(chatModel)
                .call(any(Prompt.class));

        orchestrator.processOne(requestId);

        assertThat(parseStatusDuringLlm.get()).isEqualTo("DONE");
        assertThat(requestStatusDuringLlm.get()).isEqualTo("RUNNING");
        // Dau vao LLM la raw_text DA LUU (khong doc file - file khong ton tai). ChatClient.responseEntity(converter)
        // cua Spring AI 2.0 tu noi huong dan dinh dang JSON cua BeanOutputConverter vao CUOI user message -
        // ngoai phan do, user message phai dung bang raw_text, khong them/bot gi.
        String userText = sentPrompt.get().getUserMessage().getText();
        assertThat(userText).startsWith(RAW_TEXT);
        assertThat(userText.substring(RAW_TEXT.length()).strip())
                .isEqualTo(new BeanOutputConverter<>(ResumeParsedPayload.class).getFormat().strip());

        ResumeParsedData after = reloadParsed(seeded.parsedDataId());
        assertThat(after.getId()).isEqualTo(seeded.parsedDataId());
        assertThat(after.getPromptVersion()).isEqualTo(ResumeParsingService.PROMPT_VERSION);
        assertThat(after.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(after.getTokenUsage()).isEqualTo(500);
        assertThat(after.getData().currentTitle()).isEqualTo("Backend Developer");
        assertThat(after.getIndustryCode()).isEqualTo("IT_SOFTWARE").isEqualTo(after.getData().industryCode());
        assertThat(after.getRegionCode()).isEqualTo("HO_CHI_MINH");
        assertThat(after.getData().locationText()).isEqualTo("Bình Dương");
        // Kinh nghiem tinh lai tu data moi (01/2020-06/2020 = 6), cung transaction ghi.
        assertThat(after.getExperienceMonths()).isEqualTo(6);
        assertThat(after.getExperienceComputedAt()).isNotNull();

        assertThat(rawTextBytes(seeded.parsedDataId())).isEqualTo(rawBefore);
        assertThat(resumeParsedDataRepository.hasEmbedding(seeded.parsedDataId())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM resume_parsed_data WHERE resume_id = ?", Long.class, seeded.resumeId()))
                .isEqualTo(1L);
        assertThat(resumeRepository.findById(seeded.resumeId()).orElseThrow().getParseStatus())
                .isEqualTo(ParseStatus.DONE);
        assertThat(scoringSnapshot(seeded.scoringRunId())).isEqualTo(scoringBefore);

        ResumeReparseRequest request = reloadRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(ResumeReparseRequestStatus.DONE);
        assertThat(request.getErrorMessage()).isNull();
        assertThat(request.getFinishedAt()).isNotNull();
        verifyNoInteractions(embeddingModel);
    }

    // R-C4 sau trich xuat lai: AI tra ma la -> cot va JSON deu null.
    @Test
    void processOne_success_unknownIndustryFromAi_columnAndJsonBothNull() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        doReturn(fakeResponse(V2_JSON_TEMPLATE.formatted("MA_BIA"))).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);

        ResumeParsedData after = reloadParsed(seeded.parsedDataId());
        assertThat(after.getPromptVersion()).isEqualTo(ResumeParsingService.PROMPT_VERSION);
        assertThat(after.getIndustryCode()).isNull();
        assertThat(after.getData().industryCode()).isNull();
    }

    // Prompt v2 mang danh sach nganh tu danh muc (khong OTHER) va dung ban v2.
    @Test
    void processOne_promptContainsIndustryListWithoutOther() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        AtomicReference<Prompt> sentPrompt = new AtomicReference<>();
        doAnswer(invocation -> {
                    sentPrompt.set(invocation.getArgument(0));
                    return fakeResponse(V2_JSON_TEMPLATE.formatted("IT_SOFTWARE"));
                })
                .when(chatModel)
                .call(any(Prompt.class));

        orchestrator.processOne(requestId);

        String system = sentPrompt.get().getSystemMessage().getText();
        assertThat(system).contains("- IT_SOFTWARE: Công nghệ thông tin - Phần mềm");
        assertThat(system).contains("- ACCOUNTING_AUDIT: ");
        assertThat(system).doesNotContain("OTHER:").doesNotContain("Ngành khác");
        assertThat(system).doesNotContain("{industries}");
    }

    // ---- That bai ----

    @Test
    void processOne_invalidJsonTwice_requestFailedAndParsedDataUntouched() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        String parsedBefore = parsedDataSnapshot(seeded.parsedDataId());
        String scoringBefore = scoringSnapshot(seeded.scoringRunId());
        doReturn(fakeResponse("khong phai JSON")).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);

        ResumeReparseRequest request = reloadRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(ResumeReparseRequestStatus.FAILED);
        assertThat(request.getErrorMessage()).isEqualTo(ResumeParsingErrorCode.LLM_INVALID_JSON.formatted());
        assertThat(request.getFinishedAt()).isNotNull();
        // Ca hang resume_parsed_data (data v1, prompt_version, embedding, raw_text...) giu nguyen tung byte.
        assertThat(parsedDataSnapshot(seeded.parsedDataId())).isEqualTo(parsedBefore);
        assertThat(reloadParsed(seeded.parsedDataId()).getPromptVersion()).isEqualTo("resume-parse-v1");
        assertThat(resumeParsedDataRepository.hasEmbedding(seeded.parsedDataId())).isTrue();
        assertThat(resumeRepository.findById(seeded.resumeId()).orElseThrow().getParseStatus())
                .isEqualTo(ParseStatus.DONE);
        assertThat(scoringSnapshot(seeded.scoringRunId())).isEqualTo(scoringBefore);
    }

    // Loi LLM bat ky (khong phai tam thoi) -> LLM_ERROR, khong ghi message goc vao cot.
    @Test
    void processOne_llmThrows_requestFailedWithStandardCodeOnly() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        String parsedBefore = parsedDataSnapshot(seeded.parsedDataId());
        String secret = "chi tiet loi noi bo khong duoc vao DB";
        Mockito.doThrow(new RuntimeException(secret)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);

        ResumeReparseRequest request = reloadRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(ResumeReparseRequestStatus.FAILED);
        assertThat(request.getErrorMessage()).isEqualTo(ResumeParsingErrorCode.LLM_ERROR.formatted());
        assertThat(request.getErrorMessage()).doesNotContain(secret);
        assertThat(parsedDataSnapshot(seeded.parsedDataId())).isEqualTo(parsedBefore);
    }

    // ---- Claim ----

    @Test
    void processOne_requestNotPending_doesNothing() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        assertThat(stateService.claim(requestId)).isTrue();

        orchestrator.processOne(requestId);

        verify(chatModel, never()).call(any(Prompt.class));
        assertThat(reloadRequest(requestId).getStatus()).isEqualTo(ResumeReparseRequestStatus.RUNNING);
    }

    @Test
    void claim_requestInBackoff_returnsFalse() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        jdbcTemplate.update(
                "UPDATE resume_reparse_requests SET next_attempt_at = now() + interval '1 hour' WHERE id = ?",
                requestId);

        assertThat(stateService.claim(requestId)).isFalse();
        assertThat(requestRepository.findReadyForProcessing(10_000))
                .extracting(ResumeReparseRequest::getId)
                .doesNotContain(requestId);
    }

    // ---- Thu lai co backoff (LlmRetryPolicy) ----

    @Test
    void markTemporaryFailure_firstAttempt_backToPendingWithBackoff() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        stateService.claim(requestId);

        stateService.markTemporaryFailure(requestId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        ResumeReparseRequest request = reloadRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(ResumeReparseRequestStatus.PENDING);
        assertThat(request.getAttemptCount()).isEqualTo(1);
        assertThat(request.getNextAttemptAt()).isNotNull();
        assertThat(request.getClaimedAt()).isNull();
        assertThat(request.getFinishedAt()).isNull();
        assertThat(request.getErrorMessage())
                .isEqualTo(ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE.formatted());
    }

    // Bien: attempt_count = max-1 truoc lan loi -> het luot, FAILED voi LLM_RETRY_EXHAUSTED.
    @Test
    void markTemporaryFailure_lastAttempt_failsWithRetryExhausted() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        jdbcTemplate.update(
                "UPDATE resume_reparse_requests SET attempt_count = ? WHERE id = ?",
                retryPolicy.maxAttempts() - 1,
                requestId);
        stateService.claim(requestId);

        stateService.markTemporaryFailure(requestId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        ResumeReparseRequest request = reloadRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(ResumeReparseRequestStatus.FAILED);
        assertThat(request.getAttemptCount()).isEqualTo(retryPolicy.maxAttempts());
        assertThat(request.getErrorMessage()).isEqualTo(ResumeParsingErrorCode.LLM_RETRY_EXHAUSTED.formatted());
        assertThat(request.getNextAttemptAt()).isNull();
        assertThat(request.getFinishedAt()).isNotNull();
    }

    // Bien duoi: attempt_count = max-2 truoc lan loi -> van con luot, quay ve PENDING.
    @Test
    void markTemporaryFailure_secondToLastAttempt_stillPending() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        jdbcTemplate.update(
                "UPDATE resume_reparse_requests SET attempt_count = ? WHERE id = ?",
                retryPolicy.maxAttempts() - 2,
                requestId);
        stateService.claim(requestId);

        stateService.markTemporaryFailure(requestId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        assertThat(reloadRequest(requestId).getStatus()).isEqualTo(ResumeReparseRequestStatus.PENDING);
    }

    // Stale-claim reaper: yeu cau ket o RUNNING qua nguong -> tra ve PENDING (ton mot luot thu).
    @Test
    void reapStaleClaims_runningPastThreshold_returnsToPending() {
        Seeded seeded = seedV1ResumeWithScoring();
        UUID requestId = createRequest(seeded.resumeId());
        stateService.claim(requestId);
        jdbcTemplate.update(
                "UPDATE resume_reparse_requests SET claimed_at = now() - interval '2 hours' WHERE id = ?", requestId);
        ResumeReparseScheduler scheduler =
                new ResumeReparseScheduler(requestRepository, orchestrator, stateService, 10, 60_000);

        scheduler.reapStaleClaims();

        ResumeReparseRequest request = reloadRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(ResumeReparseRequestStatus.PENDING);
        assertThat(request.getAttemptCount()).isEqualTo(1);
        assertThat(request.getErrorMessage()).isEqualTo(ResumeParsingErrorCode.STALE_CLAIM_TIMEOUT.formatted());
    }
}

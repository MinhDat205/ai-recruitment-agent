package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.cvimprovement.CvImprovementErrorCode;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
import org.springframework.transaction.annotation.Transactional;

// @Transactional o MUC METHOD cho MOI test - KHAC ResumeParsingOrchestratorTest/
// ScoreExplanationOrchestratorTest (hai file do KHONG @Transactional, chap nhan commit that vao DB
// Testcontainers dung chung). O day chon nghiem ngat hon: moi test tu rollback, khong commit vinh
// vien - orchestrator.processOne() TU NO khong co @Transactional nao (dung nguyen tac CLAUDE.md muc
// 3c cho production code that), nhung boc test bang @Transactional o MUC METHOD chi anh huong toi
// pham vi giao dich cua BAI TEST (moi loi goi repository con lai/claim/markDone deu JOIN vao dung
// mot transaction cua test, propagation REQUIRED mac dinh), khong lam thay doi cau truc production
// code - ChatModel bi mock nen khong co do tre giay thuc su can transaction ngan de tranh can pool
// (khac tinh huong that trong san xuat).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class CvImprovementOrchestratorTest {

    private static final String VALID_JSON =
            """
            {"missingKeywords": ["Docker"],
             "sectionSuggestions": [{"section": "Kỹ năng", "suggestion": "Bổ sung Docker vào mục kỹ năng"}],
             "learningPath": [{"topic": "Docker", "reason": "Nhiều tin tuyển dụng cùng lĩnh vực yêu cầu Docker"}]}
            """;

    private static final String INVALID_JSON = "day khong phai JSON hop le";

    @Autowired
    private CvImprovementOrchestrator orchestrator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private CvImprovementRequestRepository cvImprovementRequestRepository;

    @Autowired
    private CvImprovementSuggestionRepository cvImprovementSuggestionRepository;

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

    private UUID createResumeWithParsedData() {
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
        UUID resumeId = resumeRepository.save(resume).getId();

        ResumeParsedData parsedData = new ResumeParsedData();
        parsedData.setResumeId(resumeId);
        parsedData.setRawText("CV goc gia lap");
        parsedData.setData(new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java", "Spring Boot"),
                List.of(),
                List.of()));
        parsedData.setModel("claude-sonnet-4-6");
        parsedData.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.save(parsedData);

        return resumeId;
    }

    private UUID createRequest(UUID resumeId, CvImprovementRequestStatus status) {
        CvImprovementRequest request = new CvImprovementRequest();
        request.setResumeId(resumeId);
        request.setStatus(status);
        return cvImprovementRequestRepository.save(request).getId();
    }

    // ---- Case duong ----

    @Test
    @Transactional
    void processOne_pendingRequest_generatesAndSavesSuggestion() {
        UUID resumeId = createResumeWithParsedData();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.PENDING);
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);

        // flush() TRUOC clear() - bat buoc: markDone() la @Transactional propagation REQUIRED, khi
        // test nay boc ca bai test bang @Transactional (xem comment dau class), markDone() chi THAM
        // GIA transaction cua test thay vi tu commit rieng - thay doi setStatus(DONE) moi chi nam
        // trong bo nho Hibernate, CHUA duoc flush xuong DB. Neu goi clear() ngay (khong flush truoc),
        // Hibernate XOA thay doi chua flush do truoc khi kip ghi, khien findById() sau do doc lai gia
        // tri CU (RUNNING) - loi da bat duoc thuc te khi chay test nay lan dau.
        entityManager.flush();
        entityManager.clear();
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CvImprovementRequestStatus.DONE);
        assertThat(request.getFinishedAt()).isNotNull();

        CvImprovementSuggestion suggestion = cvImprovementSuggestionRepository
                .findFirstByResumeIdOrderByGeneratedAtDescIdDesc(resumeId)
                .orElseThrow();
        assertThat(suggestion.getMissingKeywords()).containsExactly("Docker");
        assertThat(suggestion.getSectionSuggestions()).hasSize(1);
        assertThat(suggestion.getSectionSuggestions().get(0).section()).isEqualTo("Kỹ năng");
        assertThat(suggestion.getLearningPath()).hasSize(1);
        assertThat(suggestion.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(suggestion.getPromptVersion()).isEqualTo("cv-improvement-v1");
    }

    // ---- Case am ----

    @Test
    @Transactional
    void processOne_llmFailsBothAttempts_marksRequestFailed() {
        UUID resumeId = createResumeWithParsedData();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.PENDING);
        doReturn(fakeResponse(INVALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);

        // flush() truoc clear() - xem giai thich chi tiet o processOne_pendingRequest_generatesAndSavesSuggestion.
        entityManager.flush();
        entityManager.clear();
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CvImprovementRequestStatus.FAILED);
        assertThat(request.getErrorMessage()).isEqualTo(CvImprovementErrorCode.LLM_INVALID_JSON.formatted());
        assertThat(request.getFinishedAt()).isNotNull();
        assertThat(cvImprovementSuggestionRepository.findFirstByResumeIdOrderByGeneratedAtDescIdDesc(resumeId))
                .isEmpty();
    }

    @Test
    @Transactional
    void processOne_calledTwiceAfterSuccess_secondCallIsNoOp() {
        UUID resumeId = createResumeWithParsedData();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.PENDING);
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);
        // flush() truoc clear() - xem giai thich chi tiet o processOne_pendingRequest_generatesAndSavesSuggestion.
        // Bat buoc o day CA giua hai lan goi: neu khong flush, DB van thay status=RUNNING (tu claim()
        // cua lan goi dau) luc lan goi thu hai claim() lai - lan hai VAN bi chan (RUNNING khac
        // PENDING) nhung la VI LY DO SAI (chua kip DONE, khong phai "da DONE nen khong claim lai"),
        // khien assertion cuoi cung "sai nhung nhin tuong dung".
        entityManager.flush();
        entityManager.clear();
        orchestrator.processOne(requestId);

        entityManager.flush();
        entityManager.clear();
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CvImprovementRequestStatus.DONE);
        // Lan goi thu hai bi claim() chan tu dau (status khong con PENDING) - chatModel chi duoc goi
        // DUNG 1 lan, cho lan processOne dau tien.
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    @Transactional
    void processOne_nonPendingRequest_doesNothing() {
        UUID resumeId = createResumeWithParsedData();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.RUNNING);

        orchestrator.processOne(requestId);

        entityManager.clear();
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CvImprovementRequestStatus.RUNNING);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ---- Xu huong thi truong rong ----

    @Test
    void buildMarketTrendText_noOpenJobs_returnsFixedDescriptionNotEmptyString() {
        String text = CvImprovementOrchestrator.buildMarketTrendText(List.of());

        assertThat(text).isEqualTo(CvImprovementOrchestrator.NO_OPEN_JOBS_TEXT);
        assertThat(text).isNotBlank();
    }

    // ---- Khong bo sot field khi render CV (loi phat hien qua test tay F2) ----

    @Test
    void buildResumeText_payloadWithAllFields_rendersEveryField() {
        ResumeParsedPayload payload = new ResumeParsedPayload(
                new ResumeParsedPayload.Contact(
                        "Nguyen Van A", "a@example.com", "0900000000", "123 Duong ABC, Q1", "linkedin.com/in/nguyenvana"),
                List.of(new ResumeParsedPayload.Education(
                        "Dai hoc Bach Khoa", "Ky su", "Cong nghe thong tin", "09/2016", "06/2020", 3.2)),
                List.of(new ResumeParsedPayload.Experience(
                        "Cong ty ABC", "Backend Developer", "07/2020", "Hien tai", "Phat trien API")),
                List.of("Java"),
                List.of(new ResumeParsedPayload.Certification("AWS Certified Developer", "Amazon", "03/2022")),
                List.of(new ResumeParsedPayload.Project(
                        "He thong dat ve", "Xay dung backend dat ve xe", List.of("Spring Boot", "PostgreSQL"))));

        String text = CvImprovementOrchestrator.buildResumeText(payload);

        // Test nay sinh ra tu loi phat hien khi test tay F2: LLM goi y "bo sung GPA" cho mot CV DA CO
        // GPA, vi buildResumeText luc do khong render startDate/endDate/gpa cua Education - LLM
        // khong thay nen tuong thieu (xem comment tren buildResumeText, production code). Khang dinh
        // DU CA 10 field tung bi bo sot truoc khi sua - Contact.phone/address/linkedin (3),
        // Education.startDate/endDate/gpa (3), Experience.startDate/endDate (2),
        // Certification.issueDate (1), Project.technologies (1). Bo sot field nao trong tuong lai
        // (vd them field moi vao ResumeParsedPayload ma quen dua vao buildResumeText) cung lam test
        // nay do ngay, khong phai doi den lan test tay ke tiep moi phat hien nhu lan nay.
        assertThat(text).contains("0900000000"); // Contact.phone
        assertThat(text).contains("123 Duong ABC, Q1"); // Contact.address
        assertThat(text).contains("linkedin.com/in/nguyenvana"); // Contact.linkedin
        assertThat(text).contains("09/2016"); // Education.startDate
        assertThat(text).contains("06/2020"); // Education.endDate
        assertThat(text).contains("3.2"); // Education.gpa
        assertThat(text).contains("07/2020"); // Experience.startDate
        assertThat(text).contains("Hien tai"); // Experience.endDate
        assertThat(text).contains("03/2022"); // Certification.issueDate
        assertThat(text).contains("Spring Boot").contains("PostgreSQL"); // Project.technologies

        // Chot chan co hoc: 10 assertion noi dung o tren KHONG bat duoc truong hop them field MOI
        // vao cac record long ma quen dua vao buildResumeText (field moi don gian la khong co gia
        // tri nao de kiem, nen khong assertion nao that bai). Dem SO LUONG record component buoc
        // nguoi them field moi phai chu dong sua CA HAI noi (them field vao buildResumeText VA cap
        // nhat con so o day) - neu chi sua ResumeParsedPayload ma quen buildResumeText, assertion
        // nay do ngay, khong phai doi lan test tay ke tiep.
        assertThat(ResumeParsedPayload.Contact.class.getRecordComponents()).hasSize(5);
        assertThat(ResumeParsedPayload.Education.class.getRecordComponents()).hasSize(6);
        assertThat(ResumeParsedPayload.Experience.class.getRecordComponents()).hasSize(5);
        assertThat(ResumeParsedPayload.Certification.class.getRecordComponents()).hasSize(3);
        assertThat(ResumeParsedPayload.Project.class.getRecordComponents()).hasSize(3);
    }

    // ---- Ranh gioi F2: khong ro ri du lieu cham diem vao prompt ----

    @Test
    @Transactional
    void processOne_promptSentToLlm_containsNoScoringData() {
        UUID resumeId = createResumeWithParsedData();
        UUID candidateId = resumeRepository.findById(resumeId).orElseThrow().getCandidateId();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.PENDING);

        String criterionName = "Kinh nghiem Docker";
        String reasoning = "Ung vien dat 4/5 diem";
        seedScoringDataForCandidate(candidateId, resumeId, criterionName, reasoning);

        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(requestId);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();

        String fullPromptText =
                capturedPrompt.getInstructions().stream().map(Message::getText).collect(Collectors.joining("\n"));
        String userMessageText = capturedPrompt.getInstructions().stream()
                .filter(m -> m instanceof UserMessage)
                .map(Message::getText)
                .collect(Collectors.joining("\n"));

        // Du lieu THAT (ten tieu chi, reasoning) khong duoc xuat hien O BAT KY DAU trong prompt gui
        // LLM - kiem tren TOAN VAN (ca system lan user).
        assertThat(fullPromptText).doesNotContain(criterionName);
        assertThat(fullPromptText).doesNotContain(reasoning);

        // "criterion"/"rubric"/"weight"/"total_score" CHI kiem tren USER message (phan du lieu CV +
        // xu huong thi truong da render qua buildResumeText/buildMarketTrendText), KHONG kiem tren
        // toan bo prompt: system message (cv-improvement-v1.st, da duyet o Dot 3) CO CHU DICH chua tu
        // "rubric" trong chinh cau chi dan cam ("You have NOT been given any score, rubric,
        // evaluation criteria...") - do la chi dan cam, khong phai du lieu ro ri. Kiem tren toan bo
        // prompt se lam test do ngay ca voi code dung.
        assertThat(userMessageText).doesNotContainIgnoringCase("criterion");
        assertThat(userMessageText).doesNotContainIgnoringCase("rubric");
        assertThat(userMessageText).doesNotContainIgnoringCase("weight");
        assertThat(userMessageText).doesNotContainIgnoringCase("total_score");
    }

    // Seed scoring_runs/criterion_scores THAT cho MOT application cua CUNG candidate - chung minh du
    // lieu nay TON TAI THAT trong DB nhung khong duoc doc boi CvImprovementOrchestrator (khong
    // ScoringRunRepository/CriterionScoreRepository nao duoc inject vao production code cua F2). Import
    // tu package scoring/ o FILE TEST nay la duoc phep - rang buoc "khong import scoring/" ap cho code
    // production (Plan Mode, rang buoc kien truc #1), khong ap cho test dang CHUNG MINH chinh rang
    // buoc do. Khong tao rubric rieng - criterionId de null, dung tien le CriterionScore.criterionId
    // co the null.
    private void seedScoringDataForCandidate(UUID candidateId, UUID resumeId, String criterionName, String reasoning) {
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
        criterionScore.setCriterionNameSnapshot(criterionName);
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(new BigDecimal("4.00"));
        criterionScore.setReasoning(reasoning);
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }
}

package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// KHONG @Transactional o class hay method nao - mau ResumeParsedDataEndpointTest/
// ApplicationOwnerControllerIntegrationTest: MockMvc chay qua DispatcherServlet that, moi
// @Transactional cua service (requestSuggestions/getSuggestions/CvImprovementOrchestrator.processOne)
// tu mo/commit rieng cho tung request neu KHONG co transaction bao ngoai tu test - boc them
// @Transactional o day se tai dien dung bug da gap o Dot 4 (ghi qua transaction JOIN khong tu flush,
// doc lai thay gia tri cu). Khong test nao o day dung truy van dem/khong-scope toan bang (khac lo
// ngai da sua o Dot 2), moi assertion deu scope theo dung resumeId cua chinh test do, nen du lieu
// cac test khac de lai (commit that) khong lam sai lech ket qua.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CvImprovementSuggestionEndpointTest {

    private static final String VALID_JSON =
            """
            {"missingKeywords": ["Docker"],
             "sectionSuggestions": [{"section": "Kỹ năng", "suggestion": "Bổ sung Docker vào mục kỹ năng"}],
             "learningPath": [{"topic": "Docker", "reason": "Nhiều tin tuyển dụng cùng lĩnh vực yêu cầu Docker"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

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
    private CvImprovementOrchestrator orchestrator;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private record CandidateAuth(String token, UUID candidateId) {
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private CandidateAuth registerAndLoginCandidate(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc.perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
        UUID candidateId = userRepository.findByEmail(email).orElseThrow().getId();
        return new CandidateAuth(token, candidateId);
    }

    private UUID createResumeWithParsedData(UUID candidateId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
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
                List.of("Java"),
                List.of(),
                List.of()));
        parsedData.setModel("claude-sonnet-4-6");
        parsedData.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.save(parsedData);

        return resumeId;
    }

    private UUID createResumeWithoutParsedData(UUID candidateId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.PENDING);
        return resumeRepository.save(resume).getId();
    }

    private void createSuggestion(UUID resumeId, List<String> missingKeywords) {
        CvImprovementSuggestion suggestion = new CvImprovementSuggestion();
        suggestion.setResumeId(resumeId);
        suggestion.setMissingKeywords(missingKeywords);
        suggestion.setSectionSuggestions(List.of());
        suggestion.setLearningPath(List.of());
        suggestion.setModel("claude-sonnet-4-6");
        suggestion.setPromptVersion("cv-improvement-v1");
        cvImprovementSuggestionRepository.save(suggestion);
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

    // Seed scoring_runs/criterion_scores THAT cho MOT application cua CUNG candidate - mau
    // CvImprovementOrchestratorTest.seedScoringDataForCandidate (Dot 4), nhan lai o day vi la private
    // method o file khac, khong tai su dung qua import duoc - trung lap fixture chap nhan duoc, cung
    // tinh than voi cac file test D2/D4 khac trong du an.
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

    // ---- POST /improvement-suggestions ----

    @Test
    void requestSuggestions_resumeNotParsed_returns404WithResumeParsedDataNotFoundCode() throws Exception {
        CandidateAuth auth = registerAndLoginCandidate("cand-notparsed");
        UUID resumeId = createResumeWithoutParsedData(auth.candidateId());

        mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("RESUME_PARSED_DATA_NOT_FOUND")));
    }

    @Test
    void requestSuggestions_ownedResumeParsedDone_createsPendingRequestReturns200() throws Exception {
        CandidateAuth auth = registerAndLoginCandidate("cand-request");
        UUID resumeId = createResumeWithParsedData(auth.candidateId());

        mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PENDING")));

        assertThat(cvImprovementRequestRepository.findFirstByResumeIdAndStatusInOrderByRequestedAtDescIdDesc(
                        resumeId, List.of(CvImprovementRequestStatus.PENDING, CvImprovementRequestStatus.RUNNING)))
                .isPresent();
    }

    @Test
    void requestSuggestions_byDifferentCandidate_returns404() throws Exception {
        CandidateAuth owner = registerAndLoginCandidate("cand-owner");
        UUID resumeId = createResumeWithParsedData(owner.candidateId());
        CandidateAuth other = registerAndLoginCandidate("cand-other");

        mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestSuggestions_alreadyHasSuggestion_returnsExistingWithoutCallingLlm() throws Exception {
        CandidateAuth auth = registerAndLoginCandidate("cand-existing");
        UUID resumeId = createResumeWithParsedData(auth.candidateId());
        createSuggestion(resumeId, List.of("Docker"));

        mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DONE")))
                .andExpect(content().string(containsString("Docker")));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ---- GET /improvement-suggestions ----

    @Test
    void getSuggestions_notRequestedYet_returnsNotRequestedStatus() throws Exception {
        CandidateAuth auth = registerAndLoginCandidate("cand-notreq");
        UUID resumeId = createResumeWithParsedData(auth.candidateId());

        mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOT_REQUESTED")));
    }

    @Test
    void getSuggestions_afterOrchestratorProcessed_returnsDoneWithPayload() throws Exception {
        CandidateAuth auth = registerAndLoginCandidate("cand-done");
        UUID resumeId = createResumeWithParsedData(auth.candidateId());
        CvImprovementRequest request = new CvImprovementRequest();
        request.setResumeId(resumeId);
        request.setStatus(CvImprovementRequestStatus.PENDING);
        UUID requestId = cvImprovementRequestRepository.save(request).getId();

        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));
        orchestrator.processOne(requestId);

        mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DONE")))
                .andExpect(content().string(containsString("Docker")));
    }

    // ---- Ranh gioi F2: response tra ve khong ro ri du lieu cham diem ----

    @Test
    void getSuggestions_responseBody_doesNotContainScoreOrRubricFields() throws Exception {
        CandidateAuth auth = registerAndLoginCandidate("cand-noleak");
        UUID resumeId = createResumeWithParsedData(auth.candidateId());
        String criterionName = "Kinh nghiem Docker that";
        String reasoning = "Ung vien dat 4/5 diem that";
        seedScoringDataForCandidate(auth.candidateId(), resumeId, criterionName, reasoning);

        CvImprovementRequest request = new CvImprovementRequest();
        request.setResumeId(resumeId);
        request.setStatus(CvImprovementRequestStatus.PENDING);
        UUID requestId = cvImprovementRequestRepository.save(request).getId();
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));
        orchestrator.processOne(requestId);

        MvcResult result = mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/improvement-suggestions")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(criterionName);
        assertThat(body).doesNotContain(reasoning);
        assertThat(body).doesNotContainIgnoringCase("score");
        assertThat(body).doesNotContainIgnoringCase("weight");
        assertThat(body).doesNotContainIgnoringCase("criterion");
        assertThat(body).doesNotContainIgnoringCase("rubric");
    }
}

package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.storage.StorageService;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ResumeParsingOrchestratorTest {

    private static final String VALID_JSON =
            """
            {
              "contact": {"fullName": "Nguyen Van A", "email": "a@example.com", "phone": null, "address": null, "linkedin": null},
              "education": [],
              "experience": [],
              "skills": ["Java"],
              "certifications": [],
              "projects": []
            }
            """;

    @Autowired
    private ResumeParsingOrchestrator orchestrator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private StorageService storageService;

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

    private UUID createCandidate() {
        User user = new User();
        user.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien Test");
        return userRepository.save(user).getId();
    }

    // Ghi that fixture vao StorageService (dung qua storageService.load() sau nay, giong cach
    // ResumeService.upload() da lam), roi tao ban ghi Resume PENDING tro toi file do.
    private UUID createResumeFromFixture(String fixtureName) throws IOException {
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/resumes/" + fixtureName)) {
            content = in.readAllBytes();
        }
        String filename = UUID.randomUUID() + ".pdf";
        storageService.store("resumes", filename, new ByteArrayInputStream(content));
        String key = "resumes/" + filename;

        Resume resume = new Resume();
        resume.setCandidateId(createCandidate());
        resume.setFileUrl(key);
        resume.setFileName(fixtureName);
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize((long) content.length);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.PENDING);
        return resumeRepository.save(resume).getId();
    }

    @Test
    void processOne_pendingResumeWithValidJson_marksDoneAndSavesData() throws IOException {
        UUID resumeId = createResumeFromFixture("cv-mot-cot.pdf");
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(resumeId);

        entityManager.clear();
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(resume.getParseStatus()).isEqualTo(ParseStatus.DONE);

        ResumeParsedData data = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();
        assertThat(data.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(data.getPromptVersion()).isEqualTo(ResumeParsingService.PROMPT_VERSION);
        assertThat(data.getTokenUsage()).isEqualTo(150);
        assertThat(data.getData().skills()).containsExactly("Java");
    }

    @Test
    void processOne_scannedPdfWithoutTextLayer_marksFailedExtractEmpty() throws IOException {
        UUID resumeId = createResumeFromFixture("cv-scan.pdf");

        orchestrator.processOne(resumeId);

        entityManager.clear();
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(resume.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(resume.getParseError()).startsWith("EXTRACT_EMPTY");
        assertThat(resumeParsedDataRepository.findByResumeId(resumeId)).isEmpty();
    }

    @Test
    void processOne_corruptPdf_marksFailedExtractCorrupt() throws IOException {
        UUID resumeId = createResumeFromFixture("cv-hong.pdf");

        orchestrator.processOne(resumeId);

        entityManager.clear();
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(resume.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(resume.getParseError()).startsWith("EXTRACT_CORRUPT");
        assertThat(resumeParsedDataRepository.findByResumeId(resumeId)).isEmpty();
    }

    @Test
    void processOne_llmReturnsInvalidJsonRepeatedly_marksFailedLlmInvalidJson() throws IOException {
        UUID resumeId = createResumeFromFixture("cv-mot-cot.pdf");
        doReturn(fakeResponse("khong phai JSON hop le")).when(chatModel).call(any(Prompt.class));

        orchestrator.processOne(resumeId);

        entityManager.clear();
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(resume.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(resume.getParseError()).startsWith("LLM_INVALID_JSON");
        assertThat(resumeParsedDataRepository.findByResumeId(resumeId)).isEmpty();
    }

    @Test
    void processOne_alreadyDoneResume_claimFailsAndDoesNotOverwrite() throws IOException {
        UUID resumeId = createResumeFromFixture("cv-mot-cot.pdf");
        doReturn(fakeResponse(VALID_JSON)).when(chatModel).call(any(Prompt.class));
        orchestrator.processOne(resumeId);
        entityManager.clear();
        ResumeParsedData firstData = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();

        // KHONG stub call() lan nay - neu claim() co bug cho phep xu ly lai, orchestrator se goi
        // LLM that va bi chan boi default-answer throw (LlmTestConfiguration), chung minh that su
        // claim() da ngan lai chu khong phai vi tinh co test khong phat hien duoc.
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();

        orchestrator.processOne(resumeId);

        entityManager.clear();
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(resume.getParseStatus()).isEqualTo(ParseStatus.DONE);
        ResumeParsedData dataAfterSecondCall = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();
        assertThat(dataAfterSecondCall.getParsedAt()).isEqualTo(firstData.getParsedAt());
    }

    // Chung minh luoi an toan trong processOne() (catch RuntimeException bao ngoai doProcess())
    // hoat dong that, khong chi doc thay hop ly. Tao Resume tro toi mot file KHONG TON TAI tren
    // dia - storageService.load() (LocalStorageService that, khong mock) se tra Optional.empty()
    // that su, kich hoat NoSuchElementException tu doProcess(), roi luoi an toan phai bat lai va
    // goi markFailed() - neu khong, resume nay se ket vinh vien o PROCESSING vi scheduler chi quet
    // PENDING.
    @Test
    void processOne_fileMissingFromStorage_marksFailedInsteadOfStuckProcessing() {
        Resume resume = new Resume();
        resume.setCandidateId(createCandidate());
        resume.setFileUrl("resumes/khong-ton-tai-" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv-khong-ton-tai.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.PENDING);
        UUID resumeId = resumeRepository.save(resume).getId();

        orchestrator.processOne(resumeId);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(resumeParsedDataRepository.findByResumeId(resumeId)).isEmpty();
    }

    // Context test co 2 bean cung kieu ChatModel: anthropicChatModel (that, tu
    // AnthropicChatModelConfig) va stubChatModel (@Primary, tu LlmTestConfiguration). Neu @Primary
    // vi ly do nao do khong thang, @Autowired ChatModel se lay nham bean that - moi test khac
    // trong file nay se AM THAM goi Anthropic that qua ANTHROPIC_API_KEY gia trong
    // application-test.yml, co the van "xanh" (vd fail voi loi 401 bi bat nham thanh
    // RuntimeException -> LLM_ERROR) MA KHONG AI BIET dang goi mang that. Assert truc tiep o day,
    // khong suy luan gian tiep tu viec cac test khac dang pass.
    @Test
    void chatModelBean_resolvesToPrimaryMock_notRealAnthropicChatModel() {
        assertThat(Mockito.mockingDetails(chatModel).isMock()).isTrue();
    }
}

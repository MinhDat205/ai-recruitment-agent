package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.anthropic.models.messages.StopReason;
import com.recruitment.ai.jobdraft.JobAiDraftService;
import com.recruitment.ai.sync.SyncAiCaller;
import com.recruitment.aigeneration.AiGenerationTokenCodec;
import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import com.recruitment.jobdraft.dto.JobAiDraftResponse;
import com.recruitment.jobdraft.dto.PartResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

// FR-H11 T22 (phan thoi gian - muc 12 L8) va T23 (R-K3H-4), KHONG Spring: facade + JobAiDraftService + SyncAiCaller THAT
// dung bang constructor, chi ChatModel la mock (moi lan goi cho 1 s roi tra JSON hop le cua dung phan). Phan transaction
// cua T22 nam o JobAiDraftPartFailureIntegrationTest (TransactionTemplate that).
class JobAiDraftFacadeConcurrencyTest {

    private static final String DESCRIPTION = "Cần tuyển lập trình viên Java tại Đà Nẵng, lương 15 - 20 triệu.";
    private static final String SECRET = "test-only-secret-khong-dung-that-toi-thieu-32-ky-tu";
    private static final long MODEL_DELAY_MS = 1000;
    private static final JobAiDraftRequest ALL_PARTS = new JobAiDraftRequest(
            DESCRIPTION, List.of(JobDraftPart.JOB, JobDraftPart.CRITERIA, JobDraftPart.QUESTIONS));

    private ThreadPoolExecutor k3Executor;

    @AfterEach
    void shutdown() {
        if (k3Executor != null) {
            k3Executor.shutdownNow();
        }
    }

    // Cung cau hinh AiSyncExecutorConfig.newExecutor (package-private cua ai/sync - K3 khong sua): luong thuong co
    // dinh, KHONG hang doi, het luong -> RejectedExecutionException.
    private static ThreadPoolExecutor k3Executor(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
                Thread.ofPlatform().daemon(true).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    private JobAiDraftFacade facade(ChatModel chatModel, int k3Threads, long attemptTimeoutMs) {
        k3Executor = k3Executor(k3Threads);
        SyncAiCaller caller = new SyncAiCaller(k3Executor, attemptTimeoutMs, "configured-model");
        JobAiDraftService service = new JobAiDraftService(ChatClient.builder(chatModel).build(),
                prompt(JobAiDraftService.JOB_PROMPT_VERSION), prompt(JobAiDraftService.CRITERIA_PROMPT_VERSION),
                prompt(JobAiDraftService.QUESTIONS_PROMPT_VERSION), caller);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        doReturn(Optional.of(new Company())).when(companyRepository).findByOwnerId(any());
        CatalogRegistry registry = new CatalogRegistry(
                List.of(new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of()),
                        new CatalogEntry("OTHER", "Ngành khác", List.of())),
                List.of(new CatalogEntry("DA_NANG", "Đà Nẵng", List.of())));
        Clock clock = Clock.systemUTC();
        return new JobAiDraftFacade(companyRepository, registry, service, new AiGenerationTokenCodec(SECRET, clock),
                clock);
    }

    private static Resource prompt(String promptVersion) {
        return new ClassPathResource("ai/prompt/" + promptVersion + ".st");
    }

    // Moi lan goi: cho 1 s roi tra JSON hop le cua phan doan duoc tu dong dau system message.
    private static ChatModel slowChatModel() throws IOException {
        String jobLine = firstLine(JobAiDraftService.JOB_PROMPT_VERSION);
        String criteriaLine = firstLine(JobAiDraftService.CRITERIA_PROMPT_VERSION);
        ChatModel chatModel = mock(ChatModel.class);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
        doAnswer(invocation -> {
                    Thread.sleep(MODEL_DELAY_MS);
                    String system = ((Prompt) invocation.getArgument(0)).getSystemMessage().getText();
                    String json = system.startsWith(jobLine)
                            ? "{\"title\":\"Lập trình viên Java\",\"description\":\"Phát triển API.\"}"
                            : system.startsWith(criteriaLine)
                                    ? "{\"criteria\":[{\"name\":\"Java\",\"description\":\"Số năm\"}]}"
                                    : "{\"questions\":[]}";
                    return response(json);
                })
                .when(chatModel)
                .call(any(Prompt.class));
        return chatModel;
    }

    private static String firstLine(String promptVersion) throws IOException {
        return prompt(promptVersion).getContentAsString(StandardCharsets.UTF_8).lines().findFirst().orElseThrow();
    }

    private static ChatResponse response(String json) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(json),
                        ChatGenerationMetadata.builder().finishReason(StopReason.END_TURN.toString()).build())))
                .metadata(ChatResponseMetadata.builder().model("claude-test").build())
                .build();
    }

    private static List<PartResult<?>> cells(JobAiDraftResponse response) {
        return Stream.<PartResult<?>>of(response.job(), response.criteria(), response.questions()).toList();
    }

    // T22 - ba phan song song: tong thoi gian ~ phan cham nhat (1 s), khong phai tong (>= 3 s). Do o tang facade, sau
    // mot lan goi khoi dong (L8).
    @Test
    void threePartsRunInParallel_underTwoAndHalfSeconds() throws Exception {
        ChatModel chatModel = slowChatModel();
        JobAiDraftFacade facade = facade(chatModel, 8, 15000);
        facade.generate(UUID.randomUUID(), ALL_PARTS);

        long start = System.nanoTime();
        JobAiDraftResponse response = facade.generate(UUID.randomUUID(), ALL_PARTS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(cells(response)).allSatisfy(cell -> assertThat(cell.status()).isEqualTo(PartStatus.SUCCEEDED));
        assertThat(elapsedMs).as("thoi gian J1 du 3 phan (ms)").isLessThan(2500);
        verify(chatModel, times(6)).call(any(Prompt.class));
    }

    // T23 - executor K3 chi 2 luong: 3 phan tranh 2 luong -> dung 2 SUCCEEDED, 1 FAILED AI_UNAVAILABLE; ChatModel goi
    // dung 2 lan (phan bi tu choi khong thu lai). Phan da nhan luong van chay va tra ket qua.
    @Test
    void k3ExecutorExhausted_onePartUnavailable_othersSucceed() throws Exception {
        ChatModel chatModel = slowChatModel();
        JobAiDraftFacade facade = facade(chatModel, 2, 15000);

        JobAiDraftResponse response = facade.generate(UUID.randomUUID(), ALL_PARTS);

        List<PartResult<?>> cells = cells(response);
        assertThat(cells).filteredOn(cell -> cell.status() == PartStatus.SUCCEEDED).hasSize(2)
                .allSatisfy(cell -> assertThat(cell.generationToken()).isNotBlank());
        assertThat(cells).filteredOn(cell -> cell.status() == PartStatus.FAILED).singleElement()
                .satisfies(cell -> {
                    assertThat(cell.error()).isEqualTo("AI_UNAVAILABLE");
                    assertThat(cell.message())
                            .isEqualTo("Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau.");
                    assertThat(cell.data()).isNull();
                    assertThat(cell.generationToken()).isNull();
                });
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}

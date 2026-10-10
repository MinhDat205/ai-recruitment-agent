package com.recruitment.ai.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.StopReason;
import com.recruitment.common.exception.AiSyncErrorCode;
import com.recruitment.common.exception.AiSyncFailedException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

// FR-C07 T14 (phan chung), T15, T16 - muc SyncAiCaller, KHONG Spring context, KHONG HTTP: khang dinh AiSyncErrorCode va
// so lan goi ChatModel. Anh xa sang 502/503/504 khang dinh o dot 4 qua A2 (T16b). ChatClient that dung tren ChatModel
// mock (ChatClient.builder(ChatModel)) - BeanOutputConverter/responseEntity chay that, chi loi goi model bi chan.
class SyncAiCallerTest {

    public record TestPayload(String text) {
    }

    private static final Resource PROMPT =
            new ByteArrayResource("Tra ve dung mot doi tuong JSON.\n{format}".getBytes(StandardCharsets.UTF_8));
    private static final String END_TURN = StopReason.END_TURN.toString();
    private static final String MAX_TOKENS = StopReason.MAX_TOKENS.toString();
    private static final Predicate<TestPayload> NOT_BLANK = p -> p.text() != null && !p.text().isBlank();

    private ChatModel chatModel;
    private ChatClient chatClient;
    private ThreadPoolExecutor executor;
    private SyncAiCaller caller;

    @BeforeEach
    void setUp() {
        chatModel = Mockito.mock(ChatModel.class);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
        chatClient = ChatClient.builder(chatModel).build();
        executor = AiSyncExecutorConfig.newExecutor(8);
        caller = new SyncAiCaller(executor, 2000, "configured-model");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static ChatResponse response(String text, String finishReason, String model) {
        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
        ChatResponseMetadata.Builder responseMetadata = ChatResponseMetadata.builder();
        if (model != null) {
            responseMetadata.model(model);
        }
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text), metadata)))
                .metadata(responseMetadata.build())
                .build();
    }

    private static ChatResponse ok(String text) {
        return response("{\"text\": \"" + text + "\"}", END_TURN, "claude-test");
    }

    private SyncAiResult<TestPayload> call(Predicate<TestPayload> validator) {
        return caller.call(chatClient, PROMPT, Map.of(), "noi dung nguoi dung", TestPayload.class, validator);
    }

    private static AiSyncErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (AiSyncFailedException e) {
            return e.errorCode();
        }
        throw new AssertionError("Mong doi AiSyncFailedException");
    }

    // ---- T14 - output ----

    @Test
    void call_validOutput_returnsEntityAndModel_withOneCall() {
        when(chatModel.call(any(Prompt.class))).thenReturn(ok("xin chao"));

        SyncAiResult<TestPayload> result = call(NOT_BLANK);

        assertThat(result.entity().text()).isEqualTo("xin chao");
        assertThat(result.model()).isEqualTo("claude-test");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void call_modelMissingInMetadata_fallsBackToConfiguredModel() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("{\"text\": \"a\"}", END_TURN, null));

        assertThat(call(NOT_BLANK).model()).isEqualTo("configured-model");
    }

    @Test
    void call_invalidJsonThenValid_retriesOnce_twoCalls() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("khong phai json", END_TURN, "claude-test"), ok("lan 2"));

        assertThat(call(NOT_BLANK).entity().text()).isEqualTo("lan 2");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void call_invalidJsonBothTimes_invalidOutput_twoCalls() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("{hong", END_TURN, "claude-test"));

        assertThat(codeOf(() -> call(NOT_BLANK))).isEqualTo(AiSyncErrorCode.AI_INVALID_OUTPUT);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // R-K3-9 - JSON van parse duoc nhung finishReason bao cham tran -> coi la hong, thu lai.
    @Test
    void call_maxTokensFirstEvenIfJsonParses_retries_returnsSecond() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"text\": \"bi cat\"}", MAX_TOKENS, "claude-test"), ok("lan 2"));

        assertThat(call(NOT_BLANK).entity().text()).isEqualTo("lan 2");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void call_maxTokensBothTimes_invalidOutput() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("{\"text\": \"bi cat\"}", MAX_TOKENS, "claude-test"));

        assertThat(codeOf(() -> call(NOT_BLANK))).isEqualTo(AiSyncErrorCode.AI_INVALID_OUTPUT);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void call_validatorRejectsFirstAcceptsSecond_retries() {
        when(chatModel.call(any(Prompt.class))).thenReturn(ok("  "), ok("lan 2"));

        assertThat(call(NOT_BLANK).entity().text()).isEqualTo("lan 2");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void call_validatorRejectsBoth_invalidOutput_twoCalls() {
        when(chatModel.call(any(Prompt.class))).thenReturn(ok("  "));

        assertThat(codeOf(() -> call(NOT_BLANK))).isEqualTo(AiSyncErrorCode.AI_INVALID_OUTPUT);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // ---- T15 - loi nha cung cap: KHONG thu lai o K3 (SDK da tu thu lai ngam) ----

    static Stream<RuntimeException> temporaryProviderErrors() {
        return Stream.of(
                new AnthropicIoException("mang"),
                new AnthropicRetryableException("tam thoi"),
                RateLimitException.builder()
                        .headers(Headers.builder().build())
                        .body(JsonValue.from(Map.of()))
                        .build(),
                InternalServerException.builder()
                        .statusCode(529)
                        .headers(Headers.builder().build())
                        .body(JsonValue.from(Map.of()))
                        .build());
    }

    @ParameterizedTest
    @MethodSource("temporaryProviderErrors")
    void call_temporaryProviderErrorFirst_unavailable_exactlyOneCall(RuntimeException error) {
        when(chatModel.call(any(Prompt.class))).thenThrow(error);

        assertThat(codeOf(() -> call(NOT_BLANK))).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void call_otherRuntimeErrorFirst_unavailable_exactlyOneCall_messageHidesCause() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("BI MAT noi dung loi goc"));

        assertThatThrownBy(() -> call(NOT_BLANK))
                .isInstanceOf(AiSyncFailedException.class)
                .satisfies(e -> {
                    AiSyncFailedException failed = (AiSyncFailedException) e;
                    assertThat(failed.errorCode()).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
                    assertThat(failed.getMessage()).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE.formatted());
                    assertThat(failed.getMessage()).doesNotContain("BI MAT");
                    assertThat(failed.errorCode().formatted()).doesNotContain("BI MAT");
                });
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void call_invalidOutputThenTemporaryError_unavailable_twoCalls() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{hong", END_TURN, "claude-test"))
                .thenThrow(new AnthropicIoException("mang"));

        assertThat(codeOf(() -> call(NOT_BLANK))).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // R-K3-5 - het luong (executor 1 luong dang bi chiem, khong hang doi) -> AI_UNAVAILABLE ngay, 0 lan goi model.
    @Test
    void call_executorExhausted_unavailable_zeroCalls() throws Exception {
        ThreadPoolExecutor singleThread = AiSyncExecutorConfig.newExecutor(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        try {
            singleThread.execute(() -> {
                occupied.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(occupied.await(2, TimeUnit.SECONDS)).isTrue();
            SyncAiCaller busyCaller = new SyncAiCaller(singleThread, 2000, "configured-model");

            assertThat(codeOf(() -> busyCaller.call(
                            chatClient, PROMPT, Map.of(), "noi dung", TestPayload.class, NOT_BLANK)))
                    .isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
            verify(chatModel, never()).call(any(Prompt.class));
        } finally {
            release.countDown();
            singleThread.shutdownNow();
        }
    }

    // ---- T16 - het gio: han 300 ms truyen qua constructor (L6), mock cho 2 s ----

    @Test
    void call_attemptExceedsTimeout_timeout_oneCall_returnsBeforeMockFinishes_andInterruptsWorker() throws Exception {
        SyncAiCaller fastTimeoutCaller = new SyncAiCaller(executor, 300, "configured-model");
        AtomicBoolean workerInterrupted = new AtomicBoolean(false);
        CountDownLatch workerDone = new CountDownLatch(1);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                workerInterrupted.set(true);
                throw e;
            } finally {
                workerDone.countDown();
            }
            return ok("qua muon");
        });

        long start = System.nanoTime();
        AiSyncErrorCode code = codeOf(() -> fastTimeoutCaller.call(
                chatClient, PROMPT, Map.of(), "noi dung", TestPayload.class, NOT_BLANK));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(code).isEqualTo(AiSyncErrorCode.AI_TIMEOUT);
        assertThat(elapsedMs).as("tra ve truoc khi mock cho xong 2 s").isLessThan(2000);
        verify(chatModel, times(1)).call(any(Prompt.class));
        // Future.cancel(true) ngat luong cua executor (R-K3-1) - mock thay InterruptedException.
        assertThat(workerDone.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(workerInterrupted).isTrue();
    }
}

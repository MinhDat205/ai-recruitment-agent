package com.recruitment.ai.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openai.errors.OpenAIIoException;
import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.ai.embedding.EmbeddingService;
import com.recruitment.common.exception.AiSyncErrorCode;
import com.recruitment.common.exception.AiSyncFailedException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

// FR-U13 T1 - muc SyncEmbeddingCaller, KHONG Spring context: EmbeddingModel mock (khong stub -> throw, khuon
// LlmTestConfiguration), EmbeddingService THAT (kiem 1536 chieu + vector 0 chay that), executor that tu
// AiSyncExecutorConfig.newExecutor. Khang dinh AiSyncErrorCode va SO LAN goi EmbeddingModel (khong thu lai).
class SyncEmbeddingCallerTest {

    private static final String MARKER = "DAU_VAO_BI_MAT_U13_7f3a";

    private EmbeddingModel embeddingModel;
    private EmbeddingService embeddingService;
    private ThreadPoolExecutor executor;
    private SyncEmbeddingCaller caller;

    @BeforeEach
    void setUp() {
        embeddingModel = Mockito.mock(EmbeddingModel.class, invocation -> {
            if ("toString".equals(invocation.getMethod().getName())) {
                return "embeddingModel(mocked)";
            }
            throw new IllegalStateException("EmbeddingModel bi goi ma chua duoc stub");
        });
        embeddingService = new EmbeddingService(embeddingModel, "configured-embedding-model");
        executor = AiSyncExecutorConfig.newExecutor(8);
        caller = new SyncEmbeddingCaller(embeddingService, executor, 2000, "configured-embedding-model");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static EmbeddingResponse response(float[] vector, String model) {
        return new EmbeddingResponse(
                List.of(new Embedding(vector, 0)), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

    private static float[] vector(int dimensions) {
        float[] v = new float[dimensions];
        if (dimensions > 0) {
            v[0] = 0.1f;
        }
        return v;
    }

    private static AiSyncErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (AiSyncFailedException e) {
            return e.errorCode();
        }
        throw new AssertionError("Mong doi AiSyncFailedException");
    }

    // ---- thanh cong ----

    @Test
    void embed_valid1536Vector_returnsVectorAndModel_withExactlyOneCallOnGivenText() {
        doReturn(response(vector(1536), "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        EmbeddingResult result = caller.embed("content marketing");

        assertThat(result.vector()).hasSize(1536);
        assertThat(result.model()).isEqualTo("text-embedding-3-small");
        verify(embeddingModel, times(1)).embedForResponse(List.of("content marketing"));
    }

    // ---- loi nha cung cap / loi khac: AI_UNAVAILABLE, DUNG 1 lan goi (khong thu lai) ----

    static Stream<RuntimeException> providerAndOtherErrors() {
        return Stream.of(new OpenAIIoException("mang"), new IllegalStateException("BI MAT noi dung loi goc"));
    }

    @ParameterizedTest
    @MethodSource("providerAndOtherErrors")
    void embed_modelThrows_unavailable_exactlyOneCall_messageHidesCause(RuntimeException error) {
        doThrow(error).when(embeddingModel).embedForResponse(anyList());

        assertThatThrownBy(() -> caller.embed("noi dung"))
                .isInstanceOf(AiSyncFailedException.class)
                .satisfies(e -> {
                    AiSyncFailedException failed = (AiSyncFailedException) e;
                    assertThat(failed.errorCode()).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
                    assertThat(failed.getMessage()).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE.formatted());
                    assertThat(failed.getMessage()).doesNotContain("BI MAT", "mang");
                });
        verify(embeddingModel, times(1)).embedForResponse(anyList());
    }

    @Test
    void embed_zeroVector_unavailable_exactlyOneCall() {
        doReturn(response(new float[1536], "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        assertThat(codeOf(() -> caller.embed("noi dung"))).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
        verify(embeddingModel, times(1)).embedForResponse(anyList());
    }

    // Sai so chieu: 3 (ca cua dac ta) va hai bien 1535/1537 quanh 1536.
    @ParameterizedTest
    @ValueSource(ints = {3, 1535, 1537})
    void embed_wrongDimensions_unavailable_exactlyOneCall(int dimensions) {
        doReturn(response(vector(dimensions), "text-embedding-3-small"))
                .when(embeddingModel)
                .embedForResponse(anyList());

        assertThat(codeOf(() -> caller.embed("noi dung"))).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
        verify(embeddingModel, times(1)).embedForResponse(anyList());
    }

    // ---- het han: 300 ms truyen qua constructor; mock CHO latch (khong ngu co dinh) ----

    @Test
    void embed_exceedsTimeout_timeout_oneCall_returnsEarly_andInterruptsWorker() throws Exception {
        SyncEmbeddingCaller fastTimeoutCaller =
                new SyncEmbeddingCaller(embeddingService, executor, 300, "configured-embedding-model");
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch workerDone = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            try {
                neverReleased.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                workerInterrupted.set(true);
                throw e;
            } finally {
                workerDone.countDown();
            }
            return response(vector(1536), "qua-muon");
        }).when(embeddingModel).embedForResponse(anyList());

        try {
            long start = System.nanoTime();
            AiSyncErrorCode code = codeOf(() -> fastTimeoutCaller.embed("noi dung"));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(code).isEqualTo(AiSyncErrorCode.AI_TIMEOUT);
            assertThat(elapsedMs).as("tra ve sau han 300 ms, truoc khi mock cho xong").isBetween(250L, 5000L);
            // Future.cancel(true) ngat luong cua executor - mock thay InterruptedException.
            assertThat(workerDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(workerInterrupted).isTrue();
            verify(embeddingModel, times(1)).embedForResponse(anyList());
        } finally {
            neverReleased.countDown();
        }
    }

    // ---- het luong: executor 1 luong dang bi chiem, khong hang doi -> AI_UNAVAILABLE ngay, 0 lan goi ----

    @Test
    void embed_executorExhausted_unavailable_zeroCalls() throws Exception {
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
            SyncEmbeddingCaller busyCaller =
                    new SyncEmbeddingCaller(embeddingService, singleThread, 2000, "configured-embedding-model");

            assertThat(codeOf(() -> busyCaller.embed("noi dung"))).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
            verify(embeddingModel, never()).embedForResponse(anyList());
        } finally {
            release.countDown();
            singleThread.shutdownNow();
        }
    }

    // ---- luong goi bi ngat trong luc cho -> AI_UNAVAILABLE, giu lai co ngat ----

    @Test
    void embed_callingThreadInterrupted_unavailable_keepsInterruptFlag() {
        CountDownLatch neverReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            neverReleased.await(10, TimeUnit.SECONDS);
            return response(vector(1536), "qua-muon");
        }).when(embeddingModel).embedForResponse(anyList());

        try {
            Thread.currentThread().interrupt();
            AiSyncErrorCode code = codeOf(() -> caller.embed("noi dung"));

            assertThat(code).isEqualTo(AiSyncErrorCode.AI_UNAVAILABLE);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            neverReleased.countDown();
        }
    }

    // ---- log: muc info/warn/error KHONG chua chuoi dau vao (R-E1.6, R-L2) ----

    @Test
    void embed_failures_logsAtInfoOrAboveNeverContainInputText() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger callerLogger = (Logger) LoggerFactory.getLogger(SyncEmbeddingCaller.class);
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(EmbeddingService.class);
        callerLogger.addAppender(appender);
        serviceLogger.addAppender(appender);
        try {
            doThrow(new OpenAIIoException("mang " + MARKER)).when(embeddingModel).embedForResponse(anyList());
            codeOf(() -> caller.embed("tim viec " + MARKER));
            doReturn(response(new float[1536], "text-embedding-3-small"))
                    .when(embeddingModel)
                    .embedForResponse(anyList());
            codeOf(() -> caller.embed("tim viec " + MARKER));
        } finally {
            callerLogger.detachAppender(appender);
            serviceLogger.detachAppender(appender);
        }

        List<ILoggingEvent> visible = appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
                .toList();
        // Doi chung duong: hai lan that bai deu da ghi warn cua SyncEmbeddingCaller.
        assertThat(visible)
                .filteredOn(event -> event.getLoggerName().equals(SyncEmbeddingCaller.class.getName()))
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("ma=AI_UNAVAILABLE"));
        assertThat(visible).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(MARKER);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}

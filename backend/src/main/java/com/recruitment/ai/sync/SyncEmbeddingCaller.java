package com.recruitment.ai.sync;

import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.ai.embedding.EmbeddingService;
import com.recruitment.common.exception.AiSyncErrorCode;
import com.recruitment.common.exception.AiSyncFailedException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// FR-U13 R-E1 - K3 mo rong cho embedding: goi EmbeddingService DONG BO trong request nguoi dung, cung rao chan voi
// SyncAiCaller (khong sua SyncAiCaller - N11). Khong biet gi ve nghiep vu, khong repository, khong luu gi.
//
// - Kiem transaction tren LUONG GOI truoc khi gui viec cho executor (cung ly do SyncAiCaller R-K3-4/L3).
// - EmbeddingService.embed da tu kiem 1536 chieu va vector 0 (b10) - khong cai lai o day.
// - Het han timeoutMs -> Future.cancel(true), AI_TIMEOUT. KHONG thu lai o bat ky nhanh nao: output la vector, khong co
//   khai niem "output hong" de thu lai; loi nha cung cap thi SDK OpenAI da tu thu lai ngam.
// - Moi loi khac (EmbeddingFailedException moi ma, RuntimeException, bi ngat, het luong) -> AI_UNAVAILABLE.
// - L13: cancel(true) khong cat duoc request OpenAI dang chan doc socket (timeout client 30 s) - luong executor co the
//   bi giu toi luc do; tai don -> het luong -> AI_UNAVAILABLE (cung ban chat K3 L1).
// - L12: executor dung bang AiSyncExecutorConfig.newExecutor (giu nguyen) nen ten luong trung tien to "ai-sync-" voi
//   executor cua SyncAiCaller - hai pool van tach biet, chi ten luong trong log/thread dump la giong nhau.
public class SyncEmbeddingCaller implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SyncEmbeddingCaller.class);

    private final EmbeddingService embeddingService;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final String configuredModel;

    public SyncEmbeddingCaller(
            EmbeddingService embeddingService, ExecutorService executor, long timeoutMs, String configuredModel) {
        this.embeddingService = embeddingService;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.configuredModel = configuredModel;
    }

    public EmbeddingResult embed(String text) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Khong duoc goi embedding dong bo (K3) khi dang co transaction - giu ket noi DB trong luc cho API");
        }

        Future<EmbeddingResult> future;
        try {
            future = executor.submit(() -> embeddingService.embed(text));
        } catch (RejectedExecutionException busy) {
            throw fail(AiSyncErrorCode.AI_UNAVAILABLE, busy);
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw fail(AiSyncErrorCode.AI_TIMEOUT, timeout);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw fail(AiSyncErrorCode.AI_UNAVAILABLE, interrupted);
        } catch (ExecutionException executionError) {
            throw fail(AiSyncErrorCode.AI_UNAVAILABLE, executionError.getCause());
        }
    }

    // R-E1.6 - warn CHI gom ma loi, model, ten lop exception (khuon SyncAiCaller.fail); KHONG BAO GIO ghi text dau vao.
    // Exception goc chi o debug.
    private AiSyncFailedException fail(AiSyncErrorCode errorCode, Throwable cause) {
        log.warn(
                "Goi embedding dong bo that bai: ma={}, model={}, loai={}",
                errorCode.name(),
                configuredModel,
                cause == null ? "-" : cause.getClass().getSimpleName());
        log.debug("Chi tiet loi goi embedding dong bo", cause);
        return new AiSyncFailedException(errorCode, cause);
    }

    // Executor do SyncEmbeddingCaller so huu (AiSyncExecutorConfig tao, khong khai thanh bean) - dong khi context tat.
    @Override
    public void close() {
        executor.shutdownNow();
    }
}

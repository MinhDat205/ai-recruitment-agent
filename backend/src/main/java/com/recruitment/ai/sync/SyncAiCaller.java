package com.recruitment.ai.sync;

import com.anthropic.models.messages.StopReason;
import com.recruitment.common.exception.AiSyncErrorCode;
import com.recruitment.common.exception.AiSyncFailedException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;

// FR-C07 K3 - goi AI DONG BO co gioi han, dung chung cho moi FR trong danh sach ngoai le cua CLAUDE.md muc 7. Khong
// biet gi ve nghiep vu: nhan ChatClient, prompt he thong + tham so, user message, kieu record ket qua, ham validate;
// tra ket qua + ten model. Khong repository, khong luu gi (R-K3-7).
//
// - R-K3-4/L3: kiem transaction tren LUONG GOI truoc khi gui viec cho executor - kiem ben trong ChatModel la vo nghia vi
//   loi goi chay tren luong cua executor, noi khong bao gio co transaction cua request.
// - R-K3-1: moi lan goi ChatModel toi da attemptTimeoutMs (15 s); het han -> Future.cancel(true), AI_TIMEOUT, KHONG
//   thu lai. Han nay bao ca cac lan SDK tu thu lai ngam (R-K3-3, khong tat duoc qua ChatModel - muc 0.c3).
// - R-K3-2/L1: thu lai TOI DA 1 lan, CHI khi output hong (JSON hong, validate hong, cham tran token). Loi nha cung cap
//   (tam thoi hay khac) -> AI_UNAVAILABLE ngay - SDK da tu thu lai, K3 khong thu chong len.
// - R-K3-5: executor rieng (AiSyncExecutorConfig), het luong -> AI_UNAVAILABLE ngay.
public class SyncAiCaller implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SyncAiCaller.class);

    // R-K3-9 - so voi DUNG ham AnthropicChatModel dung de doi StopReason sang finishReason (StopReason.toString(), xac
    // minh bang javap - muc 0.c7), khong so voi chuoi tu viet tay.
    private static final String MAX_TOKENS_FINISH_REASON = StopReason.MAX_TOKENS.toString();

    private final ExecutorService executor;
    private final long attemptTimeoutMs;
    private final String configuredModel;

    public SyncAiCaller(ExecutorService executor, long attemptTimeoutMs, String configuredModel) {
        this.executor = executor;
        this.attemptTimeoutMs = attemptTimeoutMs;
        this.configuredModel = configuredModel;
    }

    // systemParams KHONG can chua "format" - K3 tu them tu BeanOutputConverter. validator tra false khi output da parse
    // duoc nhung khong hop le theo quy tac cua FR goi (vd R-D6) -> coi nhu output hong, duoc thu lai 1 lan.
    public <T> SyncAiResult<T> call(
            ChatClient chatClient,
            Resource systemPrompt,
            Map<String, Object> systemParams,
            String userMessage,
            Class<T> outputType,
            Predicate<T> validator) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Khong duoc goi AI dong bo (K3) khi dang co transaction - giu ket noi DB trong luc cho LLM (R-K3-4)");
        }

        BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);
        Map<String, Object> params = new HashMap<>(systemParams);
        params.put("format", converter.getFormat());

        try {
            return attempt(chatClient, systemPrompt, params, userMessage, converter, validator);
        } catch (InvalidOutputException firstAttemptError) {
            log.debug("Output AI khong hop le o lan goi dau, thu lai lan 2", firstAttemptError.getCause());
        }
        try {
            return attempt(chatClient, systemPrompt, params, userMessage, converter, validator);
        } catch (InvalidOutputException secondAttemptError) {
            log.debug("Output AI van khong hop le o lan goi 2, dung lai", secondAttemptError.getCause());
            throw fail(AiSyncErrorCode.AI_INVALID_OUTPUT, secondAttemptError.getCause());
        }
    }

    private <T> SyncAiResult<T> attempt(
            ChatClient chatClient,
            Resource systemPrompt,
            Map<String, Object> params,
            String userMessage,
            BeanOutputConverter<T> converter,
            Predicate<T> validator) {
        Future<ResponseEntity<ChatResponse, T>> future;
        try {
            future = executor.submit(() -> chatClient
                    .prompt()
                    .system(spec -> spec.text(systemPrompt).params(params))
                    .user(userMessage)
                    .call()
                    .responseEntity(converter));
        } catch (RejectedExecutionException busy) {
            throw fail(AiSyncErrorCode.AI_UNAVAILABLE, busy);
        }

        ResponseEntity<ChatResponse, T> result;
        try {
            result = future.get(attemptTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            // Khong dung lai duoc request OkHttp dang chan doc socket, nhung chan cac lan SDK thu lai sau do (muc 12 L1).
            future.cancel(true);
            throw fail(AiSyncErrorCode.AI_TIMEOUT, timeout);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw fail(AiSyncErrorCode.AI_UNAVAILABLE, interrupted);
        } catch (ExecutionException executionError) {
            Throwable cause = executionError.getCause();
            if (cause instanceof JacksonException) {
                throw new InvalidOutputException(cause);
            }
            throw fail(AiSyncErrorCode.AI_UNAVAILABLE, cause);
        }

        if (isTruncated(result.response())) {
            throw new InvalidOutputException(null);
        }
        T entity = result.entity();
        if (entity == null || !validator.test(entity)) {
            throw new InvalidOutputException(null);
        }
        return new SyncAiResult<>(entity, modelOf(result.response()));
    }

    // R-K3-9 - cham tran token la output hong ke ca khi phan bi cat tinh co van parse duoc JSON. Khong co generation nao
    // thi cung khong co ket qua dung duoc -> coi la hong.
    private static boolean isTruncated(ChatResponse response) {
        Generation generation = response == null ? null : response.getResult();
        if (generation == null) {
            return true;
        }
        return MAX_TOKENS_FINISH_REASON.equals(generation.getMetadata().getFinishReason());
    }

    private String modelOf(ChatResponse response) {
        String model = response.getMetadata().getModel();
        return (model == null || model.isBlank()) ? configuredModel : model;
    }

    // R-K3-7 - warn CHI gom ma loi, model, ten lop exception; exception goc chi o debug.
    private AiSyncFailedException fail(AiSyncErrorCode errorCode, Throwable cause) {
        log.warn(
                "Goi AI dong bo that bai: ma={}, model={}, loai={}",
                errorCode.name(),
                configuredModel,
                cause == null ? "-" : cause.getClass().getSimpleName());
        log.debug("Chi tiet loi goi AI dong bo", cause);
        return new AiSyncFailedException(errorCode, cause);
    }

    // Executor do SyncAiCaller so huu (AiSyncExecutorConfig tao, khong khai thanh bean) - dong khi context tat.
    @Override
    public void close() {
        executor.shutdownNow();
    }

    // Tin hieu noi bo "output hong" - chi de chon nhanh thu lai, khong bao gio thoat ra ngoai SyncAiCaller.
    private static final class InvalidOutputException extends RuntimeException {
        InvalidOutputException(Throwable cause) {
            super(null, cause, false, false);
        }
    }
}

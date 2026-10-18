package com.recruitment.ai.sync;

import com.recruitment.ai.embedding.EmbeddingService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FR-C07 R-K3-5 - executor rieng cua K3: maxConcurrentCalls luong co dinh, luong THUONG (Thread.ofPlatform - L4: can
// "<= ~45 s chay ngam" suy tu bytecode dua tren luong thuong), KHONG hang doi (SynchronousQueue), het luong ->
// RejectedExecutionException (AbortPolicy) -> SyncAiCaller tra AI_UNAVAILABLE ngay.
//
// Executor KHONG khai thanh bean: ThreadPoolExecutor la mot java.util.concurrent.Executor, ma
// TaskExecutorConfigurations$OnExecutorCondition cua Spring Boot 4.1 co @ConditionalOnMissingBean(Executor.class) -
// khai bean Executor se tat applicationTaskExecutor cua ca ung dung (xac minh bang javap tren
// spring-boot-autoconfigure-4.1.0.jar). SyncAiCaller so huu executor va dong no khi context tat.
@Configuration
public class AiSyncExecutorConfig {

    @Bean(destroyMethod = "close")
    public SyncAiCaller syncAiCaller(
            @Value("${app.ai-sync.max-concurrent-calls}") int maxConcurrentCalls,
            @Value("${app.ai-sync.attempt-timeout-ms}") long attemptTimeoutMs,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String configuredModel) {
        return new SyncAiCaller(newExecutor(maxConcurrentCalls), attemptTimeoutMs, configuredModel);
    }

    // FR-U13 R-E2 - K3 embedding: executor RIENG (khong dung chung pool voi syncAiCaller), cung so luong, cung cach dung.
    @Bean(destroyMethod = "close")
    public SyncEmbeddingCaller syncEmbeddingCaller(
            EmbeddingService embeddingService,
            @Value("${app.ai-sync.max-concurrent-calls}") int maxConcurrentCalls,
            @Value("${app.ai-sync.embedding-timeout-ms}") long embeddingTimeoutMs,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String configuredModel) {
        return new SyncEmbeddingCaller(
                embeddingService, newExecutor(maxConcurrentCalls), embeddingTimeoutMs, configuredModel);
    }

    static ThreadPoolExecutor newExecutor(int maxConcurrentCalls) {
        return new ThreadPoolExecutor(
                maxConcurrentCalls,
                maxConcurrentCalls,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                Thread.ofPlatform().name("ai-sync-", 0).daemon(true).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}

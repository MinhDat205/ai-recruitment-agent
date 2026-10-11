package com.recruitment.ai.sync;

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

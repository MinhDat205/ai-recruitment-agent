package com.recruitment.ai.client;

import java.time.Duration;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Dot 4 (chore/hardening) - timeout tuong minh cho OpenAI embedding client (F1). KHAC
// AnthropicChatModelConfig: OpenAiEmbeddingAutoConfiguration KHONG bi loai (chi
// OpenAiChatAutoConfiguration bi loai, xem application.yml) nen tu dong lay bean
// OpenAiHttpClientBuilderCustomizer nay qua ObjectProvider - khong can tu khai OpenAiEmbeddingModel
// thu cong nhu ben Anthropic. Xac minh bang javap tren spring-ai-openai-2.0.0.jar va
// OpenAiEmbeddingAutoConfiguration.class that: constructor cua no nhan
// ObjectProvider<OpenAiHttpClientBuilderCustomizer>, va SpringAiOpenAiHttpClient.Builder co
// .timeout(java.time.Duration) - khong doan theo tutorial (CLAUDE.md muc 3b).
@Configuration
public class OpenAiEmbeddingClientConfig {

    @Bean
    public OpenAiHttpClientBuilderCustomizer openAiTimeoutCustomizer(
            @Value("${app.hardening.openai-timeout-ms:30000}") long timeoutMs) {
        return builder -> builder.timeout(Duration.ofMillis(timeoutMs));
    }
}

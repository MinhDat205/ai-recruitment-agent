package com.recruitment.ai.client;

import com.anthropic.models.messages.Model;
import java.time.Duration;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khai bao AnthropicChatModel bang code thay cho auto-configuration cua
 * spring-ai-starter-model-anthropic 2.0.0 (xem spring.autoconfigure.exclude trong
 * application.yml de biet ly do: auto-config goc bind loi vao ThinkingConfigParam).
 */
@Configuration
public class AnthropicChatModelConfig {

    // Dot 4 (chore/hardening) - timeout tuong minh, ap dung cho CA BON dich vu Anthropic (D1/D2/D4/F2
    // - resumeParsingChatClient/criterionScoringChatClient/scoreExplanationChatClient/
    // cvImprovementChatClient deu build tu CUNG ChatClient.Builder, cuoi cung deu dung CHUNG mot
    // AnthropicChatModel nay, nen chi can cau hinh o DUY NHAT MOT CHO nay). Xac minh bang javap tren
    // spring-ai-anthropic-2.0.0.jar that: AnthropicChatModel.Builder co
    // .httpClientBuilderCustomizer(AnthropicHttpClientBuilderCustomizer), va
    // SpringAiAnthropicHttpClient.Builder co .timeout(java.time.Duration) - khong doan theo
    // tutorial (CLAUDE.md muc 3b).
    @Bean
    public AnthropicChatModel anthropicChatModel(
            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String model,
            @Value("${spring.ai.anthropic.chat.options.temperature:0.2}") Double temperature,
            @Value("${app.hardening.anthropic-timeout-ms:30000}") long timeoutMs) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Thieu bien moi truong ANTHROPIC_API_KEY");
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(apiKey)
                .model(Model.of(model))
                .temperature(temperature)
                .build();

        return AnthropicChatModel.builder()
                .options(options)
                .httpClientBuilderCustomizer(builder -> builder.timeout(Duration.ofMillis(timeoutMs)))
                .build();
    }
}

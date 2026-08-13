package com.recruitment.ai.client;

import com.anthropic.models.messages.Model;
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

    @Bean
    public AnthropicChatModel anthropicChatModel(
            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String model,
            @Value("${spring.ai.anthropic.chat.options.temperature:0.2}") Double temperature) {
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
                .build();
    }
}

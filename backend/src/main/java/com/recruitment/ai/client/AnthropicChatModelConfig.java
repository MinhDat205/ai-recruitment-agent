package com.recruitment.ai.client;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khai bao AnthropicChatModel bang code thay cho auto-configuration cua
 * spring-ai-starter-model-anthropic 2.0.0.
 *
 * Auto-configuration goc dang bind 'spring.ai.anthropic.chat.thinking' vao
 * com.anthropic.models.messages.ThinkingConfigParam (mot class cua Anthropic Java SDK,
 * khong phai POJO chuan Spring Boot doc duoc) -> InvalidConfigurationPropertyNameException
 * ngay khi khoi dong ung dung. Class autoconfiguration tuong ung da bi loai trong
 * application.yml (spring.autoconfigure.exclude) de tranh loi nay.
 */
@Configuration
public class AnthropicChatModelConfig {

    @Bean
    public AnthropicChatModel anthropicChatModel(ToolCallingManager toolCallingManager) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Thieu bien moi truong ANTHROPIC_API_KEY");
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(apiKey)
                .build();

        return AnthropicChatModel.builder()
                .options(options)
                .toolCallingManager(toolCallingManager)
                .build();
    }
}

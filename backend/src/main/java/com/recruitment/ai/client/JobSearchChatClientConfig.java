package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FR-U13 R-A3 - bean rieng cho tach dieu kien tim viec (khuon CvQaChatClientConfig). Tran token dau ra 500 dat o CHINH
// bean nay qua defaultOptions, KHONG dat cho AnthropicChatModel dung chung. ChatClient.Builder la bean scope prototype
// nen defaultOptions khong lan sang ChatClient cua tinh nang khac.
@Configuration
public class JobSearchChatClientConfig {

    @Bean
    public ChatClient jobSearchChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.job-search.max-output-tokens:500}") int maxOutputTokens) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder().maxTokens(maxOutputTokens))
                .build();
    }
}

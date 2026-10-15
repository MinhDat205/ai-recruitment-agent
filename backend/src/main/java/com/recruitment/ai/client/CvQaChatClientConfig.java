package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FR-C08 R-A4 - bean rieng cho hoi dap CV (mau MessageDraftChatClientConfig). Tran token dau ra 1000 dat o CHINH bean
// nay qua defaultOptions, KHONG dat cho AnthropicChatModel dung chung. ChatClient.Builder la bean scope prototype nen
// defaultOptions khong lan sang ChatClient cua tinh nang khac.
@Configuration
public class CvQaChatClientConfig {

    @Bean
    public ChatClient cvQaChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.cv-qa.max-output-tokens:1000}") int maxOutputTokens) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder().maxTokens(maxOutputTokens))
                .build();
    }
}

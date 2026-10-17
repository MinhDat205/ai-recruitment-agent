package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FR-U12 R-A7 - bean rieng cho goi y dien dat (khuon CvQaChatClientConfig). Tran token dau ra 800 dat o CHINH bean nay
// qua defaultOptions, KHONG dat cho AnthropicChatModel dung chung. ChatClient.Builder la bean scope prototype nen
// defaultOptions khong lan sang ChatClient cua tinh nang khac.
@Configuration
public class CvRephraseChatClientConfig {

    @Bean
    public ChatClient cvRephraseChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.cv-rephrase.max-output-tokens:800}") int maxOutputTokens) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder().maxTokens(maxOutputTokens))
                .build();
    }
}

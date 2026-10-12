package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FR-H11 R-K3H-1, muc 5.1 - bean rieng cho goi y tin tuyen dung, dung chung cho ca ba phan (JOB, CRITERIA, QUESTIONS).
// Tran token dau ra 800 dat o CHINH bean nay qua defaultOptions (mau MessageDraftChatClientConfig), KHONG dat cho
// AnthropicChatModel dung chung. ChatClient.Builder la bean scope prototype nen defaultOptions khong lan sang bean khac.
@Configuration
public class JobAiDraftChatClientConfig {

    @Bean
    public ChatClient jobAiDraftChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.job-ai-draft.max-output-tokens:800}") int maxOutputTokens) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder().maxTokens(maxOutputTokens))
                .build();
    }
}

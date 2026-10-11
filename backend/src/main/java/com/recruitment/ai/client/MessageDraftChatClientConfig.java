package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FR-C07 - bean rieng cho soan nhap tin nhan (mau CvImprovementChatClientConfig). Tran token dau ra (R-K3-9, muc 5) dat
// o CHINH bean nay qua defaultOptions, KHONG dat cho AnthropicChatModel dung chung - se cat output cua job nen
// D1/D2/D4/F2. ChatClient.Builder la bean scope prototype (ChatClientAutoConfiguration, javap) nen defaultOptions o day
// khong lan sang ChatClient cua tinh nang khac.
@Configuration
public class MessageDraftChatClientConfig {

    @Bean
    public ChatClient messageDraftChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.message-draft.max-output-tokens:800}") int maxOutputTokens) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder().maxTokens(maxOutputTokens))
                .build();
    }
}

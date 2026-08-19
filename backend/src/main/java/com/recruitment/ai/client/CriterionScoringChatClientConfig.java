package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Bean rieng cho scoring, KHONG tai dung resumeParsingChatClient - ten bean do gan ngu nghia
// resume, dung lai de gay hieu lam. Mau y het ResumeParsingChatClientConfig: ChatClient.Builder do
// Spring AI tu dong cung cap (ChatClientAutoConfiguration khong bi loai), lay AnthropicChatModel da
// khai thu cong (xem AnthropicChatModelConfig) lam nen tang.
@Configuration
public class CriterionScoringChatClientConfig {

    @Bean
    public ChatClient criterionScoringChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}

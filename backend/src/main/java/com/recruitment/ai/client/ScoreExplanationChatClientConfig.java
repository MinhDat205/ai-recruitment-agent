package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Bean rieng cho sinh bao cao giai thich, KHONG tai dung resumeParsingChatClient/
// criterionScoringChatClient - ten bean gan ngu nghia rieng cua tung nhanh, dung lai de gay hieu
// lam. Mau y het CriterionScoringChatClientConfig: ChatClient.Builder do Spring AI tu dong cung cap
// (ChatClientAutoConfiguration khong bi loai), lay AnthropicChatModel da khai thu cong (xem
// AnthropicChatModelConfig) lam nen tang.
@Configuration
public class ScoreExplanationChatClientConfig {

    @Bean
    public ChatClient scoreExplanationChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}

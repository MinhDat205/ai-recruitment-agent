package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Bean rieng cho sinh goi y cai thien CV, KHONG tai dung scoreExplanationChatClient/
// resumeParsingChatClient/criterionScoringChatClient - ten bean gan ngu nghia rieng cua tung nhanh,
// dung lai de gay hieu lam. Mau y het ScoreExplanationChatClientConfig.
@Configuration
public class CvImprovementChatClientConfig {

    @Bean
    public ChatClient cvImprovementChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}

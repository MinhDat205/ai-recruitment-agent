package com.recruitment.ai.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ChatClient.Builder duoc Spring AI tu dong cung cap qua ChatClientAutoConfiguration - autoconfig
// nay KHONG bi loai trong application.yml (chi AnthropicChatAutoConfiguration, tuc ChatModel bean,
// bi loai do bug bind ThinkingConfigParam - xem AnthropicChatModelConfig). Builder do tu dong lay
// AnthropicChatModel bean da khai thu cong lam ChatModel nen tang.
@Configuration
public class ResumeParsingChatClientConfig {

    @Bean
    public ChatClient resumeParsingChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}

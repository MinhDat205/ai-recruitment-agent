package com.recruitment.resume;

import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Chan goi LLM that o tang ChatModel (khong boc interface rieng) - ChatClient va
// BeanOutputConverter that van chay nguyen trong test, chi loi goi mang thuc su ra ngoai bi chan.
// Mac dinh THROW cho MOI method chua duoc stub, de test nao quen mock thi do ngay thay vi am tham
// goi Anthropic that.
@TestConfiguration(proxyBeanMethods = false)
public class LlmTestConfiguration {

    @Bean
    @Primary
    public ChatModel stubChatModel() {
        return Mockito.mock(ChatModel.class, invocation -> {
            // toString() KHONG duoc throw: Mockito tu xu ly rieng equals/hashCode nhung khong xu
            // ly rieng toString() - Spring log bean luc khoi tao context, hoac JUnit in mock ra
            // trong message cua MOT assertion that bai khac, se goi toString() va che mat loi that
            // dang debug.
            if ("toString".equals(invocation.getMethod().getName())) {
                return "stubChatModel(mocked, throws unless explicitly stubbed)";
            }
            throw new IllegalStateException(
                    "ChatModel bi goi trong test ma chua duoc stub - dung Mockito.when(...) truoc.");
        });
    }
}

package com.recruitment.resume;

import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Chan goi LLM/embedding that o tang ChatModel/EmbeddingModel (khong boc interface rieng) -
// ChatClient/BeanOutputConverter that (va o F1: EmbeddingService that) van chay nguyen trong test,
// chi loi goi mang thuc su ra ngoai bi chan. Mac dinh THROW cho MOI method chua duoc stub, de test
// nao quen mock thi do ngay thay vi am tham goi OpenAI/Anthropic that va ton tien.
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

    // Them o F1 (feat/fr-u04-recommend) - EmbeddingService (ai/embedding) tu day tro di la mot
    // consumer that cua EmbeddingModel trong context, cung ly do voi stubChatModel: khong stub thi
    // moi @SpringBootTest full-context se dung ben OpenAiEmbeddingModel that (khong loai qua
    // application.yml, xem Plan Mode F1) - chi an toan chung nao khong co code nao thuc su GOI no.
    // Tu F1 tro di co roi, nen phai chan giong het ChatModel.
    @Bean
    @Primary
    public EmbeddingModel stubEmbeddingModel() {
        return Mockito.mock(EmbeddingModel.class, invocation -> {
            if ("toString".equals(invocation.getMethod().getName())) {
                return "stubEmbeddingModel(mocked, throws unless explicitly stubbed)";
            }
            throw new IllegalStateException(
                    "EmbeddingModel bi goi trong test ma chua duoc stub - dung Mockito.when(...) truoc.");
        });
    }
}

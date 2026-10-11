package com.recruitment.ai.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.anthropic.models.messages.StopReason;
import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// FR-C07 T17 (R-K3-4, L3) - can transaction manager THAT nen dung Spring context (Testcontainers). SyncAiCaller lay tu
// context (bean cua AiSyncExecutorConfig, doc app.ai-sync.* cua application-test.yml) - kiem luon viec noi day.
// Khang dinh tren LUONG GOI: goi K3 trong TransactionTemplate -> IllegalStateException, 0 lan goi ChatModel.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
class SyncAiCallerTransactionIntegrationTest {

    public record TestPayload(String text) {
    }

    private static final Resource PROMPT =
            new ByteArrayResource("Tra ve dung mot doi tuong JSON.\n{format}".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private SyncAiCaller syncAiCaller;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private SyncAiResult<TestPayload> callK3() {
        return syncAiCaller.call(
                ChatClient.builder(chatModel).build(),
                PROMPT,
                Map.of(),
                "noi dung nguoi dung",
                TestPayload.class,
                p -> p.text() != null && !p.text().isBlank());
    }

    @Test
    void call_insideTransaction_throwsIllegalState_andNeverCallsChatModel() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> callK3()))
                .isInstanceOf(IllegalStateException.class);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void call_outsideTransaction_runsNormally_withOneCall() {
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(
                        new AssistantMessage("{\"text\": \"xin chao\"}"),
                        ChatGenerationMetadata.builder()
                                .finishReason(StopReason.END_TURN.toString())
                                .build())))
                .metadata(ChatResponseMetadata.builder().model("claude-test").build())
                .build();
        doReturn(response).when(chatModel).call(any(Prompt.class));

        SyncAiResult<TestPayload> result = callK3();

        assertThat(result.entity().text()).isEqualTo("xin chao");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }
}

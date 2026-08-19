package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ResumeParsingServiceIntegrationTest {

    private static final String VALID_JSON =
            """
            {
              "contact": {"fullName": "Nguyen Van A", "email": "a@example.com", "phone": "0900000000", "address": "Ha Noi", "linkedin": null},
              "education": [{"school": "DHBK Ha Noi", "degree": "Ky su", "major": "CNTT", "startDate": "09/2018", "endDate": "06/2022", "gpa": 3.2}],
              "experience": [{"company": "Cong ty ABC", "title": "Backend Developer", "startDate": "07/2022", "endDate": "Hien tai", "description": "Phat trien API"}],
              "skills": ["Java", "Spring Boot"],
              "certifications": [],
              "projects": []
            }
            """;

    private static final String INVALID_JSON = "day khong phai JSON hop le";

    @Autowired
    private ResumeParsingService resumeParsingService;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        // Reset ve trang thai "throw mac dinh" truoc moi test, roi stub lai NGAY 2 method
        // ChatClient can goi de dung request TRUOC khi toi call(): getOptions() VA
        // getDefaultOptions() - la HAI default method KHAC NHAU tren interface ChatModel.
        // DefaultChatClientUtils.toChatClientRequest() thuc te goi getOptions() (khong phai
        // getDefaultOptions()) - phat hien duoc bang cach chay test that va doc stack trace that,
        // khong doan truoc. Thieu stub method nao trong hai method nay deu throw sai cho (o buoc
        // dung request, khong phai o call() chua stub nhu y do that su cua test).
        //
        // QUAN TRONG: dung doReturn(...).when(mock).method(), KHONG dung when(mock.method())
        // .thenReturn(...). Voi when(mock.method()), Java phai evaluate mock.method() TRUOC de co
        // gia tri lam tham so cho when() - loi nay di qua chinh answer mac dinh (throw) cua mock,
        // nen ban than cau lenh stub cung throw truoc khi kip goi .thenReturn(). doReturn(...)
        // .when(mock) dat mockingProgress vao che do stubbing TRUOC khi goi .method(), nen loi goi
        // .method() sau do khong invoke answer that - chi ghi nhan de gan gia tri tra ve.
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private ChatResponse fakeResponse(String text, String model, Integer promptTokens, Integer completionTokens) {
        ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder();
        if (model != null) {
            metadataBuilder.model(model);
        }
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(metadataBuilder.build())
                .build();
    }

    // Chot ha tang mock: voi getDefaultOptions() (o @BeforeEach) va call() da stub, ChatClient
    // dung duoc va goi thanh cong het duong ong (khong throw o buoc nao trong luc dung request).
    // Tach rieng khoi cac test nghiep vu parsing ben duoi - muc dich thuan la xac nhan ha tang mock
    // hoat dong dung TRUOC khi dung no cho cac test khac.
    @Test
    void chatClientBuildsAndCallsWithStubbedModel() {
        doReturn(fakeResponse(VALID_JSON, "claude-sonnet-4-6", 100, 50)).when(chatModel).call(any(Prompt.class));

        ResumeParsingResult result = resumeParsingService.parse(UUID.randomUUID(), "noi dung CV bat ky");

        assertThat(result).isNotNull();
    }

    @Test
    void parse_validJson_mapsPayloadAndMetadataCorrectly() {
        doReturn(fakeResponse(VALID_JSON, "claude-sonnet-4-6", 120, 80)).when(chatModel).call(any(Prompt.class));

        ResumeParsingResult result = resumeParsingService.parse(UUID.randomUUID(), "noi dung CV bat ky");

        assertThat(result.payload().contact().fullName()).isEqualTo("Nguyen Van A");
        assertThat(result.payload().education()).hasSize(1);
        assertThat(result.payload().skills()).containsExactly("Java", "Spring Boot");
        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(result.tokenUsage()).isEqualTo(200);
        assertThat(result.promptVersion()).isEqualTo(ResumeParsingService.PROMPT_VERSION);
    }

    @Test
    void parse_blankModelInMetadata_fallsBackToConfiguredModel() {
        doReturn(fakeResponse(VALID_JSON, "  ", 10, 10)).when(chatModel).call(any(Prompt.class));

        ResumeParsingResult result = resumeParsingService.parse(UUID.randomUUID(), "noi dung CV bat ky");

        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void parse_invalidJsonBothAttempts_retriesExactlyOnceThenFailsWithLlmInvalidJson() {
        doReturn(fakeResponse(INVALID_JSON, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        ResumeParsingFailedException exception = assertThrows(
                ResumeParsingFailedException.class,
                () -> resumeParsingService.parse(UUID.randomUUID(), "noi dung CV bat ky"));

        assertThat(exception.errorCode()).isEqualTo(ResumeParsingErrorCode.LLM_INVALID_JSON);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void parse_chatModelThrowsRuntimeException_mapsToLlmErrorWithoutLeakingOriginalMessage() {
        String secretOriginalMessage = "loi ket noi toi may chu Anthropic tai dia chi bi mat XYZ";
        doThrow(new RuntimeException(secretOriginalMessage)).when(chatModel).call(any(Prompt.class));

        ResumeParsingFailedException exception = assertThrows(
                ResumeParsingFailedException.class,
                () -> resumeParsingService.parse(UUID.randomUUID(), "noi dung CV bat ky"));

        assertThat(exception.errorCode()).isEqualTo(ResumeParsingErrorCode.LLM_ERROR);
        assertThat(exception.getMessage()).doesNotContain(secretOriginalMessage);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    // Chung minh co che mock rieng, KHONG di qua resumeParsingService.parse(): parse() catch MOI
    // RuntimeException tu callLlm() va boc lai thanh ResumeParsingFailedException(LLM_ERROR) -
    // day la hanh vi DUNG cua parse() (da kiem chung o test RuntimeException ben tren), nhung vi
    // vay no che mat IllegalStateException goc neu test qua parse(). Muc dich cua test nay la xac
    // nhan rieng ha tang mock (LlmTestConfiguration) - "method chua duoc stub thi throw ngay" -
    // nen goi thang chatModel.call(...), khong qua ResumeParsingService.
    @Test
    void chatModelCallNotStubbed_throwsIllegalStateExceptionFromDefaultAnswer() {
        assertThrows(IllegalStateException.class, () -> chatModel.call(new Prompt("noi dung bat ky")));
    }
}

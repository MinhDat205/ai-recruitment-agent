package com.recruitment.ai.cvimprovement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import java.util.List;
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

// LlmTestConfiguration khong duoc nhan ban vao package nay - class da la public, import thang tu
// package resume la du. Mau y het ScoreExplanationServiceIntegrationTest (ke hoach D4).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class CvImprovementServiceIntegrationTest {

    private static final String VALID_JSON =
            """
            {"missingKeywords": ["Docker", "Kubernetes"],
             "sectionSuggestions": [{"section": "Kỹ năng", "suggestion": "Bổ sung Docker, Kubernetes vào mục kỹ năng"}],
             "learningPath": [{"topic": "Docker", "reason": "Nhiều tin tuyển dụng cùng lĩnh vực yêu cầu Docker"}]}
            """;

    private static final String INVALID_JSON = "day khong phai JSON hop le";

    private static final String EMPTY_ALL_JSON =
            """
            {"missingKeywords": [], "sectionSuggestions": [], "learningPath": []}
            """;

    private static final String INVALID_SECTION_NAME_JSON =
            """
            {"missingKeywords": [], "sectionSuggestions": [{"section": "Muc khong ton tai", "suggestion": "Noi dung goi y"}], "learningPath": []}
            """;

    private static final String BLANK_SECTION_SUGGESTION_JSON =
            """
            {"missingKeywords": [], "sectionSuggestions": [{"section": "Kỹ năng", "suggestion": "   "}], "learningPath": []}
            """;

    private static final String BLANK_LEARNING_PATH_TOPIC_JSON =
            """
            {"missingKeywords": [], "sectionSuggestions": [], "learningPath": [{"topic": "  ", "reason": "Ly do hop le"}]}
            """;

    private static final String BLANK_MISSING_KEYWORD_JSON =
            """
            {"missingKeywords": ["  "], "sectionSuggestions": [], "learningPath": []}
            """;

    @Autowired
    private CvImprovementService cvImprovementService;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private ChatResponse fakeResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(ChatResponseMetadata.builder()
                        .model("claude-sonnet-4-6")
                        .usage(new DefaultUsage(200, 100))
                        .build())
                .build();
    }

    @Test
    void generate_invalidJsonFirstAttemptValidSecond_succeedsAfterExactlyTwoCalls() {
        doReturn(fakeResponse(INVALID_JSON))
                .doReturn(fakeResponse(VALID_JSON))
                .when(chatModel)
                .call(any(Prompt.class));

        CvImprovementResult result = cvImprovementService.generate("CV mau", "Tin tuyen dung mau");

        assertThat(result.payload().missingKeywords()).containsExactly("Docker", "Kubernetes");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generate_invalidJsonBothAttempts_retriesExactlyOnceThenFailsWithLlmInvalidJson() {
        doReturn(fakeResponse(INVALID_JSON)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.LLM_INVALID_JSON);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generate_allThreeListsEmpty_returnsEmptyResultAfterRetry() {
        doReturn(fakeResponse(EMPTY_ALL_JSON)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.EMPTY_RESULT);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generate_chatModelThrowsRuntimeException_mapsToLlmErrorWithoutRetry() {
        String secretOriginalMessage = "loi ket noi toi may chu Anthropic tai dia chi bi mat XYZ";
        doThrow(new RuntimeException(secretOriginalMessage)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.LLM_ERROR);
        assertThat(exception.getMessage()).doesNotContain(secretOriginalMessage);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void generate_sectionNotInAllowedSet_returnsInvalidSectionAfterRetry() {
        doReturn(fakeResponse(INVALID_SECTION_NAME_JSON)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.INVALID_SECTION);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generate_blankSectionSuggestionText_returnsInvalidSectionAfterRetry() {
        doReturn(fakeResponse(BLANK_SECTION_SUGGESTION_JSON)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.INVALID_SECTION);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generate_blankLearningPathTopic_returnsInvalidLearningPathItemAfterRetry() {
        doReturn(fakeResponse(BLANK_LEARNING_PATH_TOPIC_JSON)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.INVALID_LEARNING_PATH_ITEM);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // INVALID_MISSING_KEYWORD - ma loi TU THEM ngoai hai ma anh yeu cau (xem giai thich chon tach
    // rieng thay vi dung chung INVALID_SECTION o phan CvImprovementErrorCode) - can test de khong
    // phai ma chet, khong ai xac nhan hanh vi.
    @Test
    void generate_blankMissingKeyword_returnsInvalidMissingKeywordAfterRetry() {
        doReturn(fakeResponse(BLANK_MISSING_KEYWORD_JSON)).when(chatModel).call(any(Prompt.class));

        CvImprovementFailedException exception =
                assertThrows(CvImprovementFailedException.class, () -> cvImprovementService.generate("CV mau", "Tin tuyen dung mau"));

        assertThat(exception.errorCode()).isEqualTo(CvImprovementErrorCode.INVALID_MISSING_KEYWORD);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}

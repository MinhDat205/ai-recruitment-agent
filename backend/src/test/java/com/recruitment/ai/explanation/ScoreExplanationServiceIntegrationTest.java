package com.recruitment.ai.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.scoring.CriterionScoreExplanationInput;
import java.math.BigDecimal;
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
// package resume la du. Mau y het CriterionScoringServiceIntegrationTest (ke hoach D2).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ScoreExplanationServiceIntegrationTest {

    private static final String VALID_JSON =
            """
            {"summary": "Ứng viên có kinh nghiệm Java tốt nhưng còn thiếu chứng chỉ AWS",
             "strengths": [{"criterionName": "Kinh nghiem Java", "point": "Diem cao va nhan dinh tich cuc tu lot cham truoc"}],
             "weaknesses": [{"criterionName": "Chung chi AWS", "point": "Diem thap, khong tim thay bang chung lien quan"}]}
            """;

    private static final String CRITERION_NAME_MISMATCH_JSON =
            """
            {"summary": "Tom tat hop le", "strengths": [{"criterionName": "Ten tieu chi khong ton tai trong danh sach", "point": "..."}], "weaknesses": []}
            """;

    private static final String BLANK_SUMMARY_JSON = """
            {"summary": "   ", "strengths": [], "weaknesses": []}
            """;

    private static final String EMPTY_STRENGTHS_AND_WEAKNESSES_JSON =
            """
            {"summary": "Ứng viên có hồ sơ trung bình, không có điểm nào nổi bật rõ ràng hoặc yếu rõ ràng",
             "strengths": [], "weaknesses": []}
            """;

    private static final String INVALID_JSON = "day khong phai JSON hop le";

    @Autowired
    private ScoreExplanationService scoreExplanationService;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private List<CriterionScoreExplanationInput> twoCriteria() {
        return List.of(
                new CriterionScoreExplanationInput(
                        "Kinh nghiem Java", new BigDecimal("60.00"), 5, new BigDecimal("4.00"),
                        "Ung vien co 3 nam kinh nghiem Java trong CV"),
                new CriterionScoreExplanationInput(
                        "Chung chi AWS", new BigDecimal("40.00"), 5, new BigDecimal("0.00"),
                        "Khong tim thay thong tin lien quan toi chung chi AWS trong CV"));
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

    // ---- Case duong ----

    @Test
    void explain_validJson_mapsPayloadAndMetadataCorrectly() {
        doReturn(fakeResponse(VALID_JSON, "claude-sonnet-4-6", 300, 150)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationResult result = scoreExplanationService.explain(twoCriteria());

        assertThat(result.payload().summary())
                .isEqualTo("Ứng viên có kinh nghiệm Java tốt nhưng còn thiếu chứng chỉ AWS");
        assertThat(result.payload().strengths()).hasSize(1);
        assertThat(result.payload().strengths().get(0).criterionName()).isEqualTo("Kinh nghiem Java");
        assertThat(result.payload().weaknesses()).hasSize(1);
        assertThat(result.payload().weaknesses().get(0).criterionName()).isEqualTo("Chung chi AWS");
        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(result.tokenUsage()).isEqualTo(450);
        assertThat(result.promptVersion()).isEqualTo(ScoreExplanationService.PROMPT_VERSION);
    }

    @Test
    void explain_blankModelInMetadata_fallsBackToConfiguredModel() {
        doReturn(fakeResponse(VALID_JSON, "  ", 10, 10)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationResult result = scoreExplanationService.explain(twoCriteria());

        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void explain_emptyStrengthsAndWeaknesses_isValid() {
        doReturn(fakeResponse(EMPTY_STRENGTHS_AND_WEAKNESSES_JSON, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        ScoreExplanationResult result = scoreExplanationService.explain(twoCriteria());

        assertThat(result.payload().strengths()).isEmpty();
        assertThat(result.payload().weaknesses()).isEmpty();
    }

    @Test
    void explain_summaryWithVietnameseDiacritics_hasNoTechnicalBlockAtJavaLayer() {
        doReturn(fakeResponse(VALID_JSON, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationResult result = scoreExplanationService.explain(twoCriteria());

        assertThat(result.payload().summary())
                .isEqualTo("Ứng viên có kinh nghiệm Java tốt nhưng còn thiếu chứng chỉ AWS");
    }

    // ---- Case am ----

    @Test
    void explain_criterionNameNotInProvidedSet_returnsCriterionNameMismatchAfterRetry() {
        doReturn(fakeResponse(CRITERION_NAME_MISMATCH_JSON, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        ScoreExplanationFailedException exception = assertThrows(
                ScoreExplanationFailedException.class, () -> scoreExplanationService.explain(twoCriteria()));

        assertThat(exception.errorCode()).isEqualTo(ScoreExplanationErrorCode.CRITERION_NAME_MISMATCH);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void explain_invalidJsonFirstAttemptValidSecond_succeedsAfterExactlyTwoCalls() {
        doReturn(fakeResponse(INVALID_JSON, "claude-sonnet-4-6", 10, 10))
                .doReturn(fakeResponse(VALID_JSON, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        ScoreExplanationResult result = scoreExplanationService.explain(twoCriteria());

        assertThat(result.payload().summary())
                .isEqualTo("Ứng viên có kinh nghiệm Java tốt nhưng còn thiếu chứng chỉ AWS");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void explain_invalidJsonBothAttempts_retriesExactlyOnceThenFailsWithLlmInvalidJson() {
        doReturn(fakeResponse(INVALID_JSON, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationFailedException exception = assertThrows(
                ScoreExplanationFailedException.class, () -> scoreExplanationService.explain(twoCriteria()));

        assertThat(exception.errorCode()).isEqualTo(ScoreExplanationErrorCode.LLM_INVALID_JSON);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void explain_blankSummary_returnsSummaryBlankAfterRetry() {
        doReturn(fakeResponse(BLANK_SUMMARY_JSON, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationFailedException exception = assertThrows(
                ScoreExplanationFailedException.class, () -> scoreExplanationService.explain(twoCriteria()));

        assertThat(exception.errorCode()).isEqualTo(ScoreExplanationErrorCode.SUMMARY_BLANK);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void explain_chatModelThrowsRuntimeException_mapsToLlmErrorWithoutRetry() {
        String secretOriginalMessage = "loi ket noi toi may chu Anthropic tai dia chi bi mat XYZ";
        doThrow(new RuntimeException(secretOriginalMessage)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationFailedException exception = assertThrows(
                ScoreExplanationFailedException.class, () -> scoreExplanationService.explain(twoCriteria()));

        assertThat(exception.errorCode()).isEqualTo(ScoreExplanationErrorCode.LLM_ERROR);
        assertThat(exception.getMessage()).doesNotContain(secretOriginalMessage);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void explain_weaknessCriterionNameNotInProvidedSet_returnsCriterionNameMismatch() {
        String json =
                """
                {"summary": "Tom tat hop le", "strengths": [], "weaknesses": [{"criterionName": "Khong ton tai", "point": "..."}]}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        ScoreExplanationFailedException exception = assertThrows(
                ScoreExplanationFailedException.class, () -> scoreExplanationService.explain(twoCriteria()));

        assertThat(exception.errorCode()).isEqualTo(ScoreExplanationErrorCode.CRITERION_NAME_MISMATCH);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}

package com.recruitment.ai.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.rubric.ScaleLevelDescription;
import com.recruitment.scoring.RubricSnapshot;
import java.math.BigDecimal;
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

// LlmTestConfiguration khong duoc nhan ban vao package nay - class da la public, import thang tu
// package resume la du (xem ke hoach D2, muc "Diem tien quyet phai kiem o tang service": da xac
// nhan khong can to chuc lai gi ca).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class CriterionScoringServiceIntegrationTest {

    // Co y de rawText co whitespace "bay ba" (tab, nhieu space lien tiep) giua cac tu cua quote se
    // duoc LLM tra ve - kiem chung normalizeWhitespace() thuc su duoc ap dung ca hai phia (quote LAN
    // rawText) truoc khi so khop, khong chi mot phia.
    private static final String RAW_TEXT =
            "Kinh nghiem lam viec:\n"
                    + "- Trien khai he thong voi Docker\tva  Kubernetes tren AWS trong 2 nam.\n"
                    + "- Có 3 năm kinh nghiệm triển khai hệ thống backend cho công ty ABC.\n"
                    + "Ky nang: Java, Spring Boot, Docker.\n";

    private static final String VALID_JSON_WITH_EVIDENCE =
            """
            {"score": 4, "reasoning": "Ung vien co de cap Docker va Kubernetes trong kinh nghiem lam viec",
             "evidence": [{"quote": "Trien khai he thong voi Docker va Kubernetes tren AWS trong 2 nam.", "section": "experience"}]}
            """;

    private static final String ZERO_SCORE_EMPTY_EVIDENCE_JSON =
            """
            {"score": 0, "reasoning": "Khong tim thay thong tin lien quan toi tieu chi nay trong CV", "evidence": []}
            """;

    private static final String VIETNAMESE_QUOTE_JSON =
            """
            {"score": 3, "reasoning": "Co de cap kinh nghiem trien khai he thong backend",
             "evidence": [{"quote": "Có 3 năm kinh nghiệm triển khai hệ thống backend cho công ty ABC.", "section": "experience"}]}
            """;

    private static final String INVALID_JSON = "day khong phai JSON hop le";

    @Autowired
    private CriterionScoringService criterionScoringService;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private RubricSnapshot.CriterionSnapshot criterion(Integer maxScore, List<ScaleLevelDescription> scale) {
        return new RubricSnapshot.CriterionSnapshot(
                UUID.randomUUID(), "Kinh nghiem Docker", "Danh gia muc do thanh thao Docker", new BigDecimal("40"),
                maxScore, scale);
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
    void score_validJson_mapsPayloadAndMetadataCorrectly() {
        doReturn(fakeResponse(VALID_JSON_WITH_EVIDENCE, "claude-sonnet-4-6", 120, 80))
                .when(chatModel)
                .call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.payload().score()).isEqualTo(4.0);
        assertThat(result.payload().evidence()).hasSize(1);
        assertThat(result.payload().evidence().get(0).section()).isEqualTo("experience");
        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(result.tokenUsage()).isEqualTo(200);
        assertThat(result.promptVersion()).isEqualTo(CriterionScoringService.PROMPT_VERSION);
    }

    @Test
    void score_blankModelInMetadata_fallsBackToConfiguredModel() {
        doReturn(fakeResponse(VALID_JSON_WITH_EVIDENCE, "  ", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void score_emptyEvidenceWithZeroScore_isValid() {
        doReturn(fakeResponse(ZERO_SCORE_EMPTY_EVIDENCE_JSON, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.payload().score()).isEqualTo(0.0);
        assertThat(result.payload().evidence()).isEmpty();
    }

    @Test
    void score_quoteMatchesAfterWhitespaceNormalization_isValid() {
        // RAW_TEXT co tab va nhieu space lien tiep giua cac tu, JSON tra ve quote voi khoang trang
        // don gian - phai khop sau khi chuan hoa whitespace CA HAI phia.
        doReturn(fakeResponse(VALID_JSON_WITH_EVIDENCE, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.payload().evidence().get(0).quote())
                .isEqualTo("Trien khai he thong voi Docker va Kubernetes tren AWS trong 2 nam.");
    }

    @Test
    void score_vietnameseQuoteWithDiacritics_matchesExactly() {
        doReturn(fakeResponse(VIETNAMESE_QUOTE_JSON, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.payload().evidence().get(0).quote())
                .isEqualTo("Có 3 năm kinh nghiệm triển khai hệ thống backend cho công ty ABC.");
    }

    // Prompt yeu cau "reasoning" viet tieng Viet (xem criterion-score-v1.st, "Evidence rules") -
    // day khong phai rang buoc ky thuat, chi la chi dan cho LLM. Test nay chung minh KHONG co
    // rang buoc ky thuat nao o tang Java (Jackson/BeanOutputConverter/validate()) tinh co chan
    // chuoi tieng Viet co dau trong reasoning - neu co, day() se throw hoac cat chuoi sai o day.
    @Test
    void score_reasoningInVietnameseWithDiacritics_hasNoTechnicalBlockAtJavaLayer() {
        String json =
                """
                {"score": 4, "reasoning": "Ứng viên có đề cập rõ ràng kinh nghiệm triển khai Docker và Kubernetes trong phần kinh nghiệm làm việc.",
                 "evidence": [{"quote": "Trien khai he thong voi Docker va Kubernetes tren AWS trong 2 nam.", "section": "experience"}]}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.payload().reasoning())
                .isEqualTo(
                        "Ứng viên có đề cập rõ ràng kinh nghiệm triển khai Docker và Kubernetes trong phần kinh nghiệm làm việc.");
    }

    @Test
    void score_criterionWithNullScaleDescription_stillScoresUsingDefaultScale() {
        doReturn(fakeResponse(VALID_JSON_WITH_EVIDENCE, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result).isNotNull();
        assertThat(result.payload().score()).isEqualTo(4.0);
    }

    // ---- Case am ----

    @Test
    void score_invalidJsonFirstAttemptValidSecond_succeedsAfterExactlyTwoCalls() {
        doReturn(fakeResponse(INVALID_JSON, "claude-sonnet-4-6", 10, 10))
                .doReturn(fakeResponse(VALID_JSON_WITH_EVIDENCE, "claude-sonnet-4-6", 10, 10))
                .when(chatModel)
                .call(any(Prompt.class));

        CriterionScoringResult result = criterionScoringService.score(criterion(5, null), RAW_TEXT);

        assertThat(result.payload().score()).isEqualTo(4.0);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_invalidJsonBothAttempts_retriesExactlyOnceThenFailsWithLlmInvalidJson() {
        doReturn(fakeResponse(INVALID_JSON, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.LLM_INVALID_JSON);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_scoreBelowZero_returnsScoreOutOfRangeAfterRetry() {
        String json = """
                {"score": -1, "reasoning": "khong hop le", "evidence": []}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.SCORE_OUT_OF_RANGE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_scoreAboveMaxScore_returnsScoreOutOfRangeAfterRetry() {
        // maxScore = 5, score = 6 = maxScore + 1.
        String json = """
                {"score": 6, "reasoning": "khong hop le", "evidence": []}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.SCORE_OUT_OF_RANGE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_emptyEvidenceWithNonzeroScore_returnsEvidenceMissingAfterRetry() {
        String json = """
                {"score": 2, "reasoning": "co kinh nghiem", "evidence": []}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.EVIDENCE_MISSING_WITH_NONZERO_SCORE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_evidenceWithInvalidSection_returnsEvidenceInvalidSectionAfterRetry() {
        String json =
                """
                {"score": 3, "reasoning": "co kinh nghiem",
                 "evidence": [{"quote": "Trien khai he thong voi Docker va Kubernetes tren AWS trong 2 nam.", "section": "education_history"}]}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.EVIDENCE_INVALID_SECTION);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_evidenceQuoteNotFoundInRawText_returnsEvidenceNotVerifiedAfterRetry() {
        String json =
                """
                {"score": 3, "reasoning": "co kinh nghiem",
                 "evidence": [{"quote": "Cau nay hoan toan khong ton tai trong CV nay", "section": "experience"}]}
                """;
        doReturn(fakeResponse(json, "claude-sonnet-4-6", 10, 10)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.EVIDENCE_NOT_VERIFIED);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void score_chatModelThrowsRuntimeException_mapsToLlmErrorWithoutRetry() {
        String secretOriginalMessage = "loi ket noi toi may chu Anthropic tai dia chi bi mat XYZ";
        doThrow(new RuntimeException(secretOriginalMessage)).when(chatModel).call(any(Prompt.class));

        CriterionScoringFailedException exception = assertThrows(
                CriterionScoringFailedException.class,
                () -> criterionScoringService.score(criterion(5, null), RAW_TEXT));

        assertThat(exception.errorCode()).isEqualTo(CriterionScoringErrorCode.LLM_ERROR);
        assertThat(exception.getMessage()).doesNotContain(secretOriginalMessage);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }
}

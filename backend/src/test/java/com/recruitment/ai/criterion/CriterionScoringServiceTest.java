package com.recruitment.ai.criterion;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.NotFoundException;
import com.recruitment.scoring.RubricSnapshot;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Test thuan Java, khong can Spring context - mau ResumeParsingServiceTest. truncateForPrompt()/
// normalizeWhitespace()/extractStatusCode() khong dung toi chatClient/promptResource/configuredModel
// nen truyen null la du (xem CriterionScoringServiceIntegrationTest cho cac test can Spring context
// va ChatModel mock).
class CriterionScoringServiceTest {

    private final CriterionScoringService service = new CriterionScoringService(null, null, null);

    @Test
    void truncateForPrompt_shortText_staysUnchanged() {
        String text = "a".repeat(100);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isEqualTo(text);
    }

    @Test
    void truncateForPrompt_longText_keepsFirstAndLastCharacterAndStaysWithinThreshold() {
        String text = "A" + "x".repeat(CriterionScoringService.MAX_PROMPT_CHARS * 2) + "Z";

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result.length()).isLessThanOrEqualTo(CriterionScoringService.MAX_PROMPT_CHARS);
        assertThat(result.charAt(0)).isEqualTo('A');
        assertThat(result.charAt(result.length() - 1)).isEqualTo('Z');
        assertThat(result).contains("[...phan giua CV da duoc luoc bot do do dai...]");
    }

    @Test
    void truncateForPrompt_lengthOneBelowThreshold_staysUnchanged() {
        String text = "a".repeat(CriterionScoringService.MAX_PROMPT_CHARS - 1);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isEqualTo(text);
    }

    @Test
    void truncateForPrompt_lengthExactlyAtThreshold_staysUnchanged() {
        String text = "a".repeat(CriterionScoringService.MAX_PROMPT_CHARS);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isEqualTo(text);
    }

    @Test
    void truncateForPrompt_lengthOneAboveThreshold_getsTruncated() {
        String text = "a".repeat(CriterionScoringService.MAX_PROMPT_CHARS + 1);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isNotEqualTo(text);
        assertThat(result.length()).isLessThanOrEqualTo(CriterionScoringService.MAX_PROMPT_CHARS);
        assertThat(result).contains("[...phan giua CV da duoc luoc bot do do dai...]");
    }

    @Test
    void extractStatusCode_notAnthropicServiceException_returnsNullWithoutThrowing() {
        RuntimeException networkError = new RuntimeException(new IOException("Connection refused"));

        Integer status = CriterionScoringService.extractStatusCode(networkError);

        assertThat(status).isNull();
    }

    @Test
    void extractStatusCode_anthropicServiceException_returnsRealHttpStatus() {
        NotFoundException notFound = NotFoundException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from(Map.of()))
                .build();

        Integer status = CriterionScoringService.extractStatusCode(notFound);

        assertThat(status).isEqualTo(404);
    }

    @Test
    void normalizeWhitespace_collapsesConsecutiveWhitespaceIncludingTabsAndNewlines_andTrims() {
        String messy = "  Docker\tva\n  Kubernetes  ";

        String result = CriterionScoringService.normalizeWhitespace(messy);

        assertThat(result).isEqualTo("Docker va Kubernetes");
    }

    @Test
    void normalizeWhitespace_doesNotStripVietnameseDiacritics() {
        String text = "Có 3 năm kinh nghiệm";

        String result = CriterionScoringService.normalizeWhitespace(text);

        assertThat(result).isEqualTo("Có 3 năm kinh nghiệm");
    }

    // Ranh gioi cua Dot 3 (ke hoach D2, Q3): input chi la rawText thuan, KHONG phai CV JSON co cau
    // truc (ResumeParsedPayload). Kiem bang chu ky method that, khong doan/mo ta suong.
    @Test
    void score_signatureTakesRawTextNotResumeParsedPayload() throws NoSuchMethodException {
        Method scoreMethod = CriterionScoringService.class.getMethod("score", RubricSnapshot.CriterionSnapshot.class, String.class);

        Class<?>[] paramTypes = scoreMethod.getParameterTypes();
        assertThat(paramTypes).containsExactly(RubricSnapshot.CriterionSnapshot.class, String.class);
        for (Class<?> paramType : paramTypes) {
            assertThat(paramType.getName()).doesNotContain("ResumeParsedPayload");
            assertThat(paramType.getPackageName()).doesNotStartWith("com.recruitment.resume");
        }
    }
}

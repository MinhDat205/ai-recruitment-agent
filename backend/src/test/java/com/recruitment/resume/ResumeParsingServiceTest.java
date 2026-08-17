package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.NotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Test thuan Java cho ham cat rawText - khong can Spring context. truncateForPrompt() khong dung
// toi chatClient/promptResource/fallbackModel nen truyen null la du, khong can dung Spring de tao
// cac dependency that (xem ResumeParsingServiceIntegrationTest cho cac test can Spring context).
class ResumeParsingServiceTest {

    private final ResumeParsingService service = new ResumeParsingService(null, null, null);

    @Test
    void truncateForPrompt_shortText_staysUnchanged() {
        String text = "a".repeat(100);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isEqualTo(text);
    }

    @Test
    void truncateForPrompt_longText_keepsFirstAndLastCharacterAndStaysWithinThreshold() {
        String text = "A" + "x".repeat(ResumeParsingService.MAX_PROMPT_CHARS * 2) + "Z";

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result.length()).isLessThanOrEqualTo(ResumeParsingService.MAX_PROMPT_CHARS);
        assertThat(result.charAt(0)).isEqualTo('A');
        assertThat(result.charAt(result.length() - 1)).isEqualTo('Z');
        assertThat(result).contains("[...phan giua CV da duoc luoc bot do do dai...]");
    }

    @Test
    void truncateForPrompt_lengthOneBelowThreshold_staysUnchanged() {
        String text = "a".repeat(ResumeParsingService.MAX_PROMPT_CHARS - 1);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isEqualTo(text);
    }

    @Test
    void truncateForPrompt_lengthExactlyAtThreshold_staysUnchanged() {
        String text = "a".repeat(ResumeParsingService.MAX_PROMPT_CHARS);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isEqualTo(text);
    }

    @Test
    void truncateForPrompt_lengthOneAboveThreshold_getsTruncated() {
        String text = "a".repeat(ResumeParsingService.MAX_PROMPT_CHARS + 1);

        String result = service.truncateForPrompt(text, UUID.randomUUID());

        assertThat(result).isNotEqualTo(text);
        assertThat(result.length()).isLessThanOrEqualTo(ResumeParsingService.MAX_PROMPT_CHARS);
        assertThat(result).contains("[...phan giua CV da duoc luoc bot do do dai...]");
    }

    // instanceof la pattern match an toan (khac ep kieu (AnthropicServiceException) e) - voi
    // RuntimeException/IOException-boc-lai KHONG phai AnthropicServiceException, no phai tra false
    // va roi xuong return null, KHONG duoc nem NPE hay ClassCastException. Day chinh la nhanh loi
    // mang thuan (connection refused, DNS fail, timeout socket) se di qua trong that te.
    @Test
    void extractStatusCode_notAnthropicServiceException_returnsNullWithoutThrowing() {
        RuntimeException networkError = new RuntimeException(new IOException("Connection refused"));

        Integer status = ResumeParsingService.extractStatusCode(networkError);

        assertThat(status).isNull();
    }

    @Test
    void extractStatusCode_anthropicServiceException_returnsRealHttpStatus() {
        NotFoundException notFound = NotFoundException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from(Map.of()))
                .build();

        Integer status = ResumeParsingService.extractStatusCode(notFound);

        assertThat(status).isEqualTo(404);
    }
}

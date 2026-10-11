package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.anthropic.errors.AnthropicIoException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.web.servlet.MvcResult;

// FR-C07 T16b - anh xa ma loi K3 sang HTTP qua A2 (GlobalExceptionHandler): 502 AI_INVALID_OUTPUT, 503 AI_UNAVAILABLE,
// 504 AI_TIMEOUT (han 2000 ms cua test profile - L6). Body loi DUNG {"error","message"} voi cau o UI.md muc 7, khong
// chua thong diep exception goc hay output tho. Kem: options cua loi goi co maxTokens = 800 (muc 5, R-K3-9).
class MessageDraftErrorMappingIntegrationTest extends MessageDraftIntegrationTestSupport {

    private static final String SECRET = "BI-MAT-KHONG-DUOC-LO-RA";

    @Test
    void invalidOutputTwice_returns502_twoCalls() throws Exception {
        Fixture f = createApplication("c07-t16b-502");
        doReturn(chatResponse("khong phai JSON " + SECRET)).when(chatModel).call(any(Prompt.class));

        MvcResult result = postDraft(Side.HR, f.applicationId(), f.hrToken(), "THANK_FOR_APPLYING", "FORMAL");

        assertAiError(result, 502, "AI_INVALID_OUTPUT", "AI trả về kết quả không hợp lệ, vui lòng thử lại.");
        capturedPrompts(2);
    }

    // R-D6 qua A2: draft chi khoang trang ca hai lan -> output hong -> 502.
    @Test
    void blankDraftTwice_returns502_twoCalls() throws Exception {
        Fixture f = createApplication("c07-t16b-blank");
        stubDraft("   ");

        MvcResult result = postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "ASK_PROGRESS", "FORMAL");

        assertAiError(result, 502, "AI_INVALID_OUTPUT", "AI trả về kết quả không hợp lệ, vui lòng thử lại.");
        capturedPrompts(2);
    }

    @Test
    void temporaryProviderErrorFirstCall_returns503_oneCall() throws Exception {
        Fixture f = createApplication("c07-t16b-503");
        doThrow(new AnthropicIoException(SECRET)).when(chatModel).call(any(Prompt.class));

        MvcResult result = postDraft(Side.HR, f.applicationId(), f.hrToken(), "THANK_FOR_APPLYING", "FORMAL");

        assertAiError(
                result, 503, "AI_UNAVAILABLE", "Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau.");
        capturedPrompts(1);
    }

    @Test
    void slowerThanAttemptTimeout_returns504_oneCall() throws Exception {
        Fixture f = createApplication("c07-t16b-504");
        doAnswer(invocation -> {
                    Thread.sleep(3000);
                    return chatResponse(draftJson(DEFAULT_DRAFT));
                })
                .when(chatModel)
                .call(any(Prompt.class));

        long start = System.nanoTime();
        MvcResult result = postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "ASK_PROGRESS", "FORMAL");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertAiError(result, 504, "AI_TIMEOUT", "AI phản hồi quá lâu, vui lòng thử lại.");
        assertThat(elapsedMs).isLessThan(3000);
        capturedPrompts(1);
    }

    @Test
    void successfulCall_sendsMaxTokens800InOptions() throws Exception {
        Fixture f = createApplication("c07-t16b-tokens");
        stubDraft(DEFAULT_DRAFT);

        assertDraft(postDraft(Side.HR, f.applicationId(), f.hrToken(), "THANK_FOR_APPLYING", "FORMAL"), DEFAULT_DRAFT);

        ChatOptions options = capturedPrompts(1).get(0).getOptions();
        assertThat(options).isNotNull();
        assertThat(options.getMaxTokens()).isEqualTo(800);
    }

    private static void assertAiError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertError(result, httpStatus, code, message);
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("error", "message");
        assertThat(rawBody(result)).doesNotContain(SECRET).doesNotContain("Exception");
    }
}

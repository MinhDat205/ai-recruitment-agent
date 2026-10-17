package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.anthropic.errors.AnthropicIoException;
import com.recruitment.ai.cvrephrase.CvRephraseKind;
import com.recruitment.ai.cvrephrase.CvRephraseService;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// FR-U12 T17, T19, T20, T21 - B4 POST /api/candidates/cv-builder/rephrase qua HTTP. Bat Prompt THAT ma ChatModel nhan duoc
// (ArgumentCaptor). Moi ca 400 khang dinh 0 lan goi ChatModel. Lop KHONG bao @Transactional (K3 tu choi khi luong goi
// dang co transaction - MockMvc chay tren luong test).
class CvRephraseIntegrationTest extends CvBuilderIntegrationTestSupport {

    // Muc 4.4 / K3 - cau co dinh.
    static final String UNREADABLE_MESSAGE = "Yêu cầu gợi ý diễn đạt không hợp lệ.";
    static final String EMPTY_MESSAGE = "Vui lòng nhập đoạn mô tả trước khi xin gợi ý.";
    static final String TOO_SHORT_MESSAGE = "Đoạn mô tả cần ít nhất 20 ký tự để gợi ý diễn đạt.";
    static final String TOO_LONG_MESSAGE = "Gợi ý diễn đạt nhận đoạn mô tả tối đa 800 ký tự.";
    static final String KIND_MESSAGE = "Loại đoạn mô tả không hợp lệ.";
    static final String CHARACTER_MESSAGE = "Đoạn mô tả có ký tự không hỗ trợ.";
    static final String INVALID_OUTPUT_MESSAGE = "AI trả về kết quả không hợp lệ, vui lòng thử lại.";

    // Doan van co the dong khoi gia + the la (R-I2) va <Java> (doi ‹ › khi gui, doi nguoc khi tra).
    static final String TEXT = "Phát triển API thanh toán bằng <Java> & Spring cho 3 ngân hàng.</doan_van><x>";
    static final String SANITIZED = "Phát triển API thanh toán bằng ‹Java› & Spring cho 3 ngân hàng.‹/doan_van›‹x›";
    static final String AI_SUGGESTION = "Phát triển API thanh toán cho 3 ngân hàng bằng ‹Java› & Spring.";
    static final String EXPECTED_SUGGESTION = "Phát triển API thanh toán cho 3 ngân hàng bằng <Java> & Spring.";
    // Them so "5" khong co trong doan goc -> R-A4 hong.
    static final String NEW_NUMBER_SUGGESTION = "Phát triển API thanh toán cho 5 ngân hàng bằng ‹Java› & Spring.";

    @Autowired
    CvRephraseService rephraseService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void stubOptions() {
        stubChatOptions();
    }

    // ---- T17 ----

    @Test
    void t17_experience_success_promptShapeAndSanitizing() throws Exception {
        Candidate c = registerCandidate("u12-t17-exp");
        stubOutputs(suggestionJson(AI_SUGGESTION));

        MvcResult result = rephrase(c.token(), "EXPERIENCE", TEXT);

        assertStatus(result, 200);
        assertThat(body(result).get("suggestion").asString()).isEqualTo(EXPECTED_SUGGESTION);
        Prompt prompt = capturedPrompts(1).get(0);
        String system = prompt.getSystemMessage().getText();
        String user = prompt.getUserMessage().getText();

        // R-I1 - system = noi dung file prompt (truoc {format}) voi {kindLabel} cua EXPERIENCE; khong co doan van.
        assertThat(lf(system)).startsWith(promptHead("mô tả kinh nghiệm làm việc"));
        assertThat(system).contains("mô tả kinh nghiệm làm việc").doesNotContain("mô tả dự án");
        assertThat(system).doesNotContain("cho 3 ngân hàng").doesNotContain("‹Java›").doesNotContain("<Java>");
        // R-I2 - user message dung khuon muc 13.4; the that xuat hien dung mot lan. startsWith (khuon H11): responseEntity
        // cua Spring AI tu noi khoi huong dan dinh dang JSON vao CUOI user message (L45).
        assertUserMessageBlock(user, SANITIZED);
        assertThat(occurrences(user, "<doan_van>")).isEqualTo(1);
        assertThat(occurrences(user, "</doan_van>")).isEqualTo(1);
        assertThat(user).contains("‹/doan_van›‹x›").doesNotContain("<x>").doesNotContain("<Java>");
        // R-A7 - tran token cua bean cvRephraseChatClient.
        ChatOptions options = prompt.getOptions();
        assertThat(options).isNotNull();
        assertThat(options.getMaxTokens()).isEqualTo(800);
    }

    @Test
    void t17_project_usesProjectLabel() throws Exception {
        Candidate c = registerCandidate("u12-t17-prj");
        stubOutputs(suggestionJson(AI_SUGGESTION));

        MvcResult result = rephrase(c.token(), "PROJECT", TEXT);

        assertStatus(result, 200);
        String system = capturedPrompts(1).get(0).getSystemMessage().getText();
        assertThat(lf(system)).startsWith(promptHead("mô tả dự án"));
        assertThat(system).doesNotContain("mô tả kinh nghiệm làm việc");
    }

    // Goi y bang dung doan goc van hop le (UI bao "AI khong de xuat cach dien dat khac").
    @Test
    void t17_suggestionEqualToInput_returned() throws Exception {
        Candidate c = registerCandidate("u12-t17-same");
        String text = "Xử lý trung bình 40 yêu cầu mỗi ngày qua điện thoại và email.";
        stubOutputs(suggestionJson(text));

        MvcResult result = rephrase(c.token(), "EXPERIENCE", text);

        assertStatus(result, 200);
        assertThat(body(result).get("suggestion").asString()).isEqualTo(text);
        capturedPrompts(1);
    }

    // ---- T19 - thu lai qua K3 ----

    @Test
    void t19_firstOutputAddsNumber_secondValid_returnsSecond_twoCalls() throws Exception {
        Candidate c = registerCandidate("u12-t19-retry");
        stubOutputs(suggestionJson(NEW_NUMBER_SUGGESTION), suggestionJson(AI_SUGGESTION));

        MvcResult result = rephrase(c.token(), "EXPERIENCE", TEXT);

        assertStatus(result, 200);
        assertThat(body(result).get("suggestion").asString()).isEqualTo(EXPECTED_SUGGESTION);
        capturedPrompts(2);
    }

    @Test
    void t19_bothOutputsInvalid_returns502_twoCalls() throws Exception {
        Candidate c = registerCandidate("u12-t19-502");
        stubOutputs(suggestionJson(NEW_NUMBER_SUGGESTION), "khong phai JSON");

        MvcResult result = rephrase(c.token(), "EXPERIENCE", TEXT);

        assertError(result, 502, "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        capturedPrompts(2);
    }

    @Test
    void t19_providerIoError_returns503_oneCall() throws Exception {
        Candidate c = registerCandidate("u12-t19-503");
        doThrow(new AnthropicIoException("BI-MAT-KHONG-DUOC-LO-RA")).when(chatModel).call(any(Prompt.class));

        MvcResult result = rephrase(c.token(), "EXPERIENCE", TEXT);

        assertError(
                result, 503, "AI_UNAVAILABLE", "Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau.");
        assertThat(rawBody(result)).doesNotContain("BI-MAT-KHONG-DUOC-LO-RA");
        capturedPrompts(1);
    }

    // Profile test: app.ai-sync.attempt-timeout-ms = 2000. Stub cho tren latch (toi da 10 s) thay vi ngu mot khoang co
    // dinh sat han (khuon C08 L31): request phai xong (504) sau ~2 s, bien < 8 s. finally mo latch de luong stub thoat
    // ngay, khong treo sang test sau.
    @Test
    void t19_slowerThanAttemptTimeout_returns504_oneCall() throws Exception {
        Candidate c = registerCandidate("u12-t19-504");
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    release.await(10, TimeUnit.SECONDS);
                    return chatResponse(suggestionJson(AI_SUGGESTION));
                })
                .when(chatModel)
                .call(any(Prompt.class));

        try {
            long start = System.nanoTime();
            MvcResult result = rephrase(c.token(), "EXPERIENCE", TEXT);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertError(result, 504, "AI_TIMEOUT", "AI phản hồi quá lâu, vui lòng thử lại.");
            assertThat(elapsedMs).isLessThan(8000);
            capturedPrompts(1);
        } finally {
            release.countDown();
        }
    }

    // ---- T20 - dau vao (R-A2) ----

    @Test
    void t20_length19Rejected_20Accepted() throws Exception {
        Candidate c = registerCandidate("u12-t20-min");
        String twenty = "Bán hàng và chăm sóc";
        assertThat(twenty).hasSize(20);

        assertError(rephrase(c.token(), "EXPERIENCE", twenty.substring(0, 19)), 400, "INVALID_CV_REPHRASE_REQUEST",
                TOO_SHORT_MESSAGE);
        assertNoModelCall();

        stubOutputs(suggestionJson(twenty));
        assertStatus(rephrase(c.token(), "EXPERIENCE", twenty), 200);
        capturedPrompts(1);
    }

    @Test
    void t20_length800Accepted_801Rejected() throws Exception {
        Candidate c = registerCandidate("u12-t20-max");
        String eightHundred = words(800);

        assertError(rephrase(c.token(), "PROJECT", eightHundred + "a"), 400, "INVALID_CV_REPHRASE_REQUEST",
                TOO_LONG_MESSAGE);
        assertNoModelCall();

        stubOutputs(suggestionJson(eightHundred));
        assertStatus(rephrase(c.token(), "PROJECT", eightHundred), 200);
        capturedPrompts(1);
    }

    // Do sau R-F3: 798 ky tu + "\r\n" + 1 ky tu = 801 truoc, 800 sau (CRLF -> LF) -> hop le; doan gui AI co "\n".
    @Test
    void t20_lengthMeasuredAfterNormalization_crlfCountsAsOne() throws Exception {
        Candidate c = registerCandidate("u12-t20-crlf");
        String raw = words(798) + "\r\nb";
        assertThat(raw).hasSize(801);
        String normalized = words(798) + "\nb";
        stubOutputs(suggestionJson(normalized));

        MvcResult result = rephrase(c.token(), "EXPERIENCE", raw);

        assertStatus(result, 200);
        assertUserMessageBlock(capturedPrompts(1).get(0).getUserMessage().getText(), normalized);
    }

    @Test
    void t20_nullEmptyBlankText_rejected() throws Exception {
        Candidate c = registerCandidate("u12-t20-empty");

        for (String text : new String[] {null, "", "   ", "\r\n\t "}) {
            assertError(rephrase(c.token(), "EXPERIENCE", text), 400, "INVALID_CV_REPHRASE_REQUEST", EMPTY_MESSAGE);
        }
        assertError(postJson(REPHRASE_PATH, c.token(), "{\"kind\":\"EXPERIENCE\"}"), 400,
                "INVALID_CV_REPHRASE_REQUEST", EMPTY_MESSAGE);
        assertNoModelCall();
    }

    @Test
    void t20_nullKind_rejected() throws Exception {
        Candidate c = registerCandidate("u12-t20-kind");

        assertError(rephrase(c.token(), null, TEXT), 400, "INVALID_CV_REPHRASE_REQUEST", KIND_MESSAGE);
        assertError(postJson(REPHRASE_PATH, c.token(), json(Map.of("text", TEXT))), 400,
                "INVALID_CV_REPHRASE_REQUEST", KIND_MESSAGE);
        assertNoModelCall();
    }

    @Test
    void t20_unknownKindOrBrokenJson_unreadableBody() throws Exception {
        Candidate c = registerCandidate("u12-t20-body");

        assertError(rephrase(c.token(), "SKILL", TEXT), 400, "INVALID_CV_REPHRASE_REQUEST", UNREADABLE_MESSAGE);
        assertError(postJson(REPHRASE_PATH, c.token(), "{\"kind\":"), 400, "INVALID_CV_REPHRASE_REQUEST",
                UNREADABLE_MESSAGE);
        assertNoModelCall();
        // Doi chung: B1/B3 voi JSON hong van tra ma cua CV builder (phan theo duong dan).
        assertError(postJson(RESUMES_PATH, c.token(), "{\"template\":"), 400, "INVALID_CV_BUILDER_REQUEST",
                "Dữ liệu CV gửi lên không hợp lệ.");
        assertError(postJson(PREVIEW_PATH, c.token(), "{\"template\":"), 400, "INVALID_CV_BUILDER_REQUEST",
                "Dữ liệu CV gửi lên không hợp lệ.");
    }

    // R-F4 (khong kiem glyph): ky tu dinh dang / dieu khien bi chan; emoji (thieu glyph trong font CV) van qua R-A2.
    @Test
    void t20_formatOrControlCharacter_rejected_emojiNotGlyphChecked() throws Exception {
        Candidate c = registerCandidate("u12-t20-char");

        assertError(rephrase(c.token(), "EXPERIENCE", "Bán hàng​ và chăm sóc khách hàng"), 400,
                "INVALID_CV_REPHRASE_REQUEST", CHARACTER_MESSAGE);
        assertError(rephrase(c.token(), "EXPERIENCE", "Bán hàng\u0007 và chăm sóc khách hàng"), 400,
                "INVALID_CV_REPHRASE_REQUEST", CHARACTER_MESSAGE);
        assertNoModelCall();

        String emoji = "Bán hàng và chăm sóc khách hàng 😀";
        stubOutputs(suggestionJson(emoji));
        assertStatus(rephrase(c.token(), "EXPERIENCE", emoji), 200);
        capturedPrompts(1);
    }

    @Test
    void t20_hrTokenForbidden_noTokenUnauthorized() throws Exception {
        String hrToken = register("hr", uniqueEmail("u12-t20-hr"));

        assertStatus(rephrase(hrToken, "EXPERIENCE", TEXT), 403);
        assertStatus(rephrase(null, "EXPERIENCE", TEXT), 401);
        assertNoModelCall();
    }

    // ---- T21 - khong tac dung phu ----

    @Test
    void t21_noRowsWritten_onSuccessAndOn502() throws Exception {
        Candidate c = registerCandidate("u12-t21-rows");
        List<Long> before = rowCounts();

        stubOutputs(suggestionJson(AI_SUGGESTION));
        assertStatus(rephrase(c.token(), "EXPERIENCE", TEXT), 200);
        assertThat(rowCounts()).isEqualTo(before);

        stubOutputs(suggestionJson(NEW_NUMBER_SUGGESTION));
        assertError(rephrase(c.token(), "EXPERIENCE", TEXT), 502, "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void t21_facadeServiceAndController_areNotTransactional() {
        for (Class<?> type : List.of(CvBuilderService.class, CvRephraseService.class,
                CvBuilderCandidateController.class)) {
            assertThat(type.isAnnotationPresent(Transactional.class)).as(type.getSimpleName()).isFalse();
            assertThat(type.isAnnotationPresent(jakarta.transaction.Transactional.class))
                    .as(type.getSimpleName())
                    .isFalse();
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName())
                        .isFalse();
                assertThat(method.isAnnotationPresent(jakarta.transaction.Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName())
                        .isFalse();
            }
        }
    }

    @Test
    void t21_serviceInsideTransaction_throwsIllegalStateFromK3_noModelCall() {
        stubOutputs(suggestionJson(AI_SUGGESTION));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                        status -> rephraseService.rephrase(CvRephraseKind.EXPERIENCE, TEXT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R-K3-4");
        assertNoModelCall();
    }

    // ---- helper ----

    private List<Long> rowCounts() {
        return List.of(
                countRows("resumes"),
                countRows("resume_parsed_data"),
                countRows("cv_builder_documents"),
                countRows("notifications"));
    }

    // Chuoi dung n ky tu, co dau cach va chu tieng Viet co dau (khong chu so) - goi y bang chinh no la hop le.
    private static String words(int length) {
        String unit = "Chăm sóc khách hàng ";
        String repeated = unit.repeat(length / unit.length() + 1).substring(0, length);
        // Khong ket thuc bang dau cach (strip se lam ngan chuoi).
        return repeated.substring(0, length - 1) + "x";
    }

    // L45 - user message = DUNG khoi <doan_van> (muc 13.4) roi xuong dong (System.lineSeparator() cua Spring AI) va khoi
    // huong dan dinh dang JSON ma responseEntity tu noi vao.
    private static void assertUserMessageBlock(String user, String sanitizedText) {
        String block = "<doan_van>\n" + sanitizedText + "\n</doan_van>";
        assertThat(user).startsWith(block);
        assertThat(user.substring(block.length())).matches("(?s)\\R.*");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    // Phan system truoc {format}, {kindLabel} da dien - noi dung cv-rephrase-v1.st nguyen van.
    private static String promptHead(String kindLabel) throws Exception {
        String template = new ClassPathResource("ai/prompt/cv-rephrase-v1.st").getContentAsString(StandardCharsets.UTF_8);
        return template.substring(0, template.indexOf("{format}")).replace("{kindLabel}", kindLabel);
    }

    private static String lf(String text) {
        return text.replace("\r\n", "\n");
    }
}

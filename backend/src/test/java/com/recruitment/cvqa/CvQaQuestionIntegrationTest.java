package com.recruitment.cvqa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// FR-C08 T7 (R-H1 qua HTTP, bien 1/499/500/501) va T8b (bo chan tu khoa R-G qua HTTP) - phia HR. Moi ca 400 va moi ca
// bi chan: 0 lan goi ChatModel.
class CvQaQuestionIntegrationTest extends CvQaIntegrationTestSupport {

    // ---- T7 ----

    @Test
    void t7_nullMissingEmptyOrBlankQuestion_returns400EmptyQuestion_noCall() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-empty", RAW_TEXT);

        // Ca cuoi: hai ky tu U+2003 (EM SPACE, viet bang escape JSON) - strip() coi la rong (L27).
        for (String body : List.of(
                "{\"question\": null}",
                "{}",
                "{\"question\": \"\"}",
                "{\"question\": \"   \"}",
                "{\"question\": \"\\u2003\\u2003\"}")) {
            assertError(
                    postRaw(f.applicationId(), f.hrToken(), body), 400, "INVALID_CV_QA_QUESTION", EMPTY_QUESTION_MESSAGE);
        }
        assertNoModelCall();
    }

    @Test
    void t7_lengths1And499And500_return200() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-ok", RAW_TEXT);
        for (int length : List.of(1, 499, 500)) {
            resetChatModelMock();
            stubOutputs(notFoundJson());

            assertOutcome(ask(f, "a".repeat(length)), "NOT_FOUND");

            String user = userText(capturedPrompts(1).get(0));
            assertThat(user).as("length %d", length).contains("<cau_hoi>\n" + "a".repeat(length) + "\n</cau_hoi>");
        }
    }

    @Test
    void t7_length501_returns400TooLong_noCall() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-501", RAW_TEXT);

        assertError(ask(f, "a".repeat(501)), 400, "INVALID_CV_QA_QUESTION", TOO_LONG_MESSAGE);
        assertNoModelCall();
    }

    // Do dai tinh SAU trim: 502 ky tu truoc trim, 500 sau trim -> hop le, cau gui AI da trim.
    @Test
    void t7_500CharsWithSurroundingSpaces_return200_trimmed() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-trim", RAW_TEXT);
        stubOutputs(notFoundJson());

        assertOutcome(ask(f, " " + "a".repeat(500) + " "), "NOT_FOUND");

        assertThat(userText(capturedPrompts(1).get(0))).contains("<cau_hoi>\n" + "a".repeat(500) + "\n</cau_hoi>");
    }

    // Do dai tinh SAU doi CRLF -> LF: 501 truoc doi, 500 sau doi -> hop le.
    @Test
    void t7_crlfCountedAsOneCharAfterNormalization_return200() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-crlf", RAW_TEXT);
        String question = "a".repeat(498) + "\r\n" + "b";
        assertThat(question).hasSize(501);
        stubOutputs(notFoundJson());

        assertOutcome(ask(f, question), "NOT_FOUND");

        String user = userText(capturedPrompts(1).get(0));
        assertThat(user).contains("<cau_hoi>\n" + "a".repeat(498) + "\nb\n</cau_hoi>");
        // Chi xet khoi cau hoi: phan schema JSON K3 noi vao cuoi user message (getFormat() cua BeanOutputConverter) ghi
        // xuong dong bang System.lineSeparator() - CRLF tren Windows, khong phai du lieu nguoi dung.
        String questionBlock = user.substring(user.indexOf("<cau_hoi>"), user.indexOf("</cau_hoi>"));
        assertThat(questionBlock).doesNotContain("\r");
    }

    @Test
    void t7_malformedJson_returns400InvalidRequest_noCall() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-json", RAW_TEXT);

        var result = postRaw(f.applicationId(), f.hrToken(), "{\"question\":");

        assertError(result, 400, "INVALID_CV_QA_REQUEST", UNREADABLE_MESSAGE);
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("error", "message");
        assertNoModelCall();
    }

    // R-Q6 - quyen (buoc 1) truoc cau hoi (buoc 2): cau hoi rong tren don cong ty khac van 403.
    @Test
    void t7_permissionCheckedBeforeQuestion() throws Exception {
        Fixture f = createApplicationWithText("c08-t7-order", RAW_TEXT);
        String otherHr = registerHrWithCompany("c08-t7-order2");

        assertStatus(postRaw(f.applicationId(), otherHr, "{\"question\": \"\"}"), 403);
        assertNoModelCall();
    }

    // ---- T8b ----

    @Test
    void t8b_personalInfoAndHiringJudgementPhrases_returnRefused_noCall() throws Exception {
        Fixture f = createApplicationWithText("c08-t8b", RAW_TEXT);

        assertRefused(ask(f, "Ứng viên bao nhiêu tuổi?"), "PERSONAL_INFO");
        assertRefused(ask(f, "Có nên tuyển ứng viên này không?"), "HIRING_JUDGEMENT");
        assertNoModelCall();
    }

    // R-Q6 - bo chan tu khoa (buoc 3) chay TRUOC K1 (buoc 4): CV chua co raw_text van 200 REFUSED, khong phai 409.
    @Test
    void t8b_resumeWithoutRawText_stillRefused_guardRunsBeforeContext() throws Exception {
        Fixture f = createApplication("c08-t8b-notext");

        assertRefused(ask(f, "Ứng viên bao nhiêu tuổi?"), "PERSONAL_INFO");
        assertRefused(ask(f, "Có nên tuyển ứng viên này không?"), "HIRING_JUDGEMENT");
        assertNoModelCall();
        // Doi chung: cau hoi binh thuong tren cung don thi 409 (K1 thieu van ban).
        assertError(ask(f, DEFAULT_QUESTION), 409, "CV_TEXT_NOT_AVAILABLE");
        assertNoModelCall();
    }

    // CV_IMPROVEMENT chi ap phia ung vien (R-G3): "nên học gì" phia HR di toi AI.
    @Test
    void t8b_cvImprovementPhraseOnHrSide_goesToAi() throws Exception {
        Fixture f = createApplicationWithText("c08-t8b-hr-improve", RAW_TEXT);
        stubOutputs(notFoundJson());

        assertOutcome(ask(f, "Ứng viên nên học gì tiếp theo?"), "NOT_FOUND");

        capturedPrompts(1);
    }
}

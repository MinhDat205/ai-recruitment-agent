package com.recruitment.cvqa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-C08 T18-T21 - phia ung vien qua HTTP (POST /api/candidates/resumes/{id}/cv-qa). Bat Prompt THAT ma ChatModel nhan
// duoc (ArgumentCaptor). Moi ca loi khang dinh 0 lan goi ChatModel.
class CvQaCandidateIntegrationTest extends CvQaIntegrationTestSupport {

    private static final String CANDIDATE_ANSWER = "Bạn có 3 năm phát triển ứng dụng Java tại Công ty ABC.";
    private static final String CANDIDATE_QUESTION = "CV của tôi ghi kinh nghiệm Java ở đâu?";

    // CV cua ung vien (khong can don): token + id CV.
    private record OwnResume(String token, UUID resumeId) {
    }

    private OwnResume createOwnResume(String prefix) throws Exception {
        String token = register("candidate", uniqueEmail(prefix + "-uv"));
        return new OwnResume(token, uploadResume(token));
    }

    private OwnResume createOwnResumeWithText(String prefix, String rawText) throws Exception {
        OwnResume own = createOwnResume(prefix);
        insertParsedData(own.resumeId(), rawText);
        return own;
    }

    private MvcResult ask(OwnResume own, String question) throws Exception {
        return askAsCandidate(own.resumeId(), own.token(), question);
    }

    // ---- T18 ----

    @Test
    void t18_answered_ownResume_systemSaysCandidate_noJobBlock() throws Exception {
        OwnResume own = createOwnResumeWithText("c08-t18", RAW_TEXT);
        stubOutputs(answeredJson(CANDIDATE_ANSWER, QUOTE_EXPERIENCE, QUOTE_SKILLS));

        JsonNode root = assertOutcome(ask(own, CANDIDATE_QUESTION), "ANSWERED");

        assertThat(root.get("answer").asString()).isEqualTo(CANDIDATE_ANSWER);
        assertThat(quotesOf(root)).containsExactly(QUOTE_EXPERIENCE, QUOTE_SKILLS);
        assertThat(root.get("refusalReason").isNull()).isTrue();
        assertThat(root.get("cvTruncated").asBoolean()).isFalse();

        Prompt prompt = capturedPrompts(1).get(0);
        String system = lf(systemText(prompt));
        String user = userText(prompt);
        assertThat(system)
                .contains("Người hỏi là ứng viên.")
                .contains(promptHead("ứng viên"))
                .doesNotContain("Người hỏi là nhà tuyển dụng.")
                .doesNotContain(CANDIDATE_QUESTION);
        assertThat(user)
                .contains("<cau_hoi>\n" + CANDIDATE_QUESTION + "\n</cau_hoi>")
                .contains("<cv>\n" + RAW_TEXT + "\n</cv>");
        for (String tag : List.of("<tin_tuyen_dung>", "<tieu_de>", "<mo_ta>", "<yeu_cau>")) {
            assertThat(user).as(tag).doesNotContain(tag);
        }
    }

    // S3 / R-G5 - cum "ngày sinh" KHONG bi chan phia ung vien: cau hoi di toi AI.
    @Test
    void t18_personalInfoPhraseOnCandidateSide_goesToAi_oneCall() throws Exception {
        OwnResume own = createOwnResumeWithText("c08-t18-dob", RAW_TEXT);
        String question = "CV của tôi có ghi ngày sinh không?";
        stubOutputs(notFoundJson());

        assertOutcome(ask(own, question), "NOT_FOUND");

        assertThat(userText(capturedPrompts(1).get(0))).contains("<cau_hoi>\n" + question + "\n</cau_hoi>");
    }

    // S3 - PERSONAL_INFO khong hop le phia ung vien: hai lan deu tra ma nay -> output hong ca hai lan -> 502.
    @Test
    void t18_aiRefusesPersonalInfoTwiceOnCandidateSide_returns502_twoCalls() throws Exception {
        OwnResume own = createOwnResumeWithText("c08-t18-502", RAW_TEXT);
        stubOutputs(refusedJson("PERSONAL_INFO"));

        MvcResult result = ask(own, "CV của tôi có ghi ngày sinh không?");

        assertError(result, 502, "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("error", "message");
        capturedPrompts(2);
    }

    // ---- T19 ----

    // a19 - CV nguoi khac va CV khong ton tai CUNG 404 RESUME_NOT_FOUND.
    @Test
    void t19_otherCandidatesResumeOrMissingResume_return404ResumeNotFound_noCall() throws Exception {
        OwnResume other = createOwnResumeWithText("c08-t19-other", RAW_TEXT);
        OwnResume me = createOwnResumeWithText("c08-t19-me", RAW_TEXT);

        assertError(askAsCandidate(other.resumeId(), me.token(), CANDIDATE_QUESTION), 404, "RESUME_NOT_FOUND");
        assertError(askAsCandidate(MISSING_ID, me.token(), CANDIDATE_QUESTION), 404, "RESUME_NOT_FOUND");
        assertNoModelCall();
        // Doi chung: chu CV hoi chinh CV do thi qua quyen.
        stubOutputs(notFoundJson());
        assertOutcome(ask(other, CANDIDATE_QUESTION), "NOT_FOUND");
        capturedPrompts(1);
    }

    // R-Q6 - quyen (buoc 1) truoc cau hoi (buoc 2) va truoc bo chan tu khoa (buoc 3).
    @Test
    void t19_permissionCheckedBeforeQuestionAndGuard() throws Exception {
        OwnResume other = createOwnResumeWithText("c08-t19-order", RAW_TEXT);
        OwnResume me = createOwnResume("c08-t19-order2");

        assertError(postRawAsCandidate(other.resumeId(), me.token(), "{\"question\": \"\"}"), 404, "RESUME_NOT_FOUND");
        assertError(askAsCandidate(other.resumeId(), me.token(), "Tôi nên học thêm gì?"), 404, "RESUME_NOT_FOUND");
        assertNoModelCall();
    }

    @Test
    void t19_hrToken_returns403_noToken_returns401_noCall() throws Exception {
        OwnResume own = createOwnResumeWithText("c08-t19-role", RAW_TEXT);
        String hr = registerHrWithCompany("c08-t19-role-hr");

        assertStatus(askAsCandidate(own.resumeId(), hr, CANDIDATE_QUESTION), 403);
        assertError(askAsCandidate(own.resumeId(), null, CANDIDATE_QUESTION), 401, "UNAUTHENTICATED");
        assertNoModelCall();
    }

    // R-Q3 - CV PENDING (vua tai len), PROCESSING, FAILED: khong co hang resume_parsed_data -> 409.
    @Test
    void t19_resumePendingProcessingOrFailed_returns409CvTextNotAvailable_noCall() throws Exception {
        OwnResume pending = createOwnResume("c08-t19-pending");
        OwnResume processing = createOwnResume("c08-t19-processing");
        jdbcTemplate.update(
                "UPDATE resumes SET parse_status = 'PROCESSING', claimed_at = now() WHERE id = ?",
                processing.resumeId());
        OwnResume failed = createOwnResume("c08-t19-failed");
        jdbcTemplate.update("UPDATE resumes SET parse_status = 'FAILED' WHERE id = ?", failed.resumeId());

        for (OwnResume own : List.of(pending, processing, failed)) {
            assertError(ask(own, CANDIDATE_QUESTION), 409, "CV_TEXT_NOT_AVAILABLE", CV_TEXT_MESSAGE);
        }
        assertNoModelCall();
    }

    // R-Q3 - CV dang trich xuat lai (C05): parse_status van DONE, yeu cau trich xuat lai PENDING roi RUNNING, raw_text cu
    // con -> hoi duoc.
    @Test
    void t19_resumeBeingReparsed_stillHasRawText_returns200() throws Exception {
        OwnResume own = createOwnResumeWithText("c08-t19-reparse", RAW_TEXT);
        jdbcTemplate.update("UPDATE resumes SET parse_status = 'DONE' WHERE id = ?", own.resumeId());
        UUID requestId = jdbcTemplate.queryForObject(
                "INSERT INTO resume_reparse_requests (resume_id, status) VALUES (?, 'PENDING') RETURNING id",
                UUID.class,
                own.resumeId());

        for (String status : List.of("PENDING", "RUNNING")) {
            if (status.equals("RUNNING")) {
                jdbcTemplate.update(
                        "UPDATE resume_reparse_requests SET status = 'RUNNING', claimed_at = now() WHERE id = ?",
                        requestId);
            }
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT status FROM resume_reparse_requests WHERE id = ?", String.class, requestId))
                    .isEqualTo(status);
            resetChatModelMock();
            stubOutputs(answeredJson(CANDIDATE_ANSWER, QUOTE_EXPERIENCE));

            JsonNode root = assertOutcome(ask(own, CANDIDATE_QUESTION), "ANSWERED");

            assertThat(quotesOf(root)).as(status).containsExactly(QUOTE_EXPERIENCE);
            capturedPrompts(1);
        }
    }

    // ---- T20 ----

    // Cum CV_IMPROVEMENT (R-G3) phia ung vien bi chan truoc K1: ca CV chua co raw_text van 200 REFUSED, khong phai 409.
    @Test
    void t20_cvImprovementPhrases_returnRefused_noCall_evenWithoutRawText() throws Exception {
        OwnResume withText = createOwnResumeWithText("c08-t20", RAW_TEXT);
        OwnResume withoutText = createOwnResume("c08-t20-notext");

        for (OwnResume own : List.of(withText, withoutText)) {
            assertRefused(ask(own, "Tôi nên học thêm gì?"), "CV_IMPROVEMENT");
            assertRefused(ask(own, "Làm sao để cải thiện CV của tôi?"), "CV_IMPROVEMENT");
            assertRefused(ask(own, "CV của tôi còn thiếu kỹ năng gì?"), "CV_IMPROVEMENT");
            // HIRING_JUDGEMENT ap ca hai phia.
            assertRefused(ask(own, "CV của tôi có phù hợp không?"), "HIRING_JUDGEMENT");
        }
        assertNoModelCall();
        // Doi chung: cau hoi thuong tren CV chua co raw_text thi 409 (K1 thieu van ban).
        assertError(ask(withoutText, CANDIDATE_QUESTION), 409, "CV_TEXT_NOT_AVAILABLE");
        assertNoModelCall();
    }

    @Test
    void t20_aiRefusesCvImprovementOnCandidateSide_returnsRefused_oneCall() throws Exception {
        OwnResume own = createOwnResumeWithText("c08-t20-ai", RAW_TEXT);
        stubOutputs(refusedJson("CV_IMPROVEMENT"));

        // Cau hoi khong chua cum tu R-G - di toi AI.
        assertRefused(ask(own, "Giúp tôi làm CV hay hơn"), "CV_IMPROVEMENT");
        capturedPrompts(1);
    }

    // ---- T21 ----

    // CV da nop vao mot don co rubric, luot cham, giai thich, ghi chu HR va tin tuyen dung: ung vien hoi CV do -> khong
    // chuoi bi mat nao, khong tieu de/mo ta/yeu cau cua tin trong prompt.
    @Test
    void t21_resumeUsedInApplication_promptHasNoHrDataAndNoJobPosting() throws Exception {
        Fixture f = createApplication("c08-t21", "TIEU_CHI_BI_MAT_C08");
        insertParsedData(f.resumeId(), RAW_TEXT);
        setJobPosting(f.jobId(), "TIEU_DE_TIN_BI_MAT_C08", "MO_TA_TIN_BI_MAT_C08", "YEU_CAU_TIN_BI_MAT_C08");
        seedHrOnlyData(f);
        stubOutputs(answeredJson(CANDIDATE_ANSWER, QUOTE_EXPERIENCE));

        assertOutcome(askAsCandidate(f.resumeId(), f.candidateToken(), CANDIDATE_QUESTION), "ANSWERED");

        Prompt prompt = capturedPrompts(1).get(0);
        String all = systemText(prompt) + "\n" + userText(prompt);
        for (String secret : List.of(
                "TIEU_CHI_BI_MAT_C08", "LY_DO_BI_MAT_C08", "TRICH_DAN_BI_MAT_C08", "TOM_TAT_BI_MAT_C08",
                "GHI_CHU_BI_MAT_C08", "7.345", "7,345", "TIEU_DE_TIN_BI_MAT_C08", "MO_TA_TIN_BI_MAT_C08",
                "YEU_CAU_TIN_BI_MAT_C08")) {
            assertThat(all).as(secret).doesNotContain(secret);
        }
        // Chi xet user message: system (cv-qa-v1.st) co mo ta ten the <tin_tuyen_dung>.
        assertThat(userText(prompt)).doesNotContain("<tin_tuyen_dung>");
        // Doi chung: CV cua don van vao prompt.
        assertThat(userText(prompt)).contains("<cv>\n" + RAW_TEXT + "\n</cv>");
    }
}

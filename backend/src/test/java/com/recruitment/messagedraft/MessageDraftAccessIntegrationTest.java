package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.jobapplication.ApplicationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// FR-C07 T1-T6, T8 (kem 2 ca hoi quy pham vi MessageDraftExceptionAdvice). Moi ca "khong goi AI" kiem bang
// verifyNoInteractions(chatModel).
class MessageDraftAccessIntegrationTest extends MessageDraftIntegrationTestSupport {

    private static final String UNREADABLE_MESSAGE = "Yêu cầu soạn nháp không hợp lệ.";
    private static final String INVALID_SCENARIO_MESSAGE = "Tình huống không hợp lệ.";

    // ---- T1, T2 - duong thanh cong ----

    @Test
    void t1_hrOwnApplication_thankForApplyingFormal_returnsDraft() throws Exception {
        Fixture f = createApplication("c07-t1");
        stubDraft("Xin chào bạn, cảm ơn bạn đã ứng tuyển.");

        MvcResult result = postDraft(Side.HR, f.applicationId(), f.hrToken(), "THANK_FOR_APPLYING", "FORMAL");

        assertDraft(result, "Xin chào bạn, cảm ơn bạn đã ứng tuyển.");
        capturedPrompts(1);
    }

    @Test
    void t2_candidateOwnApplication_askProgressFriendly_returnsDraft() throws Exception {
        Fixture f = createApplication("c07-t2");
        stubDraft("Chào Anh/Chị, em muốn hỏi tiến độ hồ sơ.");

        MvcResult result = postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "ASK_PROGRESS", "FRIENDLY");

        assertDraft(result, "Chào Anh/Chị, em muốn hỏi tiến độ hồ sơ.");
        capturedPrompts(1);
    }

    // ---- T3 - HR: don cong ty khac 403, don khong ton tai 404, HR chua co cong ty 404 ----

    @Test
    void t3_hrOtherCompany_forbiddenOnA1A2_noAiCall() throws Exception {
        Fixture f = createApplication("c07-t3-own");
        String otherHr = registerHrWithCompany("c07-t3-other");

        assertThat(getScenarios(Side.HR, f.applicationId(), otherHr).getResponse().getStatus()).isEqualTo(403);
        assertThat(postDraft(Side.HR, f.applicationId(), otherHr, "THANK_FOR_APPLYING", "FORMAL")
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        verifyNoInteractions(chatModel);
    }

    @Test
    void t3_hrMissingApplication_notFoundOnA1A2_noAiCall() throws Exception {
        String hr = registerHrWithCompany("c07-t3-missing");

        assertError(getScenarios(Side.HR, MISSING_ID, hr), 404, "APPLICATION_NOT_FOUND");
        assertError(postDraft(Side.HR, MISSING_ID, hr, "THANK_FOR_APPLYING", "FORMAL"), 404, "APPLICATION_NOT_FOUND");
        verifyNoInteractions(chatModel);
    }

    @Test
    void t3_hrWithoutCompany_companyNotFoundOnA1A2_noAiCall() throws Exception {
        Fixture f = createApplication("c07-t3-nocomp");
        String hrWithoutCompany = register("hr", uniqueEmail("c07-t3-nocomp-x"), "Nha Tuyen Dung");

        assertError(getScenarios(Side.HR, f.applicationId(), hrWithoutCompany), 404, "COMPANY_NOT_FOUND");
        assertError(
                postDraft(Side.HR, f.applicationId(), hrWithoutCompany, "THANK_FOR_APPLYING", "FORMAL"),
                404,
                "COMPANY_NOT_FOUND");
        verifyNoInteractions(chatModel);
    }

    // ---- T4 - ung vien: don nguoi khac va don khong ton tai CUNG 404 ----

    @Test
    void t4_candidateOthersOrMissingApplication_sameNotFoundOnA1A2_noAiCall() throws Exception {
        Fixture f = createApplication("c07-t4");
        String otherCandidate = register("candidate", uniqueEmail("c07-t4-other"), "Ung Vien Khac");

        assertError(getScenarios(Side.CANDIDATE, f.applicationId(), otherCandidate), 404, "APPLICATION_NOT_FOUND");
        assertError(
                postDraft(Side.CANDIDATE, f.applicationId(), otherCandidate, "ASK_PROGRESS", "FORMAL"),
                404,
                "APPLICATION_NOT_FOUND");
        assertError(getScenarios(Side.CANDIDATE, MISSING_ID, otherCandidate), 404, "APPLICATION_NOT_FOUND");
        assertError(
                postDraft(Side.CANDIDATE, MISSING_ID, otherCandidate, "ASK_PROGRESS", "FORMAL"),
                404,
                "APPLICATION_NOT_FOUND");
        verifyNoInteractions(chatModel);
    }

    // ---- T5 - sai vai tro 403 (hai chieu), khong token 401 ----

    @Test
    void t5_wrongRoleForbidden_noTokenUnauthenticated_noAiCall() throws Exception {
        Fixture f = createApplication("c07-t5");

        assertThat(getScenarios(Side.HR, f.applicationId(), f.candidateToken()).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(postDraft(Side.HR, f.applicationId(), f.candidateToken(), "THANK_FOR_APPLYING", "FORMAL")
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(getScenarios(Side.CANDIDATE, f.applicationId(), f.hrToken()).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(postDraft(Side.CANDIDATE, f.applicationId(), f.hrToken(), "ASK_PROGRESS", "FORMAL")
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        for (Side side : Side.values()) {
            assertError(getScenarios(side, f.applicationId(), null), 401, "UNAUTHENTICATED");
            assertError(postDraft(side, f.applicationId(), null, "CUSTOM", "FORMAL"), 401, "UNAUTHENTICATED");
        }
        verifyNoInteractions(chatModel);
    }

    // ---- T6 - don WITHDRAWN 409 ca hai phia; thu tu R-Q3 voi body doc duoc nhung khong hop le ----

    @Test
    void t6_withdrawnApplication_conflictOnA1A2BothSides_evenWithInvalidReadableBody() throws Exception {
        Fixture f = createApplication("c07-t6-wd");
        setStatus(f, ApplicationStatus.WITHDRAWN);

        for (Side side : Side.values()) {
            String token = f.token(side);
            assertError(getScenarios(side, f.applicationId(), token), 409, "CONVERSATION_READ_ONLY");
            String validScenario = side == Side.HR ? "THANK_FOR_APPLYING" : "ASK_PROGRESS";
            String otherSideScenario = side == Side.HR ? "ASK_PROGRESS" : "RESULT_NOTICE";
            assertError(postDraft(side, f.applicationId(), token, validScenario, "FORMAL"), 409, "CONVERSATION_READ_ONLY");
            // Thieu tone va tinh huong cua phia kia: don WITHDRAWN dung truoc buoc hop le request -> 409, khong 400.
            assertError(postDraft(side, f.applicationId(), token, validScenario, null), 409, "CONVERSATION_READ_ONLY");
            assertError(
                    postDraft(side, f.applicationId(), token, otherSideScenario, "FORMAL"),
                    409,
                    "CONVERSATION_READ_ONLY");
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void t6_othersApplication_withInvalidReadableBody_permissionErrorBeforeValidation() throws Exception {
        Fixture f = createApplication("c07-t6-own");
        String otherHr = registerHrWithCompany("c07-t6-hr");
        String otherCandidate = register("candidate", uniqueEmail("c07-t6-uv"), "Ung Vien Khac");

        assertThat(postDraft(Side.HR, f.applicationId(), otherHr, "THANK_FOR_APPLYING", null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(postDraft(Side.HR, f.applicationId(), otherHr, "ASK_PROGRESS", "FORMAL")
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertError(
                postDraft(Side.CANDIDATE, f.applicationId(), otherCandidate, "ASK_PROGRESS", null),
                404,
                "APPLICATION_NOT_FOUND");
        assertError(
                postDraft(Side.CANDIDATE, f.applicationId(), otherCandidate, "RESULT_NOTICE", "FORMAL"),
                404,
                "APPLICATION_NOT_FOUND");
        verifyNoInteractions(chatModel);
    }

    // ---- T8 - hop le request ----

    @Test
    void t8_scenarioOfOtherSide_orMissingScenario_badRequest() throws Exception {
        Fixture f = createApplication("c07-t8-side");

        assertError(
                postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "RESULT_NOTICE", "FORMAL"),
                400,
                "INVALID_DRAFT_REQUEST",
                INVALID_SCENARIO_MESSAGE);
        assertError(
                postDraft(Side.HR, f.applicationId(), f.hrToken(), "ASK_PROGRESS", "FORMAL"),
                400,
                "INVALID_DRAFT_REQUEST",
                INVALID_SCENARIO_MESSAGE);
        assertError(
                postDraft(Side.HR, f.applicationId(), f.hrToken(), null, "FORMAL"),
                400,
                "INVALID_DRAFT_REQUEST",
                INVALID_SCENARIO_MESSAGE);
        verifyNoInteractions(chatModel);
    }

    @Test
    void t8_missingTone_badRequest() throws Exception {
        Fixture f = createApplication("c07-t8-tone");

        for (Side side : Side.values()) {
            String scenario = side == Side.HR ? "THANK_FOR_APPLYING" : "ASK_PROGRESS";
            assertError(
                    postDraft(side, f.applicationId(), f.token(side), scenario, null),
                    400,
                    "INVALID_DRAFT_REQUEST",
                    "Vui lòng chọn giọng văn.");
        }
        verifyNoInteractions(chatModel);
    }

    // Ngoai le R-Q3b: enum la / JSON hong -> 400 ngay o buoc doc body, ke ca don cua ben khac hay don WITHDRAWN; body
    // loi DUNG {"error","message"}, khong noi gi ve don.
    @Test
    void t8_unknownEnumOrBrokenJson_badRequestBeforeOwnershipAndWithdrawnChecks() throws Exception {
        Fixture own = createApplication("c07-t8-body");
        Fixture withdrawn = createApplication("c07-t8-body-wd");
        setStatus(withdrawn, ApplicationStatus.WITHDRAWN);
        String otherHr = registerHrWithCompany("c07-t8-body-hr");
        String otherCandidate = register("candidate", uniqueEmail("c07-t8-body-uv"), "Ung Vien Khac");

        List<String> unreadableBodies = List.of(
                "{\"scenario\":\"FOO\",\"tone\":\"FORMAL\"}",
                "{\"scenario\":\"ASK_PROGRESS\",\"tone\":\"LOUD\"}",
                "{bad");
        for (String body : unreadableBodies) {
            assertUnreadable(postDraft(Side.HR, own.applicationId(), own.hrToken(), body));
            assertUnreadable(postDraft(Side.CANDIDATE, own.applicationId(), own.candidateToken(), body));
            assertUnreadable(postDraft(Side.HR, own.applicationId(), otherHr, body));
            assertUnreadable(postDraft(Side.CANDIDATE, own.applicationId(), otherCandidate, body));
            assertUnreadable(postDraft(Side.HR, MISSING_ID, own.hrToken(), body));
            assertUnreadable(postDraft(Side.HR, withdrawn.applicationId(), withdrawn.hrToken(), body));
            assertUnreadable(postDraft(Side.CANDIDATE, withdrawn.applicationId(), withdrawn.candidateToken(), body));
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void t8_brokenJson_wrongRoleForbidden_noTokenUnauthenticated_filterChainFirst() throws Exception {
        Fixture f = createApplication("c07-t8-chain");

        assertThat(postDraft(Side.HR, f.applicationId(), f.candidateToken(), "{bad").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(postDraft(Side.CANDIDATE, f.applicationId(), f.hrToken(), "{bad").getResponse().getStatus())
                .isEqualTo(403);
        assertError(postDraft(Side.HR, f.applicationId(), null, "{bad"), 401, "UNAUTHENTICATED");
        verifyNoInteractions(chatModel);
    }

    // Hoi quy pham vi advice (i): M2 cua C06 khong doc body JSON -> van 400 MESSAGE_EMPTY nhu truoc FR-C07.
    @Test
    void t8_regression_brokenJsonToM2_stillMessageEmpty() throws Exception {
        Fixture f = createApplication("c07-t8-m2");

        MvcResult result = mockMvc
                .perform(post(Side.HR.messagesPath(f.applicationId()))
                        .header("Authorization", "Bearer " + f.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andReturn();

        assertError(result, 400, "MESSAGE_EMPTY", "Tin nhắn chưa có nội dung");
    }

    // Hoi quy pham vi advice (ii): endpoint JSON khac giu 400 mac dinh cua Spring, khong thanh INVALID_DRAFT_REQUEST.
    @Test
    void t8_regression_brokenJsonToLogin_keepsSpringDefault400() throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(rawBody(result)).doesNotContain("INVALID_DRAFT_REQUEST");
    }

    @Test
    void t8_customPurpose_emptyOrBlankOrMissing_badRequest() throws Exception {
        Fixture f = createApplication("c07-t8-empty");

        for (String purpose : new String[] {"", "   ", "\r\n\t "}) {
            assertError(
                    postDraft(Side.HR, f.applicationId(), f.hrToken(), requestJson("CUSTOM", "FORMAL", purpose)),
                    400,
                    "INVALID_DRAFT_REQUEST",
                    "Vui lòng mô tả mục đích tin nhắn.");
        }
        assertError(
                postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "CUSTOM", "FRIENDLY"),
                400,
                "INVALID_DRAFT_REQUEST",
                "Vui lòng mô tả mục đích tin nhắn.");
        verifyNoInteractions(chatModel);
    }

    // Bien R-S4: 499, 500 -> 200; 501 -> 400. 499 ky tu + CRLF: 501 ky tu tho nhung 500 sau chuan hoa -> 200.
    @Test
    void t8_customPurpose_lengthBoundaries() throws Exception {
        Fixture f = createApplication("c07-t8-len");
        stubDraft(DEFAULT_DRAFT);

        assertDraft(
                postDraft(Side.HR, f.applicationId(), f.hrToken(), requestJson("CUSTOM", "FORMAL", "a".repeat(499))),
                DEFAULT_DRAFT);
        assertDraft(
                postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(),
                        requestJson("CUSTOM", "FORMAL", "b".repeat(500))),
                DEFAULT_DRAFT);
        assertDraft(
                postDraft(Side.HR, f.applicationId(), f.hrToken(),
                        requestJson("CUSTOM", "FORMAL", "c".repeat(499) + "\r\n")),
                DEFAULT_DRAFT);
        capturedPrompts(3);

        assertError(
                postDraft(Side.HR, f.applicationId(), f.hrToken(), requestJson("CUSTOM", "FORMAL", "d".repeat(501))),
                400,
                "INVALID_DRAFT_REQUEST",
                "Mô tả mục đích tối đa 500 ký tự.");
        capturedPrompts(3);
    }

    // R-S4 - customPurpose voi tinh huong khac CUSTOM bi bo qua: khong loi, khong vao prompt.
    @Test
    void t8_customPurposeWithNonCustomScenario_ignored_notInPrompt() throws Exception {
        Fixture f = createApplication("c07-t8-ignore");
        stubDraft(DEFAULT_DRAFT);
        String marker = "MUC-DICH-BI-BO-QUA-" + UUID.randomUUID();

        assertDraft(
                postDraft(Side.HR, f.applicationId(), f.hrToken(), requestJson("THANK_FOR_APPLYING", "FORMAL", marker)),
                DEFAULT_DRAFT);

        Prompt prompt = capturedPrompts(1).get(0);
        assertThat(systemText(prompt)).doesNotContain(marker);
        assertThat(userText(prompt)).doesNotContain(marker).doesNotContain("<muc_dich>");
    }

    private static void assertUnreadable(MvcResult result) throws Exception {
        assertError(result, 400, "INVALID_DRAFT_REQUEST", UNREADABLE_MESSAGE);
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("error", "message");
        assertThat(rawBody(result)).doesNotContain("APPLICATION_NOT_FOUND").doesNotContain("CONVERSATION_READ_ONLY");
    }
}

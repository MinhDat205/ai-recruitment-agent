package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-C07 T7 - ma tran R-S1/R-S3: A2 tra 409 DRAFT_SCENARIO_UNAVAILABLE (khong goi AI) hoac 200 dung theo trang thai don
// va viec co giay moi; A1 tra available/unavailableReason khop tung o. Mot don, doi trang thai bang native UPDATE va
// them/xoa giay moi giua cac o.
class MessageDraftScenarioIntegrationTest extends MessageDraftIntegrationTestSupport {

    private static final String UNAVAILABLE_MESSAGE = "Tình huống này chưa dùng được với trạng thái hiện tại của đơn.";
    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-20T02:00:00Z");

    // null = dung duoc.
    private static Map<String, String> hrExpected(String interviewReminder, String resultNotice) {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("REQUEST_MORE_INFO", null);
        expected.put("INTERVIEW_REMINDER", interviewReminder);
        expected.put("THANK_FOR_APPLYING", null);
        expected.put("RESULT_NOTICE", resultNotice);
        expected.put("CUSTOM", null);
        return expected;
    }

    private static Map<String, String> candidateExpected(String thankAfterInterview, String requestReschedule) {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ASK_PROGRESS", null);
        expected.put("THANK_AFTER_INTERVIEW", thankAfterInterview);
        expected.put("REQUEST_RESCHEDULE", requestReschedule);
        expected.put("CUSTOM", null);
        return expected;
    }

    // A1: dung thu tu bang R-S1 cua phia goi, available/unavailableReason khop tung o.
    private void assertScenarios(Side side, Fixture f, Map<String, String> expected) throws Exception {
        MvcResult result = getScenarios(side, f.applicationId(), f.token(side));
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(200);
        List<String> scenarios = new ArrayList<>();
        for (JsonNode option : body(result).get("scenarios")) {
            String scenario = option.get("scenario").asString();
            scenarios.add(scenario);
            String expectedReason = expected.get(scenario);
            assertThat(option.get("available").asBoolean()).as(scenario).isEqualTo(expectedReason == null);
            if (expectedReason == null) {
                assertThat(option.get("unavailableReason").isNull()).as(scenario).isTrue();
            } else {
                assertThat(option.get("unavailableReason").asString()).as(scenario).isEqualTo(expectedReason);
            }
        }
        assertThat(scenarios).containsExactlyElementsOf(expected.keySet());
    }

    private void assertUnavailable(Side side, Fixture f, String scenario) throws Exception {
        clearInvocations(chatModel);
        assertError(
                postDraft(side, f.applicationId(), f.token(side), scenario, "FORMAL"),
                409,
                "DRAFT_SCENARIO_UNAVAILABLE",
                UNAVAILABLE_MESSAGE);
        verifyNoInteractions(chatModel);
    }

    private void assertAvailable(Side side, Fixture f, String scenario) throws Exception {
        stubDraft(DEFAULT_DRAFT);
        assertDraft(postDraft(side, f.applicationId(), f.token(side), scenario, "FORMAL"), DEFAULT_DRAFT);
        capturedPrompts(1);
        resetChatModelMock();
    }

    @Test
    void resultNotice_onlyAvailableWhenHiredOrRejected() throws Exception {
        Fixture f = createApplication("c07-t7-result");

        // PENDING (mac dinh khi nop don)
        assertScenarios(Side.HR, f, hrExpected("NO_ACTIVE_INTERVIEW", "RESULT_NOT_FINAL"));
        assertUnavailable(Side.HR, f, "RESULT_NOTICE");

        setStatus(f, ApplicationStatus.INTERVIEW_INVITED);
        assertUnavailable(Side.HR, f, "RESULT_NOTICE");

        setStatus(f, ApplicationStatus.HIRED);
        assertScenarios(Side.HR, f, hrExpected("NO_ACTIVE_INTERVIEW", null));
        assertAvailable(Side.HR, f, "RESULT_NOTICE");

        setStatus(f, ApplicationStatus.REJECTED);
        assertScenarios(Side.HR, f, hrExpected("NO_ACTIVE_INTERVIEW", null));
        assertAvailable(Side.HR, f, "RESULT_NOTICE");
    }

    @Test
    void interviewReminderAndReschedule_onlyWhenInterviewInvitedWithInvitation() throws Exception {
        Fixture f = createApplication("c07-t7-remind");

        // INTERVIEW_INVITED nhung chua co giay moi
        setStatus(f, ApplicationStatus.INTERVIEW_INVITED);
        assertScenarios(Side.HR, f, hrExpected("NO_ACTIVE_INTERVIEW", "RESULT_NOT_FINAL"));
        assertScenarios(Side.CANDIDATE, f, candidateExpected("NO_INTERVIEW", "NO_ACTIVE_INTERVIEW"));
        assertUnavailable(Side.HR, f, "INTERVIEW_REMINDER");
        assertUnavailable(Side.CANDIDATE, f, "REQUEST_RESCHEDULE");

        // INTERVIEW_INVITED co giay moi -> dung duoc
        insertInvitation(f, SCHEDULED_AT, "Tang 3");
        assertScenarios(Side.HR, f, hrExpected(null, "RESULT_NOT_FINAL"));
        assertScenarios(Side.CANDIDATE, f, candidateExpected(null, null));
        assertAvailable(Side.HR, f, "INTERVIEW_REMINDER");
        assertAvailable(Side.CANDIDATE, f, "REQUEST_RESCHEDULE");

        // HIRED co giay moi -> khong con buoi phong van dang cho
        setStatus(f, ApplicationStatus.HIRED);
        assertScenarios(Side.HR, f, hrExpected("NO_ACTIVE_INTERVIEW", null));
        assertScenarios(Side.CANDIDATE, f, candidateExpected(null, "NO_ACTIVE_INTERVIEW"));
        assertUnavailable(Side.HR, f, "INTERVIEW_REMINDER");
        assertUnavailable(Side.CANDIDATE, f, "REQUEST_RESCHEDULE");
    }

    @Test
    void thankAfterInterview_onlyWhenInvitationExists() throws Exception {
        Fixture f = createApplication("c07-t7-thank");

        assertScenarios(Side.CANDIDATE, f, candidateExpected("NO_INTERVIEW", "NO_ACTIVE_INTERVIEW"));
        assertUnavailable(Side.CANDIDATE, f, "THANK_AFTER_INTERVIEW");

        setStatus(f, ApplicationStatus.REJECTED);
        assertUnavailable(Side.CANDIDATE, f, "THANK_AFTER_INTERVIEW");

        insertInvitation(f, SCHEDULED_AT, null);
        assertScenarios(Side.CANDIDATE, f, candidateExpected(null, "NO_ACTIVE_INTERVIEW"));
        assertAvailable(Side.CANDIDATE, f, "THANK_AFTER_INTERVIEW");

        // Xoa giay moi -> khoa lai (backend tinh moi lan goi, khong tin trang thai client).
        deleteInvitations(f);
        assertUnavailable(Side.CANDIDATE, f, "THANK_AFTER_INTERVIEW");
    }

    // Tinh huong khong co dieu kien rieng dung duoc o moi trang thai truoc WITHDRAWN.
    @Test
    void unconditionalScenarios_availableInEveryNonWithdrawnStatus() throws Exception {
        Fixture f = createApplication("c07-t7-free");

        for (ApplicationStatus status : List.of(
                ApplicationStatus.PENDING,
                ApplicationStatus.INTERVIEW_INVITED,
                ApplicationStatus.HIRED,
                ApplicationStatus.REJECTED)) {
            setStatus(f, status);
            assertAvailable(Side.HR, f, "REQUEST_MORE_INFO");
            assertAvailable(Side.HR, f, "THANK_FOR_APPLYING");
            assertAvailable(Side.CANDIDATE, f, "ASK_PROGRESS");
        }
    }
}

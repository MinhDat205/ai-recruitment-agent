package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.anthropic.errors.AnthropicIoException;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.messaging.MessageSenderRole;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

// FR-C07 T18 (R-D1, R-D2, R-S6) - A2 KHONG luu gi, KHONG doi trang thai: so dong application_messages, notifications (ca
// hai nguoi dung), application_status_history va job_applications.status/updated_at khong doi sau A2 thanh cong VA sau
// A2 loi 502/503/504. Ap ca voi RESULT_NOTICE o don HIRED.
class MessageDraftNoSideEffectIntegrationTest extends MessageDraftIntegrationTestSupport {

    private record Snapshot(long messages, long notifications, long history, String status, Timestamp updatedAt) {
    }

    private Snapshot snapshot(Fixture f) {
        Map<String, Object> application = jdbcTemplate.queryForMap(
                "SELECT status, updated_at FROM job_applications WHERE id = ?", f.applicationId());
        return new Snapshot(
                count("SELECT COUNT(*) FROM application_messages WHERE application_id = ?", f.applicationId()),
                count("SELECT COUNT(*) FROM notifications WHERE user_id IN (?, ?)", f.hrUserId(), f.candidateUserId()),
                count("SELECT COUNT(*) FROM application_status_history WHERE application_id = ?", f.applicationId()),
                (String) application.get("status"),
                (Timestamp) application.get("updated_at"));
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    @Test
    void resultNoticeOnHired_successAndAllErrorKinds_changeNothing() throws Exception {
        Fixture f = createApplication("c07-t18-hired");
        setStatus(f, ApplicationStatus.HIRED);
        insertMessage(f, MessageSenderRole.CANDIDATE, "Tin co san", false, 1);
        Snapshot before = snapshot(f);

        // 200
        stubDraft("Chúc mừng bạn đã trúng tuyển.");
        assertDraft(
                postDraft(Side.HR, f.applicationId(), f.hrToken(), "RESULT_NOTICE", "FORMAL"),
                "Chúc mừng bạn đã trúng tuyển.");
        assertThat(snapshot(f)).isEqualTo(before);

        // 502
        resetChatModelMock();
        doReturn(chatResponse("khong phai JSON")).when(chatModel).call(any(Prompt.class));
        assertError(postDraft(Side.HR, f.applicationId(), f.hrToken(), "RESULT_NOTICE", "FORMAL"), 502, "AI_INVALID_OUTPUT");
        assertThat(snapshot(f)).isEqualTo(before);

        // 503
        resetChatModelMock();
        doThrow(new AnthropicIoException("mang")).when(chatModel).call(any(Prompt.class));
        assertError(postDraft(Side.HR, f.applicationId(), f.hrToken(), "RESULT_NOTICE", "FORMAL"), 503, "AI_UNAVAILABLE");
        assertThat(snapshot(f)).isEqualTo(before);

        // 504
        resetChatModelMock();
        doAnswer(invocation -> {
                    Thread.sleep(3000);
                    return chatResponse(draftJson(DEFAULT_DRAFT));
                })
                .when(chatModel)
                .call(any(Prompt.class));
        assertError(postDraft(Side.HR, f.applicationId(), f.hrToken(), "RESULT_NOTICE", "FORMAL"), 504, "AI_TIMEOUT");
        assertThat(snapshot(f)).isEqualTo(before);
    }

    @Test
    void candidateDraft_success_changesNothing() throws Exception {
        Fixture f = createApplication("c07-t18-cand");
        Snapshot before = snapshot(f);
        stubDraft(DEFAULT_DRAFT);

        assertDraft(postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "ASK_PROGRESS", "FORMAL"), DEFAULT_DRAFT);

        assertThat(snapshot(f)).isEqualTo(before);
        assertThat(before.status()).isEqualTo("PENDING");
    }
}

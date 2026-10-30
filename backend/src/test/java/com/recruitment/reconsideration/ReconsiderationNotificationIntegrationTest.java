package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.blindreview.CandidateAlias;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// FR-C09 T22 (R-N1-R-N5, R-A3): thong bao RECONSIDERATION_REQUESTED (C1), RECONSIDERATION_DECLINED (H2), va chap nhan
// (I2) chi co thong bao doi trang thai san co. Listener chay AFTER_COMMIT trong chinh thread cua request, nen sau khi
// MockMvc tra ve dong thong bao da co.
class ReconsiderationNotificationIntegrationTest extends ReconsiderationIntegrationTestSupport {

    private static final String REASON = "Ly do rieng tu cua ung vien - khong duoc vao thong bao";

    private Map<String, Object> onlyNotification(Fixture f, String type) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, title, body, link, email_body FROM notifications WHERE type = ? AND entity_id = ?",
                type,
                f.applicationId());
    }

    @Test
    void t22_request_notifiesCompanyOwnerOnce_withoutReason() throws Exception {
        Fixture f = rejectedAtScreening("c09-n1");
        String title = jdbcTemplate.queryForObject("SELECT title FROM jobs WHERE id = ?", String.class, f.jobId());

        MvcResult result = postReason(f.candidate().token(), f.applicationId(), REASON);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(countNotifications(f.hr().id(), "RECONSIDERATION_REQUESTED", f.applicationId())).isEqualTo(1L);
        assertThat(countNotifications(f.candidate().id(), "RECONSIDERATION_REQUESTED", f.applicationId())).isZero();
        Map<String, Object> n = onlyNotification(f, "RECONSIDERATION_REQUESTED");
        assertThat(n.get("title")).isEqualTo("Ứng viên yêu cầu xem xét lại");
        assertThat(n.get("body"))
                .isEqualTo("Ứng viên Ung Vien C09 đã gửi yêu cầu xem xét lại đơn ứng tuyển vị trí \"" + title + "\".");
        assertThat(n.get("link")).isEqualTo("/hr/applications/" + f.applicationId());
        assertThat(n.get("email_body")).isNull();
        assertThat((String) n.get("body")).doesNotContain(REASON);
    }

    // R-A3 - don dang an danh (Job bat cong tac, chua tung moi): nhan "Ung vien #...", khong ho ten.
    @Test
    void t22_maskedApplication_usesAlias_noFullName() throws Exception {
        Fixture f = rejectedAtScreening("c09-n2");
        jdbcTemplate.update("UPDATE jobs SET blind_review_enabled = TRUE WHERE id = ?", f.jobId());

        postReason(f.candidate().token(), f.applicationId(), "Toi la Ung Vien C09, xin xem lai");

        Map<String, Object> n = onlyNotification(f, "RECONSIDERATION_REQUESTED");
        assertThat((String) n.get("body"))
                .startsWith(CandidateAlias.of(f.applicationId()) + " đã gửi yêu cầu xem xét lại")
                .doesNotContain("Ung Vien C09")
                .doesNotContain("xin xem lai");
    }

    // ---- T22 phan H2 (R-N3): mot thong bao cho ung vien, khong chua phan hoi ----
    @Test
    void t22_decline_notifiesCandidateOnce_withoutResponse() throws Exception {
        Fixture f = openRequest("c09-n4", null);
        String title = jdbcTemplate.queryForObject("SELECT title FROM jobs WHERE id = ?", String.class, f.jobId());
        String response = "Phan hoi rieng cua HR - khong duoc vao thong bao";

        assertThat(declineWith(f.hr().token(), f.applicationId(), response).getResponse().getStatus()).isEqualTo(200);

        assertThat(countNotifications(f.candidate().id(), "RECONSIDERATION_DECLINED", f.applicationId())).isEqualTo(1L);
        assertThat(countNotificationsForApplication(f.applicationId())).isEqualTo(1L);
        Map<String, Object> n = onlyNotification(f, "RECONSIDERATION_DECLINED");
        assertThat(n.get("user_id")).isEqualTo(f.candidate().id());
        assertThat(n.get("title")).isEqualTo("Kết quả yêu cầu xem xét lại");
        assertThat(n.get("body"))
                .isEqualTo("Nhà tuyển dụng đã xem xét lại và giữ quyết định từ chối đơn ứng tuyển vị trí \"" + title
                        + "\". Xem chi tiết trong đơn ứng tuyển.");
        assertThat(n.get("link")).isEqualTo("/candidate/applications/" + f.applicationId());
        assertThat(n.get("email_body")).isNull();
        assertThat((String) n.get("body")).doesNotContain(response);
    }

    // ---- T22 phan I2 (R-S4, R-N4): chap nhan = dung 1 APPLICATION_STATUS_CHANGED cho ung vien, 0 cho HR ----
    @Test
    void t22_accept_onlyStatusChangedForCandidate() throws Exception {
        Fixture f = openRequest("c09-n5", null);

        assertThat(sendInvitation(f.hr().token(), f.applicationId()).getResponse().getStatus()).isEqualTo(201);

        assertThat(countNotifications(f.candidate().id(), "APPLICATION_STATUS_CHANGED", f.applicationId()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM notifications WHERE user_id = ? AND entity_id = ?",
                        Long.class,
                        f.hr().id(),
                        f.applicationId()))
                .isZero();
        assertThat(countNotificationsForApplication(f.applicationId())).isEqualTo(1L);
    }

    // H2 / I2 loi -> 0 thong bao.
    @Test
    void t22_failedDecisions_createNoNotification() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-n6-hr");
        Account candidate = registerCandidate("c09-n6-c");
        Fixture tooLong = openRequest(hr, candidate, null);
        Fixture noRequest = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertTemplate(noRequest.jobId());

        assertThat(declineWith(hr.token(), tooLong.applicationId(), "b".repeat(1001)).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(declineWith(hr.token(), noRequest.applicationId(), "x").getResponse().getStatus())
                .isEqualTo(409);
        assertThat(sendInvitation(hr.token(), noRequest.applicationId()).getResponse().getStatus())
                .isEqualTo(400);

        assertThat(countNotificationsForApplication(tooLong.applicationId())).isZero();
        assertThat(countNotificationsForApplication(noRequest.applicationId())).isZero();
    }

    // Request loi (409 / 400) -> 0 thong bao.
    @Test
    void t22_failedRequests_createNoNotification() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-n3-hr");
        Account candidate = registerCandidate("c09-n3-c");
        Fixture expired = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        backdateRejection(expired.applicationId(), "INTERVAL '8 days'");
        Fixture tooLong = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        Fixture twice = rejectedAtScreening(hr, candidate, insertOpenJob(hr));

        assertThat(postReason(candidate.token(), expired.applicationId(), "x").getResponse().getStatus())
                .isEqualTo(409);
        assertThat(postReason(candidate.token(), tooLong.applicationId(), "a".repeat(1001)).getResponse().getStatus())
                .isEqualTo(400);
        postReason(candidate.token(), twice.applicationId(), "x");
        assertThat(postReason(candidate.token(), twice.applicationId(), "y").getResponse().getStatus())
                .isEqualTo(409);

        assertThat(countNotificationsForApplication(expired.applicationId())).isZero();
        assertThat(countNotificationsForApplication(tooLong.applicationId())).isZero();
        assertThat(countNotifications(hr.id(), "RECONSIDERATION_REQUESTED", twice.applicationId())).isEqualTo(1L);
    }
}

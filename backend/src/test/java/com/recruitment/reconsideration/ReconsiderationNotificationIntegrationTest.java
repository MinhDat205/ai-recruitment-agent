package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.blindreview.CandidateAlias;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// FR-C09 T22 phan C1 (R-N1, R-N2, R-A3): thong bao RECONSIDERATION_REQUESTED. Listener chay AFTER_COMMIT trong chinh
// thread cua request, nen sau khi MockMvc tra ve dong thong bao da co. Phan H2/I2 cua T22 them o dot 4.
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

package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.recruitment.common.exception.InvalidApplicationStatusTransitionException;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusService;
import com.recruitment.jobapplication.FunnelCountsView;
import com.recruitment.jobapplication.JobApplicationRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// FR-C09 nhanh chap nhan trong I2 (R-S1-R-S6) - T10, T10b, T11 a-e, T12, T24 cua REQUIREMENT muc 7.1. Lop KHONG
// @Transactional (thong bao AFTER_COMMIT, rollback cua I2 phai la rollback that cua transaction request).
class ReconsiderationAcceptIntegrationTest extends ReconsiderationIntegrationTestSupport {

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    private static String transitionMessage(String from) {
        return "Không thể chuyển đơn ứng tuyển từ trạng thái " + from + " sang INTERVIEW_INVITED.";
    }

    // ---- T10: chap nhan ----

    @Test
    void t10_openRequest_inviteAccepts_inOneTransaction() throws Exception {
        Fixture f = openRequest("c09-t10", "Xin xem lai");

        MvcResult result = sendInvitation(f.hr().token(), f.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(applicationStatus(f.applicationId())).isEqualTo("INTERVIEW_INVITED");
        Map<String, Object> request = requestRow(f.applicationId());
        assertThat(request.get("status")).isEqualTo("ACCEPTED");
        assertThat(request.get("decided_at")).isNotNull();
        assertThat(request.get("decided_by")).isEqualTo(f.hr().id());
        assertThat(request.get("hr_response")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM interview_invitations WHERE application_id = ? AND superseded_at IS NULL",
                        Long.class,
                        f.applicationId()))
                .isEqualTo(1L);
        assertThat(countHistory(f.applicationId(), "REJECTED", "INTERVIEW_INVITED")).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT changed_by FROM application_status_history
                        WHERE application_id = ? AND from_status = 'REJECTED' AND to_status = 'INTERVIEW_INVITED'
                        """,
                        UUID.class,
                        f.applicationId()))
                .isEqualTo(f.hr().id());
        // R-N4 - dung 1 thong bao doi trang thai cho ung vien, 0 cho HR, khong loai thong bao rieng nao.
        assertThat(countNotifications(f.candidate().id(), "APPLICATION_STATUS_CHANGED", f.applicationId()))
                .isEqualTo(1L);
        assertThat(countNotificationsForApplication(f.applicationId())).isEqualTo(1L);
    }

    // ---- T10b (B1): loi SAU buoc claim -> rollback ca transaction ----

    @Test
    void t10b_failureAfterClaim_rollsBackEverything() throws Exception {
        Fixture f = openRequest("c09-t10b", "Xin xem lai");
        // Giay moi hieu luc ghi tay: I2 qua claim (R-S3 (1)) va canh R-S2, roi createInvitation.saveAndFlush vo
        // uq_invitation_active.
        insertActiveInvitation(f.applicationId(), f.hr().id());
        long historyBefore = countHistory(f.applicationId());

        MvcResult result = sendInvitation(f.hr().token(), f.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("INTERVIEW_INVITATION_CONFLICT");
        assertThat(errorMessage(result))
                .isEqualTo("Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang.");
        Map<String, Object> request = requestRow(f.applicationId());
        assertThat(request.get("status")).isEqualTo("OPEN");
        assertThat(request.get("decided_at")).isNull();
        assertThat(request.get("decided_by")).isNull();
        assertThat(applicationStatus(f.applicationId())).isEqualTo("REJECTED");
        assertThat(countInvitations(f.applicationId())).isEqualTo(1L);
        assertThat(countHistory(f.applicationId())).isEqualTo(historyBefore);
        assertThat(countNotificationsForApplication(f.applicationId())).isZero();
    }

    // ---- T11: hoi quy FR-H12 R-F3 - van 400 dung cau hien nay ----

    private void assertInviteRejected400(Fixture f, String fromStatus, String expectedRequestStatus) throws Exception {
        long historyBefore = countHistory(f.applicationId());
        long invitationsBefore = countInvitations(f.applicationId());
        long notificationsBefore = countNotificationsForApplication(f.applicationId());

        MvcResult result = sendInvitation(f.hr().token(), f.applicationId());

        assertThat(result.getResponse().getStatus()).as(fromStatus).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("INVALID_APPLICATION_STATUS_TRANSITION");
        assertThat(errorMessage(result)).isEqualTo(transitionMessage(fromStatus));
        assertThat(applicationStatus(f.applicationId())).isEqualTo(fromStatus);
        assertThat(countHistory(f.applicationId())).isEqualTo(historyBefore);
        assertThat(countInvitations(f.applicationId())).isEqualTo(invitationsBefore);
        assertThat(countNotificationsForApplication(f.applicationId())).isEqualTo(notificationsBefore);
        if (expectedRequestStatus == null) {
            assertThat(countRequests(f.applicationId())).isZero();
        } else {
            assertThat(requestStatus(f.applicationId())).isEqualTo(expectedRequestStatus);
        }
    }

    @Test
    void t11a_rejectedWithoutRequest_returns400() throws Exception {
        Fixture f = rejectedAtScreening("c09-t11a");
        insertTemplate(f.jobId());

        assertInviteRejected400(f, "REJECTED", null);
    }

    @Test
    void t11b_rejectedWithAcceptedRequest_returns400() throws Exception {
        Fixture f = rejectedAtScreening("c09-t11b");
        insertTemplate(f.jobId());
        // Da duoc moi lai roi bi tu choi lan nua sau phong van.
        insertRequest(f.applicationId(), "ACCEPTED", f.hr().id(), null, null);
        history(f.applicationId(), "REJECTED", "INTERVIEW_INVITED", f.hr().id());
        history(f.applicationId(), "INTERVIEW_INVITED", "REJECTED", f.hr().id());

        assertInviteRejected400(f, "REJECTED", "ACCEPTED");
    }

    @Test
    void t11c_rejectedWithDeclinedRequest_returns400() throws Exception {
        Fixture f = rejectedAtScreening("c09-t11c");
        insertTemplate(f.jobId());
        insertRequest(f.applicationId(), "DECLINED", f.hr().id(), null, "Giu nguyen");

        assertInviteRejected400(f, "REJECTED", "DECLINED");
    }

    @Test
    void t11d_hired_and_t11e_withdrawn_return400() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t11d-hr");
        Account candidate = registerCandidate("c09-t11d-c");
        for (String status : new String[] {"HIRED", "WITHDRAWN"}) {
            UUID jobId = insertOpenJob(hr);
            insertTemplate(jobId);
            UUID applicationId = insertApplication(candidate.id(), jobId, status);
            history(applicationId, null, "PENDING", candidate.id());

            assertInviteRejected400(new Fixture(hr, candidate, jobId, applicationId), status, null);
        }
    }

    // ---- T12: bang chuyen tiep chung khong doi ----

    private MvcResult patchStatus(String token, UUID applicationId, String status) throws Exception {
        return call(
                patch("/api/hr/applications/" + applicationId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"),
                token);
    }

    @Test
    void t12_patchFromRejected_stillBlocked_evenWithOpenRequest() throws Exception {
        Fixture f = openRequest("c09-t12", null);

        for (String target : new String[] {"INTERVIEW_INVITED", "PENDING", "HIRED"}) {
            MvcResult result = patchStatus(f.hr().token(), f.applicationId(), target);

            assertThat(result.getResponse().getStatus()).as(target).isEqualTo(400);
            assertThat(errorCode(result)).as(target).isEqualTo("INVALID_APPLICATION_STATUS_TRANSITION");
        }
        assertThat(applicationStatus(f.applicationId())).isEqualTo("REJECTED");
        assertThat(requestStatus(f.applicationId())).isEqualTo("OPEN");
        assertThat(countHistory(f.applicationId())).isEqualTo(2L);
    }

    // T12 (Plan Mode L2 sau dot 4) - goi THANG changeStatus, bo qua guard PATCH o controller: bang chuyen tiep chung van
    // khong cho REJECTED -> INTERVIEW_INVITED, ke ca khi don co yeu cau OPEN. Ca nay do HANH VI cua bang (T13 do van ban).
    @Test
    void t12_changeStatusDirectly_rejectedToInterviewInvited_stillInvalid() throws Exception {
        Fixture f = openRequest("c09-t12b", null);
        long historyBefore = countHistory(f.applicationId());

        assertThatThrownBy(() -> applicationStatusService.changeStatus(
                        f.hr().id(), f.applicationId(), ApplicationStatus.INTERVIEW_INVITED))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class)
                .hasMessage(transitionMessage("REJECTED"));

        assertThat(applicationStatus(f.applicationId())).isEqualTo("REJECTED");
        assertThat(requestStatus(f.applicationId())).isEqualTo("OPEN");
        assertThat(countHistory(f.applicationId())).isEqualTo(historyBefore);
        assertThat(countInvitations(f.applicationId())).isZero();
        assertThat(countNotificationsForApplication(f.applicationId())).isZero();
    }

    // ---- T24: funnel FR-H08 dem don duoc moi lai MOT lan ----

    @Test
    void t24_funnel_countsReinvitedApplicationOnce() throws Exception {
        Fixture f = openRequest("c09-t24", null);
        sendInvitation(f.hr().token(), f.applicationId());
        // Sau khi duoc moi lai, bi tu choi lan nua (INTERVIEW_INVITED -> REJECTED hop le qua PATCH).
        assertThat(patchStatus(f.hr().token(), f.applicationId(), "REJECTED").getResponse().getStatus())
                .isEqualTo(200);

        FunnelCountsView funnel = jobApplicationRepository.countFunnelForCompany(f.hr().companyId());

        assertThat(funnel.getTotalApplications()).isEqualTo(1L);
        assertThat(funnel.getEverInvited()).isEqualTo(1L);
        assertThat(funnel.getEverHired()).isZero();
    }
}

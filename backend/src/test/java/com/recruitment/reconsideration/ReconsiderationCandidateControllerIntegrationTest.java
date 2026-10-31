package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.common.exception.ReconsiderationDeadlinePassedException;
import com.recruitment.reconsideration.dto.CandidateReconsiderationResponse;
import com.recruitment.reconsideration.dto.CandidateReconsiderationState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;

// FR-C09 C1 + khoi E1 ung vien + danh sach don ung vien - T1-T4, T6-T9, T19-T21 cua REQUIREMENT muc 7.1 (T5 o
// ReconsiderationRequestConcurrencyIntegrationTest, T22 phan C1 o ReconsiderationNotificationIntegrationTest).
// T1-T3: bien thoi gian dung CUNG mot transaction cho buoc backdate va loi goi service (TransactionTemplate; service
// @Transactional tham gia transaction ngoai) vi now() theo transaction (CLAUDE.md muc 3c) - now() cua cau kiem han bang
// dung now() luc backdate.
class ReconsiderationCandidateControllerIntegrationTest extends ReconsiderationIntegrationTestSupport {

    private static final List<String> CANDIDATE_BLOCK_KEYS =
            List.of("state", "deadline", "requestedAt", "reason", "decidedAt", "hrResponse");

    @Autowired
    private ReconsiderationCandidateService service;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // Backdate dong PENDING -> REJECTED roi goi C1 trong CUNG transaction.
    private CandidateReconsiderationResponse requestWithRejectionAt(Fixture f, String interval, String reason) {
        return transactionTemplate.execute(status -> {
            backdateRejection(f.applicationId(), interval);
            return service.request(f.candidate().id(), f.applicationId(), reason);
        });
    }

    // ---- T1-T3: bien han 168 gio ----

    @Test
    void t1_oneSecondBeforeDeadline_created() throws Exception {
        Fixture f = rejectedAtScreening("c09-t1");

        CandidateReconsiderationResponse response =
                requestWithRejectionAt(f, "INTERVAL '168 hours' + INTERVAL '1 second'", "Ly do\nhai dong");

        assertThat(response.state()).isEqualTo(CandidateReconsiderationState.OPEN);
        assertThat(response.requestedAt()).isNotNull();
        assertThat(response.reason()).isEqualTo("Ly do\nhai dong");
        Map<String, Object> row = requestRow(f.applicationId());
        assertThat(row.get("reason")).isEqualTo("Ly do\nhai dong");
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("requested_at")).isNotNull();
        assertThat(row.get("decided_at")).isNull();
        assertThat(row.get("decided_by")).isNull();
    }

    @Test
    void t2_exactlyAtDeadline_created() throws Exception {
        Fixture f = rejectedAtScreening("c09-t2");

        CandidateReconsiderationResponse response = requestWithRejectionAt(f, "INTERVAL '168 hours'", null);

        assertThat(response.state()).isEqualTo(CandidateReconsiderationState.OPEN);
        assertThat(countRequests(f.applicationId())).isEqualTo(1L);
    }

    @Test
    void t3_oneSecondAfterDeadline_deadlinePassed_noRow() throws Exception {
        Fixture f = rejectedAtScreening("c09-t3");

        assertThatThrownBy(
                        () -> requestWithRejectionAt(f, "INTERVAL '168 hours' - INTERVAL '1 second'", "Qua han"))
                .isInstanceOf(ReconsiderationDeadlinePassedException.class);
        assertThat(countRequests(f.applicationId())).isZero();
    }

    // Qua HTTP: qua han -> 409 dung ma, dung cau.
    @Test
    void t3_http_eightDaysAgo_returns409DeadlinePassed() throws Exception {
        Fixture f = rejectedAtScreening("c09-t3h");
        backdateRejection(f.applicationId(), "INTERVAL '8 days'");

        MvcResult result = postReason(f.candidate().token(), f.applicationId(), "x");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("RECONSIDERATION_DEADLINE_PASSED");
        assertThat(errorMessage(result)).isEqualTo("Đã quá hạn 7 ngày để gửi yêu cầu xem xét lại.");
        assertThat(countRequests(f.applicationId())).isZero();
    }

    // ---- T4: mot lan cho moi don ----

    @Test
    void t4_secondRequest_and_afterDeclined_return409AlreadyRequested() throws Exception {
        Fixture f = rejectedAtScreening("c09-t4");

        MvcResult first = postReason(f.candidate().token(), f.applicationId(), "Lan mot");
        MvcResult second = postReason(f.candidate().token(), f.applicationId(), "Lan hai");

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("RECONSIDERATION_ALREADY_REQUESTED");
        assertThat(errorMessage(second))
                .isEqualTo("Bạn đã gửi yêu cầu xem xét lại cho đơn này. Mỗi đơn chỉ gửi được một lần.");
        assertThat(countRequests(f.applicationId())).isEqualTo(1L);
        assertThat(requestRow(f.applicationId()).get("reason")).isEqualTo("Lan mot");

        jdbcTemplate.update(
                """
                UPDATE application_reconsideration_requests SET status = 'DECLINED', decided_at = now(), decided_by = ?
                WHERE application_id = ?
                """,
                f.hr().id(),
                f.applicationId());
        MvcResult afterDeclined = postReason(f.candidate().token(), f.applicationId(), "Lan ba");

        assertThat(afterDeclined.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(afterDeclined)).isEqualTo("RECONSIDERATION_ALREADY_REQUESTED");
        assertThat(countRequests(f.applicationId())).isEqualTo(1L);
    }

    // ---- T6: khong du dieu kien ----

    @Test
    void t6_rejectedAfterInterview_returns409NotEligible() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t6-hr");
        Account candidate = registerCandidate("c09-t6-c");
        UUID applicationId = insertApplication(candidate.id(), insertOpenJob(hr), "REJECTED");
        history(applicationId, null, "PENDING", candidate.id());
        history(applicationId, "PENDING", "INTERVIEW_INVITED", hr.id());
        history(applicationId, "INTERVIEW_INVITED", "REJECTED", hr.id());

        MvcResult result = postReason(candidate.token(), applicationId, "x");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("RECONSIDERATION_NOT_ELIGIBLE");
        assertThat(errorMessage(result))
                .isEqualTo("Chỉ đơn bị từ chối ở vòng hồ sơ mới gửi được yêu cầu xem xét lại.");
        assertThat(countRequests(applicationId)).isZero();
    }

    @Test
    void t6_otherStatuses_and_rejectedWithoutHistory_return409NotEligible() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t6b-hr");
        Account candidate = registerCandidate("c09-t6b-c");
        for (String status : List.of("PENDING", "INTERVIEW_INVITED", "HIRED", "WITHDRAWN", "REJECTED")) {
            // Moi don mot tin rieng (uq_application_per_cycle). Don REJECTED cuoi cung KHONG co dong lich su (R-E3).
            UUID applicationId = insertApplication(candidate.id(), insertOpenJob(hr), status);
            if (!status.equals("REJECTED")) {
                history(applicationId, null, "PENDING", candidate.id());
            }

            MvcResult result = postReason(candidate.token(), applicationId, null);

            assertThat(result.getResponse().getStatus()).as(status).isEqualTo(409);
            assertThat(errorCode(result)).as(status).isEqualTo("RECONSIDERATION_NOT_ELIGIBLE");
            assertThat(countRequests(applicationId)).as(status).isZero();
        }
    }

    // ---- T7: tinh trang tin ----

    @Test
    void t7_expiredPausedClosedJob_created_deletedJob_returns409JobRemoved() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t7-hr");
        Account candidate = registerCandidate("c09-t7-c");
        UUID expired = insertJob(hr, "OPEN", -1, false);
        UUID paused = insertJob(hr, "PAUSED", null, false);
        UUID closed = insertJob(hr, "CLOSED", null, false);
        UUID deleted = insertJob(hr, "OPEN", null, true);

        for (UUID jobId : List.of(expired, paused, closed)) {
            Fixture f = rejectedAtScreening(hr, candidate, jobId);
            assertThat(postReason(candidate.token(), f.applicationId(), "x").getResponse().getStatus())
                    .isEqualTo(201);
        }
        Fixture removed = rejectedAtScreening(hr, candidate, deleted);
        MvcResult result = postReason(candidate.token(), removed.applicationId(), "x");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("RECONSIDERATION_JOB_REMOVED");
        assertThat(errorMessage(result))
                .isEqualTo("Tin tuyển dụng của đơn này đã bị gỡ nên không gửi được yêu cầu xem xét lại.");
        assertThat(countRequests(removed.applicationId())).isZero();
    }

    // ---- T8: van ban ----

    @Test
    void t8_lengthBoundaries() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t8-hr");
        Account candidate = registerCandidate("c09-t8-c");
        Fixture f999 = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        Fixture f1000 = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        Fixture f1001 = rejectedAtScreening(hr, candidate, insertOpenJob(hr));

        assertThat(postReason(candidate.token(), f999.applicationId(), "a".repeat(999)).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(postReason(candidate.token(), f1000.applicationId(), "a".repeat(1000)).getResponse().getStatus())
                .isEqualTo(201);
        MvcResult tooLong = postReason(candidate.token(), f1001.applicationId(), "a".repeat(1001));

        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(tooLong)).isEqualTo("RECONSIDERATION_TEXT_TOO_LONG");
        assertThat(errorMessage(tooLong)).isEqualTo("Nội dung tối đa 1000 ký tự.");
        assertThat(countRequests(f1001.applicationId())).isZero();
    }

    // 999 ky tu + CRLF -> 1000 ky tu ket thuc \n (do dai SAU chuan hoa).
    @Test
    void t8_crlfCountedAfterNormalization() throws Exception {
        Fixture f = rejectedAtScreening("c09-t8b");

        MvcResult result = postReason(f.candidate().token(), f.applicationId(), "a".repeat(999) + "\r\n");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String stored = (String) requestRow(f.applicationId()).get("reason");
        assertThat(stored).hasSize(1000).endsWith("\n").doesNotContain("\r");
    }

    // Khong trim, khong escape, CR don le cung thanh LF.
    @Test
    void t8_storedVerbatimExceptLineBreaks() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t8c-hr");
        Account candidate = registerCandidate("c09-t8c-c");
        Fixture html = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        Fixture cr = rejectedAtScreening(hr, candidate, insertOpenJob(hr));

        postReason(candidate.token(), html.applicationId(), "  <b>x</b> & y\r\n");
        postReason(candidate.token(), cr.applicationId(), "a\rb");

        assertThat(requestRow(html.applicationId()).get("reason")).isEqualTo("  <b>x</b> & y\n");
        assertThat(requestRow(cr.applicationId()).get("reason")).isEqualTo("a\nb");
    }

    // Chi khoang trang / null / thieu khoa / khong body -> 201, reason NULL.
    @Test
    void t8_blankNullMissingKeyOrNoBody_storeNull() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t8d-hr");
        Account candidate = registerCandidate("c09-t8d-c");
        List<String> bodies = new ArrayList<>(List.of("{\"reason\":\"   \"}", "{\"reason\":null}", "{}"));
        bodies.add(null);

        for (String body : bodies) {
            Fixture f = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
            MvcResult result = postRequest(candidate.token(), f.applicationId(), body);

            assertThat(result.getResponse().getStatus()).as(String.valueOf(body)).isEqualTo(201);
            assertThat(requestRow(f.applicationId()).get("reason")).as(String.valueOf(body)).isNull();
            assertThat(body(result).get("reason").isNull()).isTrue();
        }
    }

    // R-E5 - trang thai truoc noi dung: don khong du dieu kien + ly do qua dai -> 409, khong phai 400.
    @Test
    void ruleE5_stateCheckedBeforeText() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-e5-hr");
        Account candidate = registerCandidate("c09-e5-c");
        UUID pending = insertApplication(candidate.id(), insertOpenJob(hr), "PENDING");
        Fixture expired = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        backdateRejection(expired.applicationId(), "INTERVAL '8 days'");
        Fixture removed = rejectedAtScreening(hr, candidate, insertJob(hr, "OPEN", null, true));
        backdateRejection(removed.applicationId(), "INTERVAL '8 days'");

        assertThat(errorCode(postReason(candidate.token(), pending, "a".repeat(1001))))
                .isEqualTo("RECONSIDERATION_NOT_ELIGIBLE");
        assertThat(errorCode(postReason(candidate.token(), expired.applicationId(), "a".repeat(1001))))
                .isEqualTo("RECONSIDERATION_DEADLINE_PASSED");
        // Tin da xoa VA qua han -> JOB_REMOVED (kiem tin truoc han).
        assertThat(errorCode(postReason(candidate.token(), removed.applicationId(), "x")))
                .isEqualTo("RECONSIDERATION_JOB_REMOVED");
    }

    // ---- T9: quyen ----

    @Test
    void t9_otherCandidatesApplication_and_unknownId_return404() throws Exception {
        Fixture f = rejectedAtScreening("c09-t9");
        Account other = registerCandidate("c09-t9-other");

        MvcResult foreign = postReason(other.token(), f.applicationId(), "x");
        MvcResult unknown = postReason(other.token(), UUID.randomUUID(), "x");

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(foreign)).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(unknown)).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(countRequests(f.applicationId())).isZero();
    }

    @Test
    void t9_hrForbidden_anonymousUnauthorized() throws Exception {
        Fixture f = rejectedAtScreening("c09-t9b");

        assertThat(postReason(f.hr().token(), f.applicationId(), "x").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(postReason(null, f.applicationId(), "x").getResponse().getStatus()).isEqualTo(401);
        assertThat(countRequests(f.applicationId())).isZero();
    }

    // ---- T19: DTO ung vien ----

    @Test
    void t19_candidateBlock_hasExactlySixKeys_noInternalData() throws Exception {
        Fixture f = rejectedAtScreening("c09-t19");

        MvcResult created = postReason(f.candidate().token(), f.applicationId(), "Ly do");
        JsonNode detailBlock = body(getDetail(f.candidate().token(), f.applicationId())).get("reconsideration");
        jdbcTemplate.update(
                """
                UPDATE application_reconsideration_requests
                SET status = 'DECLINED', decided_at = now(), decided_by = ?, hr_response = 'Phan hoi'
                WHERE application_id = ?
                """,
                f.hr().id(),
                f.applicationId());
        JsonNode declinedBlock = body(getDetail(f.candidate().token(), f.applicationId())).get("reconsideration");

        for (JsonNode block : List.of(body(created), detailBlock, declinedBlock)) {
            assertThat(keysOf(block)).containsExactlyInAnyOrderElementsOf(CANDIDATE_BLOCK_KEYS);
            String raw = block.toString();
            assertThat(raw).doesNotContain(f.hr().id().toString());
        }
        List<String> allKeys = new ArrayList<>();
        collectKeys(body(getDetail(f.candidate().token(), f.applicationId())), allKeys);
        assertThat(allKeys).doesNotContain("decidedBy", "identityMasked", "candidateName");
    }

    // ---- T20: khoi E1 ung vien theo 6 state + null ----

    @Test
    void t20_available_deadlineIsRejection168Hours() throws Exception {
        Fixture f = rejectedAtScreening("c09-t20a");

        JsonNode block = body(getDetail(f.candidate().token(), f.applicationId())).get("reconsideration");

        assertThat(block.get("state").asString()).isEqualTo("AVAILABLE");
        assertThat(Instant.parse(block.get("deadline").asString())).isEqualTo(dbDeadline(f.applicationId()));
        assertThat(block.get("requestedAt").isNull()).isTrue();
        assertThat(block.get("reason").isNull()).isTrue();
        assertThat(block.get("decidedAt").isNull()).isTrue();
        assertThat(block.get("hrResponse").isNull()).isTrue();
    }

    @Test
    void t20_expired_and_jobRemoved() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t20b-hr");
        Account candidate = registerCandidate("c09-t20b-c");
        Fixture expired = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        backdateRejection(expired.applicationId(), "INTERVAL '8 days'");
        Fixture removed = rejectedAtScreening(hr, candidate, insertJob(hr, "OPEN", null, true));

        JsonNode expiredBlock = body(getDetail(candidate.token(), expired.applicationId())).get("reconsideration");
        JsonNode removedBlock = body(getDetail(candidate.token(), removed.applicationId())).get("reconsideration");

        assertThat(expiredBlock.get("state").asString()).isEqualTo("EXPIRED");
        assertThat(Instant.parse(expiredBlock.get("deadline").asString()))
                .isEqualTo(dbDeadline(expired.applicationId()));
        assertThat(removedBlock.get("state").asString()).isEqualTo("JOB_REMOVED");
        assertThat(removedBlock.get("deadline").isNull()).isFalse();
    }

    @Test
    void t20_open_accepted_declined() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t20c-hr");
        Account candidate = registerCandidate("c09-t20c-c");
        Fixture open = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertRequest(open.applicationId(), "OPEN", null, "Ly do mo", null);
        Fixture declined = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertRequest(declined.applicationId(), "DECLINED", hr.id(), "Ly do", "Phan hoi\nnguyen van");

        // Duoc moi lai roi trung tuyen: van thay ACCEPTED (khoi hien o MOI trang thai don khi co yeu cau).
        Fixture hired = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertRequest(hired.applicationId(), "ACCEPTED", hr.id(), null, null);
        history(hired.applicationId(), "REJECTED", "INTERVIEW_INVITED", hr.id());
        history(hired.applicationId(), "INTERVIEW_INVITED", "HIRED", hr.id());
        jdbcTemplate.update("UPDATE job_applications SET status = 'HIRED' WHERE id = ?", hired.applicationId());

        JsonNode openBlock = body(getDetail(candidate.token(), open.applicationId())).get("reconsideration");
        JsonNode declinedBlock = body(getDetail(candidate.token(), declined.applicationId())).get("reconsideration");
        JsonNode hiredBlock = body(getDetail(candidate.token(), hired.applicationId())).get("reconsideration");

        assertThat(openBlock.get("state").asString()).isEqualTo("OPEN");
        assertThat(openBlock.get("reason").asString()).isEqualTo("Ly do mo");
        assertThat(openBlock.get("requestedAt").isNull()).isFalse();
        assertThat(openBlock.get("decidedAt").isNull()).isTrue();
        assertThat(Instant.parse(openBlock.get("deadline").asString())).isEqualTo(dbDeadline(open.applicationId()));

        assertThat(declinedBlock.get("state").asString()).isEqualTo("DECLINED");
        assertThat(declinedBlock.get("decidedAt").isNull()).isFalse();
        assertThat(declinedBlock.get("hrResponse").asString()).isEqualTo("Phan hoi\nnguyen van");

        assertThat(hiredBlock.get("state").asString()).isEqualTo("ACCEPTED");
        assertThat(hiredBlock.get("decidedAt").isNull()).isFalse();
        assertThat(hiredBlock.get("hrResponse").isNull()).isTrue();
        // B2 - han van la cua lan tu choi o vong ho so.
        assertThat(Instant.parse(hiredBlock.get("deadline").asString()))
                .isEqualTo(dbDeadline(hired.applicationId()));
    }

    // B2 - yeu cau ACCEPTED roi bi tu choi lan nua sau phong van: khoi van ACCEPTED, han cua lan tu choi o vong ho so.
    @Test
    void t20_acceptedThenRejectedAfterInterview_keepsScreeningDeadline() throws Exception {
        Fixture f = rejectedAtScreening("c09-t20d");
        backdateRejection(f.applicationId(), "INTERVAL '3 days'");
        insertRequest(f.applicationId(), "ACCEPTED", f.hr().id(), null, null);
        history(f.applicationId(), "REJECTED", "INTERVIEW_INVITED", f.hr().id());
        history(f.applicationId(), "INTERVIEW_INVITED", "REJECTED", f.hr().id());

        JsonNode block = body(getDetail(f.candidate().token(), f.applicationId())).get("reconsideration");
        MvcResult again = postReason(f.candidate().token(), f.applicationId(), "x");

        assertThat(block.get("state").asString()).isEqualTo("ACCEPTED");
        assertThat(Instant.parse(block.get("deadline").asString())).isEqualTo(dbDeadline(f.applicationId()));
        assertThat(errorCode(again)).isEqualTo("RECONSIDERATION_ALREADY_REQUESTED");
    }

    @Test
    void t20_nullBlock_forPendingAndRejectedAfterInterview() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t20e-hr");
        Account candidate = registerCandidate("c09-t20e-c");
        UUID pending = insertApplication(candidate.id(), insertOpenJob(hr), "PENDING");
        history(pending, null, "PENDING", candidate.id());
        UUID afterInterview = insertApplication(candidate.id(), insertOpenJob(hr), "REJECTED");
        history(afterInterview, null, "PENDING", candidate.id());
        history(afterInterview, "PENDING", "INTERVIEW_INVITED", hr.id());
        history(afterInterview, "INTERVIEW_INVITED", "REJECTED", hr.id());

        JsonNode pendingJson = body(getDetail(candidate.token(), pending));
        JsonNode afterInterviewJson = body(getDetail(candidate.token(), afterInterview));

        assertThat(pendingJson.has("reconsideration")).isTrue();
        assertThat(pendingJson.get("reconsideration").isNull()).isTrue();
        assertThat(afterInterviewJson.get("reconsideration").isNull()).isTrue();
    }

    // ---- T21: danh sach don ung vien ----

    @Test
    void t21_listReconsiderationStatus_perApplication() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t21-hr");
        Account candidate = registerCandidate("c09-t21-c");
        UUID none = insertApplication(candidate.id(), insertOpenJob(hr), "PENDING");
        Fixture open = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertRequest(open.applicationId(), "OPEN", null, null, null);
        Fixture declined = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertRequest(declined.applicationId(), "DECLINED", hr.id(), null, null);

        JsonNode list = body(getList(candidate.token()));

        Map<String, JsonNode> byId = new java.util.HashMap<>();
        for (JsonNode item : list.values()) {
            byId.put(item.get("id").asString(), item);
        }
        assertThat(byId).hasSize(3);
        assertThat(byId.get(none.toString()).get("reconsiderationStatus").isNull()).isTrue();
        assertThat(byId.get(open.applicationId().toString()).get("reconsiderationStatus").asString())
                .isEqualTo("OPEN");
        assertThat(byId.get(declined.applicationId().toString()).get("reconsiderationStatus").asString())
                .isEqualTo("DECLINED");
    }

    // So cau lenh cua danh sach khong tang theo so don (mot cau, LEFT JOIN - khuon FR-H14 T30).
    @Test
    void t21_statementCountDoesNotGrowWithApplications() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t21b-hr");
        Account candidate = registerCandidate("c09-t21b-c");
        Fixture first = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        insertRequest(first.applicationId(), "OPEN", null, null, null);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        assertThat(getList(candidate.token()).getResponse().getStatus()).isEqualTo(200);
        long n1 = statistics.getPrepareStatementCount();

        for (int i = 0; i < 10; i++) {
            Fixture extra = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
            insertRequest(extra.applicationId(), i % 2 == 0 ? "OPEN" : "DECLINED", i % 2 == 0 ? null : hr.id(), null, null);
        }
        statistics.clear();
        MvcResult result = getList(candidate.token());
        long n11 = statistics.getPrepareStatementCount();

        assertThat(body(result).size()).isEqualTo(11);
        assertThat(n11).as("So cau lenh: 1 don = %d, 11 don = %d", n1, n11).isEqualTo(n1);
    }

    // ORDER BY applied_at DESC, id DESC - hai don cung applied_at van co thu tu on dinh (khoa cuoi id).
    @Test
    void t21_orderHasUniqueTieBreaker() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t21c-hr");
        Account candidate = registerCandidate("c09-t21c-c");
        UUID a = insertApplication(candidate.id(), insertOpenJob(hr), "PENDING");
        UUID b = insertApplication(candidate.id(), insertOpenJob(hr), "PENDING");
        jdbcTemplate.update(
                "UPDATE job_applications SET applied_at = TIMESTAMPTZ '2026-10-01 10:00:00+07' WHERE id IN (?, ?)", a, b);

        List<String> ids = new ArrayList<>();
        for (JsonNode item : body(getList(candidate.token())).values()) {
            ids.add(item.get("id").asString());
        }
        List<String> expected = new ArrayList<>(List.of(a.toString(), b.toString()));
        expected.sort(java.util.Comparator.reverseOrder());

        assertThat(ids).containsExactlyElementsOf(expected);
    }
}

package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.recruitment.common.exception.InvalidApplicationStatusTransitionException;
import com.recruitment.common.exception.ReconsiderationNotOpenException;
import com.recruitment.interviewinvitation.InterviewInvitationService;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest.SlotRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

// FR-C09 T15 (R-D3) - tranh chap quyet dinh tren CUNG mot yeu cau: chi mot ben thang, ben thua 409
// RECONSIDERATION_NOT_OPEN dung cau muc 4.3, khong 500, khong ghi gi. Lop KHONG @Transactional. Hai dang (REQUIREMENT muc
// 7.4, khuon SavedJobConcurrencyIntegrationTest):
// (a) tat dinh - ben A (service) ghi trong TransactionTemplate chua commit; ben B di qua HTTP (MockMvc) va bi chan o khoa
//     hang yeu cau - xac nhan bang pg_stat_activity; A commit -> B nhan 409 (kiem ma + cau o tang web);
// (b) CyclicBarrier hai luong goi service cung luc, lap ROUNDS vong, du lieu moi moi vong.
class ReconsiderationDecisionConcurrencyIntegrationTest extends ReconsiderationIntegrationTestSupport {

    static final int ROUNDS = 20;
    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;
    private static final long LOCK_POLL_INTERVAL_MS = 50;
    private static final String NOT_OPEN_MESSAGE = "Yêu cầu xem xét lại này không còn chờ xử lý. Vui lòng tải lại trang.";

    @Autowired
    private ReconsiderationHrService hrService;

    @Autowired
    private InterviewInvitationService invitationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // NOT_OPEN = thua tranh chap (409, R-D3). INVALID_TRANSITION chi hop le cho ben I2 trong dang lap: hai luong khong thuc
    // su chong nhau, I2 chay SAU khi ben kia da commit -> doc thay don/yeu cau da doi -> 400 nhu FR-H12 (R-S3 (0)).
    private enum Outcome {
        WON,
        NOT_OPEN,
        INVALID_TRANSITION
    }

    private static InterviewInvitationSendRequest invitationRequest() {
        Instant slot = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MINUTES);
        return new InterviewInvitationSendRequest(
                List.of(new SlotRequest(slot, "Phong 1")), "Thu moi phong van", "Kinh moi ban tham gia phong van.");
    }

    private void invite(Fixture f) {
        invitationService.sendInvitation(f.hr().id(), f.applicationId(), invitationRequest());
    }

    private void declineService(Fixture f, String response) {
        hrService.decline(f.hr().id(), f.applicationId(), response);
    }

    private static Outcome attempt(Runnable action) {
        try {
            action.run();
            return Outcome.WON;
        } catch (ReconsiderationNotOpenException e) {
            return Outcome.NOT_OPEN;
        } catch (InvalidApplicationStatusTransitionException e) {
            return Outcome.INVALID_TRANSITION;
        }
    }

    // Cho toi khi CO phien khac dang doi khoa o cau UPDATE bang yeu cau (U09 L9: clear snapshot truoc moi lan tham do).
    private void awaitOtherSessionBlockedOnRequestRow() {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
            Integer waiting = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%UPDATE application_reconsideration_requests%'
                    """,
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Bi ngat khi cho ben thu hai bi khoa chan");
            }
        }
        fail("Ben thu hai khong bi khoa chan sau " + LOCK_WAIT_TIMEOUT_MS + "ms");
    }

    // A (service) giu transaction; B (HTTP) bi chan; A commit; tra ve response cua B.
    private MvcResult runDeterministic(Runnable winner, Callable<MvcResult> loserHttp) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<MvcResult>> futureB = new AtomicReference<>();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                winner.run();
                futureB.set(executor.submit(loserHttp));
                awaitOtherSessionBlockedOnRequestRow();
                assertThat(futureB.get().isDone()).isFalse();
            });
            return futureB.get().get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertLoserHttp(MvcResult loser) throws Exception {
        assertThat(loser.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(loser)).isEqualTo("RECONSIDERATION_NOT_OPEN");
        assertThat(errorMessage(loser)).isEqualTo(NOT_OPEN_MESSAGE);
    }

    private void assertDeclinedState(Fixture f, String expectedResponse) {
        assertThat(requestStatus(f.applicationId())).isEqualTo("DECLINED");
        assertThat(requestRow(f.applicationId()).get("hr_response")).isEqualTo(expectedResponse);
        assertThat(applicationStatus(f.applicationId())).isEqualTo("REJECTED");
        assertThat(countInvitations(f.applicationId())).isZero();
        assertThat(countHistory(f.applicationId(), "REJECTED", "INTERVIEW_INVITED")).isZero();
        assertThat(countNotifications(f.candidate().id(), "RECONSIDERATION_DECLINED", f.applicationId())).isEqualTo(1L);
        assertThat(countNotificationsForApplication(f.applicationId())).isEqualTo(1L);
    }

    private void assertAcceptedState(Fixture f) {
        assertThat(requestStatus(f.applicationId())).isEqualTo("ACCEPTED");
        assertThat(requestRow(f.applicationId()).get("hr_response")).isNull();
        assertThat(applicationStatus(f.applicationId())).isEqualTo("INTERVIEW_INVITED");
        assertThat(countInvitations(f.applicationId())).isEqualTo(1L);
        assertThat(countHistory(f.applicationId(), "REJECTED", "INTERVIEW_INVITED")).isEqualTo(1L);
        assertThat(countNotifications(f.candidate().id(), "APPLICATION_STATUS_CHANGED", f.applicationId()))
                .isEqualTo(1L);
        assertThat(countNotificationsForApplication(f.applicationId())).isEqualTo(1L);
    }

    // ---- (a) tat dinh, ben thua qua HTTP ----

    @Test
    void t15a_deterministic_declineVsDecline_secondGets409() throws Exception {
        Fixture f = openRequest("c09-t15a", null);

        MvcResult loser = runDeterministic(
                () -> declineService(f, "Ben A"), () -> declineWith(f.hr().token(), f.applicationId(), "Ben B"));

        assertLoserHttp(loser);
        assertDeclinedState(f, "Ben A");
    }

    @Test
    void t15b_deterministic_inviteVsInvite_secondGets409() throws Exception {
        Fixture f = openRequest("c09-t15b", null);

        MvcResult loser = runDeterministic(() -> invite(f), () -> sendInvitation(f.hr().token(), f.applicationId()));

        assertLoserHttp(loser);
        assertAcceptedState(f);
    }

    @Test
    void t15c_deterministic_declineWins_inviteGets409() throws Exception {
        Fixture f = openRequest("c09-t15c1", null);

        MvcResult loser = runDeterministic(
                () -> declineService(f, "Giu nguyen"), () -> sendInvitation(f.hr().token(), f.applicationId()));

        assertLoserHttp(loser);
        assertDeclinedState(f, "Giu nguyen");
    }

    @Test
    void t15c_deterministic_inviteWins_declineGets409() throws Exception {
        Fixture f = openRequest("c09-t15c2", null);

        MvcResult loser =
                runDeterministic(() -> invite(f), () -> declineWith(f.hr().token(), f.applicationId(), "Muon"));

        assertLoserHttp(loser);
        assertAcceptedState(f);
    }

    // ---- (b) CyclicBarrier, ROUNDS vong ----

    private List<Outcome> runTogether(Runnable a, Runnable b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Outcome> fa = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return attempt(a);
            });
            Future<Outcome> fb = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return attempt(b);
            });
            return List.of(fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void t15a_concurrentDeclines_exactlyOneWins() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t15ra-hr");
        Account candidate = registerCandidate("c09-t15ra-c");
        List<Integer> rounds = new ArrayList<>();
        for (int round = 1; round <= ROUNDS; round++) {
            Fixture f = openRequest(hr, candidate, null);

            List<Outcome> outcomes = runTogether(() -> declineService(f, "A"), () -> declineService(f, "B"));

            assertThat(outcomes).as("vong %d", round).containsExactlyInAnyOrder(Outcome.WON, Outcome.NOT_OPEN);
            String winner = outcomes.get(0) == Outcome.WON ? "A" : "B";
            assertDeclinedState(f, winner);
            rounds.add(round);
        }
        assertThat(rounds).hasSize(ROUNDS);
    }

    @Test
    void t15b_concurrentInvites_exactlyOneWins() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t15rb-hr");
        Account candidate = registerCandidate("c09-t15rb-c");
        int contended = 0;
        for (int round = 1; round <= ROUNDS; round++) {
            Fixture f = openRequest(hr, candidate, null);

            List<Outcome> outcomes = runTogether(() -> invite(f), () -> invite(f));

            assertThat(outcomes).as("vong %d", round).containsOnlyOnce(Outcome.WON);
            assertThat(outcomes).as("vong %d", round).containsAnyOf(Outcome.NOT_OPEN, Outcome.INVALID_TRANSITION);
            contended += outcomes.contains(Outcome.NOT_OPEN) ? 1 : 0;
            assertAcceptedState(f);
        }
        System.out.printf("FR-C09 T15(b): %d/%d vong ben thua 409 (chong nhau that), con lai 400%n", contended, ROUNDS);
    }

    @Test
    void t15c_concurrentInviteAndDecline_exactlyOneWins() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t15rc-hr");
        Account candidate = registerCandidate("c09-t15rc-c");
        int inviteWins = 0;
        int declineWins = 0;
        for (int round = 1; round <= ROUNDS; round++) {
            Fixture f = openRequest(hr, candidate, null);

            List<Outcome> outcomes = runTogether(() -> invite(f), () -> declineService(f, "Giu nguyen"));

            if (outcomes.get(0) == Outcome.WON) {
                // I2 thang: H2 luon 409 (UPDATE co dieu kien 0 dong).
                assertThat(outcomes.get(1)).as("vong %d", round).isEqualTo(Outcome.NOT_OPEN);
                assertAcceptedState(f);
                inviteWins++;
            } else {
                // H2 thang: I2 409 neu chong nhau that, 400 neu doc yeu cau sau khi H2 da commit.
                assertThat(outcomes.get(1)).as("vong %d", round).isEqualTo(Outcome.WON);
                assertThat(outcomes.get(0)).as("vong %d", round).isIn(Outcome.NOT_OPEN, Outcome.INVALID_TRANSITION);
                assertDeclinedState(f, "Giu nguyen");
                declineWins++;
            }
        }
        assertThat(inviteWins + declineWins).isEqualTo(ROUNDS);
        System.out.printf("FR-C09 T15(c): I2 thang %d vong, H2 thang %d vong%n", inviteWins, declineWins);
    }
}

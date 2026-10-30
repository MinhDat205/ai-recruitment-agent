package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

// FR-C09 T5 (R-E2) - hai C1 dong thoi cho CUNG mot don: dung mot 201 + mot 409 RECONSIDERATION_ALREADY_REQUESTED voi
// dung cau o muc 4.3, KHONG 500, mot dong, mot thong bao. Di qua HTTP (MockMvc) vi "409 chu khong 500" chi kiem duoc o
// tang web (GlobalExceptionHandler dich uq_reconsideration_per_application). Lop KHONG @Transactional. Hai dang (khuon
// SavedJobConcurrencyIntegrationTest, REQUIREMENT muc 7.4):
// (a) tat dinh - luong A ghi trong TransactionTemplate chua commit; request B qua buoc kiem truoc (chua thay dong cua A)
//     roi bi chan o unique index - xac nhan bang pg_stat_activity (khong cho theo thoi gian); A commit -> B thua;
// (b) CyclicBarrier hai request xuat phat cung luc, lap ROUNDS vong, du lieu moi moi vong.
class ReconsiderationRequestConcurrencyIntegrationTest extends ReconsiderationIntegrationTestSupport {

    static final int ROUNDS = 20;
    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;
    private static final long LOCK_POLL_INTERVAL_MS = 50;
    private static final String ALREADY_REQUESTED_MESSAGE =
            "Bạn đã gửi yêu cầu xem xét lại cho đơn này. Mỗi đơn chỉ gửi được một lần.";

    @Autowired
    private ReconsiderationCandidateService service;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // Cho toi khi CO phien khac dang doi khoa o cau INSERT vao bang yeu cau (U09 L9: clear snapshot truoc moi lan tham do).
    private void awaitOtherSessionBlockedOnInsert() {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
            Integer waiting = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%INSERT INTO application_reconsideration_requests%'
                    """,
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Bi ngat khi cho request thu hai bi khoa chan");
            }
        }
        fail("Request thu hai khong bi khoa chan sau " + LOCK_WAIT_TIMEOUT_MS + "ms");
    }

    private void assertLoser(MvcResult loser) throws Exception {
        assertThat(loser.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(loser)).isEqualTo("RECONSIDERATION_ALREADY_REQUESTED");
        assertThat(errorMessage(loser)).isEqualTo(ALREADY_REQUESTED_MESSAGE);
    }

    // ---- T5 (a) - tat dinh ----
    @Test
    void t5_deterministic_secondRequestBlocksOnUniqueThenGets409() throws Exception {
        Fixture f = rejectedAtScreening("c09-t5a");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<MvcResult>> futureB = new AtomicReference<>();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                service.request(f.candidate().id(), f.applicationId(), "Request A");
                futureB.set(executor.submit(() -> postReason(f.candidate().token(), f.applicationId(), "Request B")));
                awaitOtherSessionBlockedOnInsert();
                assertThat(futureB.get().isDone()).isFalse();
            });

            assertLoser(futureB.get().get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(countRequests(f.applicationId())).isEqualTo(1L);
        assertThat(requestRow(f.applicationId()).get("reason")).isEqualTo("Request A");
        assertThat(countNotifications(f.hr().id(), "RECONSIDERATION_REQUESTED", f.applicationId())).isEqualTo(1L);
    }

    // ---- T5 (b) - CyclicBarrier, ROUNDS vong ----
    @Test
    void t5_concurrentRequests_exactlyOneCreated_otherGets409_neverServerError() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t5b-hr");
        Account candidate = registerCandidate("c09-t5b-c");
        List<Integer> completedRounds = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            Fixture f = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
            List<MvcResult> results = runTogether(candidate.token(), f.applicationId());

            List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
            assertThat(statuses).as("vong %d", round).containsExactly(201, 409);
            assertLoser(results.stream()
                    .filter(r -> r.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow());
            assertThat(countRequests(f.applicationId())).as("vong %d", round).isEqualTo(1L);
            assertThat(countNotifications(hr.id(), "RECONSIDERATION_REQUESTED", f.applicationId()))
                    .as("vong %d", round)
                    .isEqualTo(1L);
            completedRounds.add(round);
        }
        assertThat(completedRounds).hasSize(ROUNDS);
    }

    private List<MvcResult> runTogether(String token, UUID applicationId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<MvcResult> a = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return postReason(token, applicationId, "Luong A");
            });
            Future<MvcResult> b = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return postReason(token, applicationId, "Luong B");
            });
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}

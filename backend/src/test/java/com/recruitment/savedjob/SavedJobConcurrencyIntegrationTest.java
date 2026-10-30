package com.recruitment.savedjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.recruitment.common.exception.SavedJobLimitReachedException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// FR-U16 T15, T16 (R-L3, R-L4). Goi thang SavedJobService tu nhieu luong (khuon ApplicationTalentPoolConcurrencyIntegration
// Test): "204" = method tra ve binh thuong, "409" = SavedJobLimitReachedException. Moi test co hai dang:
// (a) tat dinh - luong A giu khoa trong TransactionTemplate, "B dang bi chan o FOR NO KEY UPDATE" xac nhan bang
//     pg_stat_activity (khong cho theo thoi gian);
// (b) CyclicBarrier hai luong xuat phat cung luc, lap ROUNDS vong de race lo ra neu thieu khoa.
class SavedJobConcurrencyIntegrationTest extends SavedJobIntegrationTestSupport {

    static final int ROUNDS = 20;
    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;
    private static final long LOCK_POLL_INTERVAL_MS = 50;

    @Autowired
    private SavedJobService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private enum Outcome {
        SAVED,
        LIMIT_REACHED
    }

    private Outcome trySave(UUID candidateId, UUID jobId) {
        try {
            service.save(candidateId, jobId);
            return Outcome.SAVED;
        } catch (SavedJobLimitReachedException e) {
            return Outcome.LIMIT_REACHED;
        }
    }

    // Cho toi khi CO phien khac dang doi khoa voi cau khop mau (U09 L9: clear snapshot truoc moi lan tham do).
    private void awaitOtherSessionBlockedOnLock() {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
            Integer waiting = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%FOR NO KEY UPDATE%'
                    """,
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Bi ngat khi cho phien thu hai bi khoa chan");
            }
        }
        fail("Phien thu hai khong bi khoa chan sau " + LOCK_WAIT_TIMEOUT_MS + "ms");
    }

    private List<Outcome> runTogether(UUID candidateId, UUID jobA, UUID jobB) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Outcome> a = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return trySave(candidateId, jobA);
            });
            Future<Outcome> b = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return trySave(candidateId, jobB);
            });
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- T15 (a) - hai PUT cung tin chua luu: B cho khoa cua A, sau do thay dong cua A -> idempotent ----
    @Test
    void t15_deterministic_secondSaveOfSameJobWaitsThenIsIdempotent() throws Exception {
        HrAccount hr = registerHrWithCompany("u16k-hr");
        UUID candidateId = insertCandidate();
        UUID jobId = insertOpenJob(hr);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Outcome>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.save(candidateId, jobId);
                futureB.set(executor.submit(() -> trySave(candidateId, jobId)));
                awaitOtherSessionBlockedOnLock();
                assertThat(futureB.get().isDone()).isFalse();
            });
            assertThat(futureB.get().get(5, TimeUnit.SECONDS)).isEqualTo(Outcome.SAVED);
        } finally {
            executor.shutdownNow();
        }
        assertThat(countRows(candidateId, jobId)).isEqualTo(1L);
        assertThat(countActive(candidateId)).isEqualTo(1L);
    }

    // ---- T15 (b) - CyclicBarrier, ROUNDS vong ----
    @Test
    void t15_barrier_twoSavesOfSameJob_bothSucceed_exactlyOneRow() throws Exception {
        HrAccount hr = registerHrWithCompany("u16k-hr");
        UUID jobId = insertOpenJob(hr);
        for (int round = 1; round <= ROUNDS; round++) {
            UUID candidateId = insertCandidate();

            List<Outcome> outcomes = runTogether(candidateId, jobId, jobId);

            assertThat(outcomes).as("vong %d", round).containsExactly(Outcome.SAVED, Outcome.SAVED);
            assertThat(countRows(candidateId, jobId)).as("vong %d", round).isEqualTo(1L);
        }
    }

    // ---- T16 (a) - 99 tin; A luu tin X (100, chua commit); B luu tin Y bi chan; A commit -> B 409 ----
    @Test
    void t16_deterministic_at99_secondSaveWaitsThenHitsLimit() throws Exception {
        HrAccount hr = registerHrWithCompany("u16k-hr");
        UUID candidateId = insertCandidate();
        insertActiveSaves(candidateId, insertOpenJobs(hr, SavedJobService.MAX_ACTIVE_SAVED_JOBS - 1));
        UUID jobX = insertOpenJob(hr);
        UUID jobY = insertOpenJob(hr);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Outcome>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.save(candidateId, jobX);
                futureB.set(executor.submit(() -> trySave(candidateId, jobY)));
                awaitOtherSessionBlockedOnLock();
                assertThat(futureB.get().isDone()).isFalse();
            });
            assertThat(futureB.get().get(5, TimeUnit.SECONDS)).isEqualTo(Outcome.LIMIT_REACHED);
        } finally {
            executor.shutdownNow();
        }
        assertThat(countActive(candidateId)).isEqualTo(100L);
        assertThat(countRows(candidateId, jobY)).isZero();
    }

    // ---- T16 (b) - 99 tin, hai luong luu hai tin moi cung luc, ROUNDS vong: dung mot 204 + mot 409, dung 100 dong ----
    @Test
    void t16_barrier_at99_twoNewSaves_exactlyOneSucceeds_eachOfTwentyRounds() throws Exception {
        HrAccount hr = registerHrWithCompany("u16k-hr");
        // Mot bo tin dung chung cho moi vong (unique theo cap ung vien-tin, moi vong mot ung vien moi).
        List<UUID> preSavedJobs = insertOpenJobs(hr, SavedJobService.MAX_ACTIVE_SAVED_JOBS - 1);
        UUID jobA = insertOpenJob(hr);
        UUID jobB = insertOpenJob(hr);
        List<Integer> completedRounds = new ArrayList<>();
        for (int round = 1; round <= ROUNDS; round++) {
            UUID candidateId = insertCandidate();
            insertActiveSaves(candidateId, preSavedJobs);

            List<Outcome> outcomes = runTogether(candidateId, jobA, jobB);

            assertThat(outcomes).as("vong %d", round).containsExactlyInAnyOrder(Outcome.SAVED, Outcome.LIMIT_REACHED);
            assertThat(countActive(candidateId)).as("vong %d", round).isEqualTo(100L);
            completedRounds.add(round);
        }
        assertThat(completedRounds).hasSize(ROUNDS);
    }
}

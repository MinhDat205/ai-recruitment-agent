package com.recruitment.scoring;

import static com.recruitment.scoring.BulkScoringSkipReason.RUN_IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

// FR-H04 mo rong - REQUIREMENT muc 7.1: T10, T11 (R-T3, R-T4). ScoringRunStateService la @MockitoSpyBean (goi that tru
// khi stub) -> lop nay co Spring context rieng (L7). Race dung bang stub chen luot TRUOC khi goi that, khong hai luong +
// sleep (muc 8).
class JobScoringRunBulkRaceIntegrationTest extends JobScoringRunBulkIntegrationTestSupport {

    @MockitoSpyBean
    ScoringRunStateService stateService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void resetSpy() {
        reset(stateService);
    }

    // ---- T10 ----

    @Test
    void t10_raceOnOneApplication_skipsItAndContinues() throws Exception {
        JobFixture f = readyJob();
        UUID a = eligible(f.jobId(), 1);
        UUID b = eligible(f.jobId(), 2);
        UUID c = eligible(f.jobId(), 3);
        // L4 - REQUIRES_NEW: luot chen tay commit doc lap, khong di chung transaction cua create du spy nam trong hay
        // ngoai proxy giao dich. Ghi lai luc stub chay co transaction dang mo hay khong de biet vi tri spy.
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicReference<Boolean> transactionActiveInStub = new AtomicReference<>();
        doAnswer(invocation -> {
            transactionActiveInStub.set(TransactionSynchronizationManager.isActualTransactionActive());
            requiresNew.executeWithoutResult(status -> jdbcTemplate.update(
                    "INSERT INTO scoring_runs (application_id, status) VALUES (?, 'PENDING')", a));
            return invocation.callRealMethod();
        }).when(stateService).create(eq(a), any(), any());

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isEqualTo(2);
        assertThat(body.get("remaining").asInt()).isZero();
        assertThat(skipped(body)).containsExactly(row(a, RUN_IN_PROGRESS));
        // A con dung 1 luot: luot chen tay (khong co rubric_snapshot) - luot cua create da rollback theo vi pham index.
        assertThat(runCount(a)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM scoring_runs WHERE application_id = ? AND rubric_snapshot IS NULL",
                        Long.class, a))
                .isEqualTo(1);
        assertThat(pendingRunCount(b)).isEqualTo(1);
        assertThat(pendingRunCount(c)).isEqualTo(1);
        System.out.printf(
                "FR-H04 bulk T10 (L4): transaction dang mo trong stub = %s -> spy nam %s proxy giao dich%n",
                transactionActiveInStub.get(), Boolean.TRUE.equals(transactionActiveInStub.get()) ? "TRONG" : "NGOAI");
    }

    // ---- T11 ----

    @Test
    void t11_otherIntegrityViolation_isNotSwallowed() throws Exception {
        JobFixture f = readyJob();
        UUID first = eligible(f.jobId(), 1);
        UUID second = eligible(f.jobId(), 2);
        UUID third = eligible(f.jobId(), 3);
        // Thong diep khong chua uq_scoring_run_in_progress -> R-T3 phai nem tiep, khong thanh RUN_IN_PROGRESS.
        doThrow(new DataIntegrityViolationException("vi pham rang buoc khac (gia lap)"))
                .when(stateService).create(eq(second), any(), any());

        // L5 - GlobalExceptionHandler "throw ex" va khong co handler Exception chung: duoi MockMvc perform nem
        // ServletException (server that tra 500).
        assertThatThrownBy(() -> bulk(f.hr().token(), f.jobId()))
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // R-T4 - luot cua don thu nhat da commit truoc loi, giu nguyen; don thu ba khong duoc thu.
        assertThat(pendingRunCount(first)).isEqualTo(1);
        assertThat(runCount(second)).isZero();
        assertThat(runCount(third)).isZero();
    }
}

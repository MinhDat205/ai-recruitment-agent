package com.recruitment.scoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.scoring.enabled=false (application-test.yml) - luc do bean nay KHONG ton
// tai trong context, tranh scheduler tu tick gay nhieu cac @SpringBootTest khac dung chung context
// cache. Test goi thang orchestrator.processOne(id)/scheduler.reapStaleClaims() tren mot instance
// TU DUNG bang new, khong cho @Scheduled tick that. Mau ResumeParsingScheduler/app.resume-parsing.enabled.
@Component
@ConditionalOnProperty(name = "app.scoring.enabled", havingValue = "true", matchIfMissing = true)
public class ScoringRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScoringRunScheduler.class);

    private final ScoringRunRepository scoringRunRepository;
    private final ScoringRunOrchestrator orchestrator;
    private final ScoringRunStateService stateService;
    private final int batchSize;
    private final long staleTimeoutMs;

    public ScoringRunScheduler(
            ScoringRunRepository scoringRunRepository,
            ScoringRunOrchestrator orchestrator,
            ScoringRunStateService stateService,
            @Value("${app.scoring.batch-size:10}") int batchSize,
            @Value("${app.hardening.stale-timeout-ms:900000}") long staleTimeoutMs) {
        this.scoringRunRepository = scoringRunRepository;
        this.orchestrator = orchestrator;
        this.stateService = stateService;
        this.batchSize = batchSize;
        this.staleTimeoutMs = staleTimeoutMs;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (markFailed ben trong), nhung van bat them o day de mot luot cham loi khong lam gian doan
    // viec quet cac luot con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.scoring.poll-interval-ms:5000}")
    public void pollPendingScoringRuns() {
        // Dot 4f (chore/hardening) - findReadyForProcessing (khong con findByStatus) da loc san
        // next_attempt_at va co khoa cuoi id, xem ScoringRunRepository.
        List<ScoringRun> pending = scoringRunRepository.findReadyForProcessing(batchSize);
        for (ScoringRun run : pending) {
            try {
                orchestrator.processOne(run.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet: scoringRunId={}", run.getId(), e);
            }
        }
    }

    // Dot 4h (chore/hardening) - stale-claim reaper: mot luot "ket" o RUNNING/finished_at NULL vi
    // JVM restart giua chung (uq_scoring_run_in_progress con chan ca viec tao luot cham moi cho don
    // do). Vong lap that su + goi stateService.markTemporaryFailure() QUA THAM CHIEU BEAN nam DUNG O
    // DAY, khong dat trong state service (tranh bay self-invocation, xem
    // ScoringRunStateService.findStaleClaimIds). Chu ky rieng, mau ResumeParsingScheduler.reapStaleClaims.
    @Scheduled(fixedDelayString = "${app.hardening.reaper-poll-interval-ms:60000}")
    public void reapStaleClaims() {
        Instant threshold = Instant.now().minusMillis(staleTimeoutMs);
        for (UUID id : stateService.findStaleClaimIds(threshold)) {
            try {
                stateService.markTemporaryFailure(id, ScoringRunErrorCode.STALE_CLAIM_TIMEOUT);
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc khi reap stale claim: scoringRunId={}", id, e);
            }
        }
    }
}

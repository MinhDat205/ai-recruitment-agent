package com.recruitment.scoring;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.scoring.enabled=false (application-test.yml) - luc do bean nay KHONG ton
// tai trong context, tranh scheduler tu tick gay nhieu cac @SpringBootTest khac dung chung context
// cache. Test goi thang orchestrator.processOne(id), khong cho @Scheduled tick. Mau
// ResumeParsingScheduler/app.resume-parsing.enabled.
@Component
@ConditionalOnProperty(name = "app.scoring.enabled", havingValue = "true", matchIfMissing = true)
public class ScoringRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScoringRunScheduler.class);

    private final ScoringRunRepository scoringRunRepository;
    private final ScoringRunOrchestrator orchestrator;
    private final int batchSize;

    public ScoringRunScheduler(
            ScoringRunRepository scoringRunRepository,
            ScoringRunOrchestrator orchestrator,
            @Value("${app.scoring.batch-size:10}") int batchSize) {
        this.scoringRunRepository = scoringRunRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (markFailed ben trong), nhung van bat them o day de mot luot cham loi khong lam gian doan
    // viec quet cac luot con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.scoring.poll-interval-ms:5000}")
    public void pollPendingScoringRuns() {
        List<ScoringRun> pending =
                scoringRunRepository.findByStatus(ScoringRunStatus.PENDING, PageRequest.of(0, batchSize));
        for (ScoringRun run : pending) {
            try {
                orchestrator.processOne(run.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet: scoringRunId={}", run.getId(), e);
            }
        }
    }
}

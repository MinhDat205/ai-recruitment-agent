package com.recruitment.scoring;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.aggregation.enabled=false (application-test.yml) - mau
// ScoringRunScheduler/ResumeParsingScheduler, cung ly do (tranh scheduler tu tick gay nhieu cac
// @SpringBootTest khac dung chung context cache). Test goi thang orchestrator.processOne(id),
// khong cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.aggregation.enabled", havingValue = "true", matchIfMissing = true)
public class AggregationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AggregationScheduler.class);

    private final ScoringRunRepository scoringRunRepository;
    private final AggregationOrchestrator orchestrator;
    private final int batchSize;

    public AggregationScheduler(
            ScoringRunRepository scoringRunRepository,
            AggregationOrchestrator orchestrator,
            @Value("${app.aggregation.batch-size:10}") int batchSize) {
        this.scoringRunRepository = scoringRunRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (markFailed ben trong), nhung van bat them o day de mot luot loi khong lam gian doan viec
    // quet cac luot con lai trong cung dot. Dieu kien nhat luot: status='RUNNING' AND finished_at
    // IS NOT NULL AND total_score IS NULL (Q1, ke hoach D3) - xem
    // ScoringRunRepository.findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull.
    @Scheduled(fixedDelayString = "${app.aggregation.poll-interval-ms:5000}")
    public void pollRunsReadyForAggregation() {
        List<ScoringRun> ready = scoringRunRepository.findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull(
                ScoringRunStatus.RUNNING, PageRequest.of(0, batchSize));
        for (ScoringRun run : ready) {
            try {
                orchestrator.processOne(run.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet tong hop: scoringRunId={}", run.getId(), e);
            }
        }
    }
}

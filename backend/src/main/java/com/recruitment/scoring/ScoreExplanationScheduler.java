package com.recruitment.scoring;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.explanation.enabled=false (application-test.yml) - mau
// AggregationScheduler/ScoringRunScheduler, cung ly do (tranh scheduler tu tick gay nhieu cac
// @SpringBootTest khac dung chung context cache). Test goi thang orchestrator.processOne(id), khong
// cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.explanation.enabled", havingValue = "true", matchIfMissing = true)
public class ScoreExplanationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScoreExplanationScheduler.class);

    private final ScoringRunRepository scoringRunRepository;
    private final ScoreExplanationOrchestrator orchestrator;
    private final int batchSize;
    private final int maxAttempts;

    public ScoreExplanationScheduler(
            ScoringRunRepository scoringRunRepository,
            ScoreExplanationOrchestrator orchestrator,
            @Value("${app.explanation.batch-size:10}") int batchSize,
            @Value("${app.explanation.max-attempts:3}") int maxAttempts) {
        this.scoringRunRepository = scoringRunRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (recordFailedAttempt ben trong), nhung van bat them o day de mot luot loi khong lam gian
    // doan viec quet cac luot con lai trong cung dot. Dieu kien nhat luot (ba dieu kien, xem
    // ScoringRunRepository.findRunsReadyForExplanation): status='DONE', chua co score_explanations,
    // chua vuot nguong app.explanation.max-attempts.
    @Scheduled(fixedDelayString = "${app.explanation.poll-interval-ms:5000}")
    public void pollRunsReadyForExplanation() {
        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(maxAttempts, batchSize);
        for (ScoringRun run : ready) {
            try {
                orchestrator.processOne(run.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet sinh bao cao giai thich: scoringRunId={}", run.getId(), e);
            }
        }
    }
}

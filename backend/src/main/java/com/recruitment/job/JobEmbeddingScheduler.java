package com.recruitment.job;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.job-embedding.enabled=false (application-test.yml) - mau
// ResumeParsingScheduler/CvImprovementScheduler, cung ly do (tranh scheduler tu tick gay nhieu cac
// @SpringBootTest khac dung chung context cache). Test goi thang orchestrator.processOne(id) hoac
// scheduler.pollJobsNeedingEmbedding(), khong cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.job-embedding.enabled", havingValue = "true", matchIfMissing = true)
public class JobEmbeddingScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobEmbeddingScheduler.class);

    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final JobEmbeddingOrchestrator orchestrator;
    private final int batchSize;

    public JobEmbeddingScheduler(
            JobEmbeddingRepository jobEmbeddingRepository,
            JobEmbeddingOrchestrator orchestrator,
            @Value("${app.job-embedding.batch-size:10}") int batchSize) {
        this.jobEmbeddingRepository = jobEmbeddingRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (khong ghi gi ca khi embedding that bai), nhung van bat them o day de mot job loi khong lam
    // gian doan viec quet cac job con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.job-embedding.poll-interval-ms:5000}")
    public void pollJobsNeedingEmbedding() {
        List<UUID> jobIds = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, batchSize));
        for (UUID jobId : jobIds) {
            try {
                orchestrator.processOne(jobId);
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet sinh embedding job: jobId={}", jobId, e);
            }
        }
    }
}

package com.recruitment.user;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.candidate-profile-embedding.enabled=false (application-test.yml) - mau
// ResumeEmbeddingScheduler/JobEmbeddingScheduler, cung ly do (tranh scheduler tu tick gay nhieu cac
// @SpringBootTest khac dung chung context cache). Test goi thang orchestrator.processOne(id), khong
// cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.candidate-profile-embedding.enabled", havingValue = "true", matchIfMissing = true)
public class CandidateProfileEmbeddingScheduler {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileEmbeddingScheduler.class);

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileEmbeddingOrchestrator orchestrator;
    private final int batchSize;

    public CandidateProfileEmbeddingScheduler(
            CandidateProfileRepository candidateProfileRepository,
            CandidateProfileEmbeddingOrchestrator orchestrator,
            @Value("${app.candidate-profile-embedding.batch-size:10}") int batchSize) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (khong ghi gi ca khi embedding that bai), nhung van bat them o day de mot ho so loi khong
    // lam gian doan viec quet cac ho so con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.candidate-profile-embedding.poll-interval-ms:5000}")
    public void pollProfilesNeedingEmbedding() {
        List<UUID> ids = candidateProfileRepository.findIdsNeedingEmbedding(PageRequest.of(0, batchSize));
        for (UUID id : ids) {
            try {
                orchestrator.processOne(id);
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet sinh embedding ho so: id={}", id, e);
            }
        }
    }
}

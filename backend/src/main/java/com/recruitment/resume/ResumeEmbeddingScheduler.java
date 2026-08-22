package com.recruitment.resume;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.resume-embedding.enabled=false (application-test.yml, da them tu Dot 3) -
// mau JobEmbeddingScheduler/ResumeParsingScheduler, cung ly do (tranh scheduler tu tick gay nhieu
// cac @SpringBootTest khac dung chung context cache). Test goi thang orchestrator.processOne(id)
// hoac pollOnce() tu viet trong test, khong cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.resume-embedding.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeEmbeddingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumeEmbeddingScheduler.class);

    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeEmbeddingOrchestrator orchestrator;
    private final int batchSize;

    public ResumeEmbeddingScheduler(
            ResumeParsedDataRepository resumeParsedDataRepository,
            ResumeEmbeddingOrchestrator orchestrator,
            @Value("${app.resume-embedding.batch-size:10}") int batchSize) {
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (khong ghi gi ca khi embedding that bai), nhung van bat them o day de mot CV loi khong lam
    // gian doan viec quet cac CV con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.resume-embedding.poll-interval-ms:5000}")
    public void pollResumesNeedingEmbedding() {
        List<UUID> ids = resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, batchSize));
        for (UUID id : ids) {
            try {
                orchestrator.processOne(id);
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet sinh embedding CV: parsedDataId={}", id, e);
            }
        }
    }
}

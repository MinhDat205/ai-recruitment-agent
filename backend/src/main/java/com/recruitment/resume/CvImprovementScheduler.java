package com.recruitment.resume;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.cv-improvement.enabled=false (application-test.yml) - mau
// ResumeParsingScheduler/ScoreExplanationScheduler, cung ly do (tranh scheduler tu tick gay nhieu cac
// @SpringBootTest khac dung chung context cache). Test goi thang orchestrator.processOne(id), khong
// cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.cv-improvement.enabled", havingValue = "true", matchIfMissing = true)
public class CvImprovementScheduler {

    private static final Logger log = LoggerFactory.getLogger(CvImprovementScheduler.class);

    private final CvImprovementRequestRepository cvImprovementRequestRepository;
    private final CvImprovementOrchestrator orchestrator;
    private final int batchSize;

    public CvImprovementScheduler(
            CvImprovementRequestRepository cvImprovementRequestRepository,
            CvImprovementOrchestrator orchestrator,
            @Value("${app.cv-improvement.batch-size:10}") int batchSize) {
        this.cvImprovementRequestRepository = cvImprovementRequestRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua chinh
    // no (markFailed ben trong), nhung van bat them o day de mot request loi khong lam gian doan viec
    // quet cac request con lai trong cung dot. findByStatusOrderByRequestedAtAscIdAsc (Dot 2) - FIFO,
    // tranh starvation khi hang doi dai hon batchSize.
    @Scheduled(fixedDelayString = "${app.cv-improvement.poll-interval-ms:5000}")
    public void pollPendingRequests() {
        List<CvImprovementRequest> pending = cvImprovementRequestRepository.findByStatusOrderByRequestedAtAscIdAsc(
                CvImprovementRequestStatus.PENDING, PageRequest.of(0, batchSize));
        for (CvImprovementRequest request : pending) {
            try {
                orchestrator.processOne(request.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet sinh goi y cai thien CV: requestId={}", request.getId(), e);
            }
        }
    }
}

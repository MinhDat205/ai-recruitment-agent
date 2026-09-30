package com.recruitment.resume;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Poller trich xuat lai (FR-C05 R-R5) - khuon ResumeParsingScheduler. Tat trong test qua
// app.resume-reparse.enabled=false; test goi thang orchestrator.processOne(id) hoac tu new scheduler.
@Component
@ConditionalOnProperty(name = "app.resume-reparse.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeReparseScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumeReparseScheduler.class);

    private final ResumeReparseRequestRepository requestRepository;
    private final ResumeReparseOrchestrator orchestrator;
    private final ResumeReparseStateService stateService;
    private final int batchSize;
    private final long staleTimeoutMs;

    public ResumeReparseScheduler(
            ResumeReparseRequestRepository requestRepository,
            ResumeReparseOrchestrator orchestrator,
            ResumeReparseStateService stateService,
            @Value("${app.resume-reparse.batch-size:10}") int batchSize,
            @Value("${app.hardening.stale-timeout-ms:900000}") long staleTimeoutMs) {
        this.requestRepository = requestRepository;
        this.orchestrator = orchestrator;
        this.stateService = stateService;
        this.batchSize = batchSize;
        this.staleTimeoutMs = staleTimeoutMs;
    }

    // KHONG @Transactional (CLAUDE.md muc 3c). Bat loi tung phan tu de mot yeu cau loi khong chan ca lo.
    @Scheduled(fixedDelayString = "${app.resume-reparse.poll-interval-ms:5000}")
    public void pollPendingRequests() {
        for (ResumeReparseRequest request : requestRepository.findReadyForProcessing(batchSize)) {
            try {
                orchestrator.processOne(request.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet trich xuat lai: requestId={}", request.getId(), e);
            }
        }
    }

    // Stale-claim reaper (mau ResumeParsingScheduler.reapStaleClaims): JVM restart giua chung de yeu cau
    // ket o RUNNING - khong gat lai thi partial unique index chan ung vien gui yeu cau moi vinh vien.
    // Dung chung ngan sach attempt_count/LLM_RETRY_EXHAUSTED voi loi tam thoi.
    @Scheduled(fixedDelayString = "${app.hardening.reaper-poll-interval-ms:60000}")
    public void reapStaleClaims() {
        Instant threshold = Instant.now().minusMillis(staleTimeoutMs);
        for (UUID id : stateService.findStaleClaimIds(threshold)) {
            try {
                stateService.markTemporaryFailure(id, ResumeParsingErrorCode.STALE_CLAIM_TIMEOUT);
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc khi reap stale claim trich xuat lai: requestId={}", id, e);
            }
        }
    }
}

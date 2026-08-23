package com.recruitment.resume;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.resume-parsing.enabled=false (application-test.yml) - luc do bean nay
// KHONG ton tai trong context, tranh scheduler tu tick gay nhieu cac @SpringBootTest khac dung
// chung context cache. Test goi thang orchestrator.processOne(id)/scheduler.reapStaleClaims() tren
// mot instance TU DUNG bang new (khong qua Spring), khong cho @Scheduled tick that.
@Component
@ConditionalOnProperty(name = "app.resume-parsing.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParsingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingScheduler.class);

    private final ResumeRepository resumeRepository;
    private final ResumeParsingOrchestrator orchestrator;
    private final ResumeParsingStateService stateService;
    private final int batchSize;
    private final long staleTimeoutMs;

    public ResumeParsingScheduler(
            ResumeRepository resumeRepository,
            ResumeParsingOrchestrator orchestrator,
            ResumeParsingStateService stateService,
            @Value("${app.resume-parsing.batch-size:10}") int batchSize,
            @Value("${app.hardening.stale-timeout-ms:900000}") long staleTimeoutMs) {
        this.resumeRepository = resumeRepository;
        this.orchestrator = orchestrator;
        this.stateService = stateService;
        this.batchSize = batchSize;
        this.staleTimeoutMs = staleTimeoutMs;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua
    // chinh no (markFailed ben trong), nhung van bat them o day de mot resume loi khong lam gian
    // doan viec quet cac resume con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.resume-parsing.poll-interval-ms:5000}")
    public void pollPendingResumes() {
        // Dot 4f (chore/hardening) - findReadyForProcessing (khong con findByParseStatus) da loc san
        // next_attempt_at va co khoa cuoi id, xem ResumeRepository.
        List<Resume> pending = resumeRepository.findReadyForProcessing(batchSize);
        for (Resume resume : pending) {
            try {
                orchestrator.processOne(resume.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet: resumeId={}", resume.getId(), e);
            }
        }
    }

    // Dot 4h (chore/hardening) - stale-claim reaper: mot resume "ket" o PROCESSING vi JVM restart
    // giua chung (scheduler chinh chi quet PENDING, khong bao gio nhat lai duoc PROCESSING). Vong
    // lap that su + goi stateService.markTemporaryFailure() QUA THAM CHIEU BEAN (khac class voi
    // ResumeParsingStateService) nam DUNG O DAY - KHONG dat trong state service (xem
    // ResumeParsingStateService.findStaleClaimIds ve bay self-invocation). Chu ky rieng
    // (app.hardening.reaper-poll-interval-ms), tach khoi poll-interval-ms xu ly chinh - mot ban ghi
    // loi trong vong quet nay khong lam gian doan cac ban ghi khac (try-catch tung phan tu, dung
    // mau pollPendingResumes o tren).
    @Scheduled(fixedDelayString = "${app.hardening.reaper-poll-interval-ms:60000}")
    public void reapStaleClaims() {
        Instant threshold = Instant.now().minusMillis(staleTimeoutMs);
        for (UUID id : stateService.findStaleClaimIds(threshold)) {
            try {
                stateService.markTemporaryFailure(id, ResumeParsingErrorCode.STALE_CLAIM_TIMEOUT);
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc khi reap stale claim: resumeId={}", id, e);
            }
        }
    }
}

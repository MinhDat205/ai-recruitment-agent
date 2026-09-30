package com.recruitment.resume;

import com.recruitment.common.LlmRetryPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho trich xuat lai (FR-C05 R-R4, R-R5) - xem CLAUDE.md muc 3c: moi method ngan, KHONG
// bao quanh loi goi LLM, va khong bao gio duoc goi qua self-invocation tu ResumeReparseOrchestrator.
@Service
public class ResumeReparseStateService {

    private static final Logger log = LoggerFactory.getLogger(ResumeReparseStateService.class);

    private final ResumeReparseRequestRepository requestRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeParsedDataEnricher enricher;
    private final LlmRetryPolicy retryPolicy;

    public ResumeReparseStateService(
            ResumeReparseRequestRepository requestRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            ResumeParsedDataEnricher enricher,
            LlmRetryPolicy retryPolicy) {
        this.requestRepository = requestRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.enricher = enricher;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public boolean claim(UUID requestId) {
        return requestRepository.claimForProcessing(requestId) == 1;
    }

    // R-R5 - MOT transaction: UPDATE tai cho resume_parsed_data (khong INSERT - resume_id UNIQUE) +
    // yeu cau -> DONE. Ghi data/model/prompt_version/token_usage/industry_code/region_code/cot kinh
    // nghiem qua entity (enricher: R-C3 + R-E9a, CUNG ham voi trich xuat lan dau), roi parsed_at va
    // embedding = NULL qua touchAfterReparse. raw_text KHONG bi dung toi: entity doc len giu nguyen gia
    // tri, khong setter nao doi no. resumes.parse_status khong bi doc/ghi o day (giu DONE).
    @Transactional
    public void markDone(UUID requestId, UUID parsedDataId, ResumeParsingResult result) {
        ResumeParsedData data = resumeParsedDataRepository.findById(parsedDataId).orElseThrow();
        enricher.applyExtraction(data, result.payload());
        data.setModel(result.model());
        data.setPromptVersion(result.promptVersion());
        data.setTokenUsage(result.tokenUsage());
        resumeParsedDataRepository.saveAndFlush(data);
        resumeParsedDataRepository.touchAfterReparse(parsedDataId);

        ResumeReparseRequest request = requestRepository.findById(requestId).orElseThrow();
        request.setStatus(ResumeReparseRequestStatus.DONE);
        request.setErrorMessage(null);
        request.setClaimedAt(null);
        request.setNextAttemptAt(null);
        request.setFinishedAt(Instant.now());
        requestRepository.save(request);
    }

    // R-R5 that bai: CHI cham bang yeu cau - resume_parsed_data giu nguyen (du lieu v1 van dung duoc).
    // Nhan ResumeParsingErrorCode (FormattedErrorCode), khong nhan String - khong the lo truyen
    // e.getMessage() vao cot.
    @Transactional
    public void markFailed(UUID requestId, ResumeParsingErrorCode errorCode) {
        ResumeReparseRequest request = requestRepository.findById(requestId).orElseThrow();
        request.setStatus(ResumeReparseRequestStatus.FAILED);
        request.setErrorMessage(errorCode.formatted());
        request.setClaimedAt(null);
        request.setFinishedAt(Instant.now());
        requestRepository.save(request);
    }

    // Loi LLM tam thoi / stale claim - co che thu lai co backoff san co (LlmRetryPolicy, chore/hardening),
    // khuon ResumeParsingStateService.markTemporaryFailure: doc attempt_count chi de TINH, ghi qua MOT
    // UPDATE co dieu kien "attempt_count = expectedCount". Het luot -> FAILED voi LLM_RETRY_EXHAUSTED.
    @Transactional
    public void markTemporaryFailure(UUID requestId, ResumeParsingErrorCode errorCode) {
        ResumeReparseRequest current = requestRepository.findById(requestId).orElseThrow();
        int expectedCount = current.getAttemptCount();
        int nextAttempt = expectedCount + 1;
        boolean exhausted = nextAttempt >= retryPolicy.maxAttempts();

        ResumeReparseRequestStatus nextStatus =
                exhausted ? ResumeReparseRequestStatus.FAILED : ResumeReparseRequestStatus.PENDING;
        String errorMessage = exhausted
                ? ResumeParsingErrorCode.LLM_RETRY_EXHAUSTED.formatted()
                : errorCode.formatted();
        Instant now = Instant.now();
        Instant nextAttemptAt = exhausted ? null : now.plusMillis(retryPolicy.backoffMillisAfterAttempt(nextAttempt));
        Instant finishedAt = exhausted ? now : null;

        int updated = requestRepository.markTemporaryFailure(
                requestId, expectedCount, nextStatus.name(), nextAttempt, errorMessage, nextAttemptAt, finishedAt);
        if (updated == 0) {
            log.warn(
                    "Bo qua markTemporaryFailure trich xuat lai: requestId={} da bi luong khac doi giua luc doc va "
                            + "luc ghi (expectedCount={})",
                    requestId,
                    expectedCount);
        }
    }

    // Chi doc - vong lap va loi goi markTemporaryFailure nam o ResumeReparseScheduler (bean khac, tranh
    // self-invocation), mau ResumeParsingStateService.findStaleClaimIds.
    @Transactional(readOnly = true)
    public List<UUID> findStaleClaimIds(Instant threshold) {
        return requestRepository.findIdsByStatusAndClaimedAtBefore(ResumeReparseRequestStatus.RUNNING, threshold);
    }
}

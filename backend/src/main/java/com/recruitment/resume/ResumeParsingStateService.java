package com.recruitment.resume;

import com.recruitment.common.LlmRetryPolicy;
import com.recruitment.common.exception.ResumeNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng, tach khoi ResumeParsingOrchestrator - xem CLAUDE.md muc 3c. Self-invocation
// (goi this.method() tu trong cung mot class) khong di qua proxy Spring, nen @Transactional se
// khong mo transaction nao ca. Ca cac method o day deu ngan, khong bao gio bao quanh loi goi LLM.
@Service
public class ResumeParsingStateService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingStateService.class);

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final LlmRetryPolicy retryPolicy;

    public ResumeParsingStateService(
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            LlmRetryPolicy retryPolicy) {
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public boolean claim(UUID resumeId) {
        return resumeRepository.claimForProcessing(resumeId) == 1;
    }

    // Hai cau ghi (resumes + resume_parsed_data) phai cung thanh cong hoac cung rollback - neu chi
    // ghi duoc resumes.parse_status=DONE ma insert resume_parsed_data loi (vd vi pham UNIQUE do
    // race), trang thai se noi doi la DONE nhung khong co data.
    @Transactional
    public void markDone(
            UUID resumeId,
            String rawText,
            ResumeParsedPayload payload,
            String model,
            String promptVersion,
            Integer tokenUsage) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.save(resume);

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText(rawText);
        data.setData(payload);
        data.setModel(model);
        data.setPromptVersion(promptVersion);
        data.setTokenUsage(tokenUsage);
        resumeParsedDataRepository.save(data);
    }

    @Transactional
    public void markFailed(UUID resumeId, ResumeParsingErrorCode errorCode) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        resume.setParseStatus(ParseStatus.FAILED);
        resume.setParseError(errorCode.formatted());
        resumeRepository.save(resume);
    }

    // Dot 4e (chore/hardening) - loi LLM TAM THOI (LLM_TEMPORARILY_UNAVAILABLE) hoac stale-claim
    // (STALE_CLAIM_TIMEOUT, Dot 4h nhip sau) di qua day thay vi markFailed. Doc attempt_count HIEN
    // TAI chi de TINH TOAN (expectedCount, nextAttempt) - KHONG dung lam can cu de GHI: ghi that su
    // qua MOT @Modifying UPDATE mang chinh dieu kien "attempt_count = expectedCount" lam optimistic
    // lock, tranh lap lai lost-update ma Dot 2a da sua (xem ResumeRepository.markTemporaryFailure).
    // Rowcount 0 nghia la mot luong khac (worker goc vua markDone/markFailed, hoac reaper) da doi
    // ban ghi nay truoc - log warn roi RETURN EM, KHONG nem exception (khong duoc lam gian doan
    // vong quet cua scheduler vi mot ban ghi da duoc xu ly boi noi khac).
    @Transactional
    public void markTemporaryFailure(UUID resumeId, ResumeParsingErrorCode errorCode) {
        Resume current = resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        int expectedCount = current.getAttemptCount();
        int nextAttempt = expectedCount + 1;
        boolean exhausted = nextAttempt >= retryPolicy.maxAttempts();

        ParseStatus nextStatus = exhausted ? ParseStatus.FAILED : ParseStatus.PENDING;
        String errorMessage = exhausted
                ? ResumeParsingErrorCode.LLM_RETRY_EXHAUSTED.formatted()
                : errorCode.formatted();
        Instant nextAttemptAt =
                exhausted ? null : Instant.now().plusMillis(retryPolicy.backoffMillisAfterAttempt(nextAttempt));

        int updated = resumeRepository.markTemporaryFailure(
                resumeId, expectedCount, nextStatus.name(), nextAttempt, errorMessage, nextAttemptAt);
        if (updated == 0) {
            log.warn(
                    "Bo qua markTemporaryFailure: resumeId={} da bi thay doi boi luong xu ly khac giua "
                            + "luc doc va luc ghi (expectedCount={})",
                    resumeId,
                    expectedCount);
        }
    }

    // Dot 4h (chore/hardening) - CHI doc, KHONG vong lap va KHONG goi markTemporaryFailure o day (xem
    // CLAUDE.md muc 3c ve bay self-invocation da tranh o Dot 4e: goi mot method @Transactional khac
    // qua this. tu trong CUNG class se khong di qua proxy Spring, transaction se khong mo). Vong lap
    // that su + goi markTemporaryFailure() QUA THAM CHIEU BEAN nam o ResumeParsingScheduler, bean
    // KHAC voi bean nay. readOnly=true, tra List<UUID> (KHONG tra List<Resume>) - tranh de entity
    // thoat ra ngoai pham vi transaction doc nay roi bi truy cap detached ngoai y muon.
    @Transactional(readOnly = true)
    public List<UUID> findStaleClaimIds(Instant threshold) {
        return resumeRepository.findIdsByParseStatusAndClaimedAtBefore(ParseStatus.PROCESSING, threshold);
    }
}

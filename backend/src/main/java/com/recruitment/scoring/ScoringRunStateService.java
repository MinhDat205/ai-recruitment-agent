package com.recruitment.scoring;

import com.recruitment.ai.criterion.CriterionScorePayload;
import com.recruitment.ai.criterion.CriterionScoringResult;
import com.recruitment.common.FormattedErrorCode;
import com.recruitment.common.LlmRetryPolicy;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterionRepository;
import com.recruitment.rubric.RubricRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho scoring - xem CLAUDE.md muc 3c va ResumeParsingStateService. KHONG method nao o
// day duoc goi qua self-invocation tu ScoringRunService/ScoringRunOrchestrator - phai qua bean nay de
// @Transactional di qua proxy Spring. unlockRubricIfSafe la private helper (khong @Transactional
// rieng) nen goi tu markFailed/markTemporaryFailure trong CUNG class KHONG phai self-invocation qua
// proxy - no chay trong transaction cua method goi no.
@Service
public class ScoringRunStateService {

    private static final Logger log = LoggerFactory.getLogger(ScoringRunStateService.class);

    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final LlmRetryPolicy retryPolicy;

    public ScoringRunStateService(
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository,
            RubricRepository rubricRepository,
            RubricCriterionRepository rubricCriterionRepository,
            JobApplicationRepository jobApplicationRepository,
            LlmRetryPolicy retryPolicy) {
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
        this.rubricRepository = rubricRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.retryPolicy = retryPolicy;
    }

    // Q6 (ke hoach D2): khoa rubric CUNG transaction voi viec tao scoring_runs, ngay tai thoi diem
    // tao (khong doi den luc claim/dong tieu chi dau tien) - dong voi thoi diem chup rubric_snapshot.
    // Vi ca hai cau ghi (INSERT scoring_runs, UPDATE rubrics.is_locked) cung mot @Transactional,
    // Spring commit/rollback ca hai cung luc - khong bao gio co trang thai nua voi (tao duoc
    // scoring_runs ma khoa rubric that bai, hay nguoc lai).
    //
    // Doc lai Rubric TUOI trong chinh transaction nay (khong dung lai object da load o
    // ScoringRunService) - cung tinh than voi ResumeParsingStateService.markDone doc lai Resume
    // moi thay vi tin object caller dang cam san.
    //
    // Khoa idempotent: chi UPDATE khi dang false. Lot cham thu hai tro di cho cung job la no-op
    // tren co khoa, khong loi (tinh huong 2 cua Q6).
    @Transactional
    public ScoringRun create(UUID applicationId, RubricSnapshot rubricSnapshot, UUID rubricId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        run.setRubricSnapshot(rubricSnapshot);
        // saveAndFlush: bat INSERT no ngay tai day thay vi hoan toi luc commit, de
        // DataIntegrityViolationException cua uq_scoring_run_in_progress (V4) noi len trong pham
        // vi request va GlobalExceptionHandler bat duoc, tra 409 xac dinh - cung ly do
        // ApplicationService.apply da dung saveAndFlush cho uq_application_per_cycle.
        run = scoringRunRepository.saveAndFlush(run);

        // Chi la luoi an toan: rubric da duoc ScoringRunService xac nhan ton tai va du dieu kien
        // ngay truoc do trong cung request. Khong dung RubricNotFoundException (danh cho 404
        // nguoi dung that) - day khong phai duong loi nguoi dung co the tao ra.
        Rubric rubric = rubricRepository.findById(rubricId).orElseThrow();
        if (!rubric.isLocked()) {
            rubric.setLocked(true);
            rubricRepository.save(rubric);
        }

        return run;
    }

    @Transactional
    public boolean claim(UUID scoringRunId) {
        return scoringRunRepository.claimForProcessing(scoringRunId) == 1;
    }

    // Ghi MOT dong criterion_scores va cong don token_usage vao scoring_runs trong CUNG transaction
    // NGAN - goi NGOAI transaction goi LLM (CriterionScoringService.score() da chay xong TRUOC do o
    // ScoringRunOrchestrator, xem CLAUDE.md muc 3c). model/promptVersion ghi DE o MOI lan goi (khong
    // gating "chi ghi lan dau") - ca luot luon dung cung model/prompt nen ghi de lap lai vo hai, don
    // gian hon logic set-once.
    @Transactional
    public void recordCriterionScore(
            UUID scoringRunId, RubricSnapshot.CriterionSnapshot criterionSnapshot, CriterionScoringResult result) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(scoringRunId);
        // FK toi rubric_criteria (ON DELETE SET NULL) KHONG the INSERT tham chieu toi mot id da
        // khong con ton tai (khac voi viec DB tu null hoa khi xoa SAU KHI da tham chieu) - phai
        // existsById() truoc khi set, null neu tieu chi goc da bi HR xoa giua luc snapshot va luc
        // cham (xem CriterionScore.java). Cac field snapshot ben duoi luon ghi day du tu
        // criterionSnapshot, khong phu thuoc FK con song hay khong.
        UUID criterionId = criterionSnapshot.criterionId();
        criterionScore.setCriterionId(rubricCriterionRepository.existsById(criterionId) ? criterionId : null);
        criterionScore.setCriterionNameSnapshot(criterionSnapshot.name());
        criterionScore.setWeightSnapshot(criterionSnapshot.weight());
        criterionScore.setMaxScoreSnapshot(criterionSnapshot.maxScore());
        criterionScore.setScore(toScoreScale(result.payload().score()));
        criterionScore.setReasoning(result.payload().reasoning());
        criterionScore.setEvidence(toEvidenceEntries(result.payload().evidence()));
        // Dot 4g (chore/hardening) - saveAndFlush thay vi save: bat INSERT chay NGAY trong pham vi
        // method nay de DataIntegrityViolationException cua uq_score_per_criterion (V1, khi hai
        // worker cung cham mot tieu chi do race stale-claim reaper) noi len cho ScoringRunOrchestrator
        // bat duoc TAI DAY, khong bi hoan toi luc commit cuoi transaction (luc do da ra khoi try-catch
        // du dinh). Cung khuon voi ScoringRunStateService.create da dung cho uq_scoring_run_in_progress.
        criterionScoreRepository.saveAndFlush(criterionScore);

        ScoringRun run = scoringRunRepository.findById(scoringRunId).orElseThrow();
        run.setModel(result.model());
        run.setPromptVersion(result.promptVersion());
        int previousTokenUsage = run.getTokenUsage() == null ? 0 : run.getTokenUsage();
        int callTokenUsage = result.tokenUsage() == null ? 0 : result.tokenUsage();
        run.setTokenUsage(previousTokenUsage + callTokenUsage);
        scoringRunRepository.save(run);
    }

    // score la Double o CriterionScorePayload (LLM), BigDecimal NUMERIC(5,2) o entity.
    // BigDecimal.valueOf(double) (KHONG "new BigDecimal(double)") doc qua Double.toString() truoc,
    // tranh sai so bieu dien nhi phan cua double (vd "new BigDecimal(4.1)" ra
    // 4.0999999999999996...); setScale(2, HALF_UP) sau do khop dung NUMERIC(5,2) cua cot. validate()
    // o CriterionScoringService da dam bao score nam trong [0, maxScore] TRUOC khi ket qua toi day,
    // khong can kiem lai o tang nay.
    private static BigDecimal toScoreScale(double score) {
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    // Anh xa EvidenceQuote (ai.criterion, doc lap DB) -> EvidenceEntry (scoring, JSONB cua
    // criterion_scores.evidence) - Dot 3 co y giu hai record rieng, xem ghi chu trong
    // CriterionScorePayload.java.
    private static List<EvidenceEntry> toEvidenceEntries(List<CriterionScorePayload.EvidenceQuote> quotes) {
        return quotes.stream().map(q -> new EvidenceEntry(q.quote(), q.section())).toList();
    }

    // Dung khi mot tieu chi loi sau retry (CriterionScoringFailedException, xem
    // ScoringRunOrchestrator.doProcess), hoac luoi an toan cuoi cung bat mot RuntimeException khong
    // luong truoc (xem ScoringRunOrchestrator.processOne). Thu tu BAT BUOC theo Q6 (ke hoach D2):
    // set FAILED+finished_at TRUOC, roi moi chay isSafeToUnlock, roi moi mo khoa neu thoa - tat ca
    // trong CUNG transaction, de chinh luot dang fail tu loai khoi ve thu hai cua dieu kien "dang
    // chay" (Postgres thay duoc ghi cua chinh transaction minh, khong can loai tru tuong minh id).
    // Da doi tu findById+save khong dieu kien sang UPDATE co dieu kien status='RUNNING' (Dot 4e,
    // Viec 3 - xem ScoringRunRepository.markFailedIfRunning ve ly do va gioi han da biet). Hanh vi
    // "CHI set finished_at khi dang NULL" giu nguyen qua COALESCE trong chinh cau UPDATE do - van
    // dam bao D3 (AggregationOrchestrator, goi ham nay khi finished_at DA CO tu D2) khong bi ghi de
    // moc "D2 cham xong toan bo tieu chi" (xem CLAUDE.md muc 2b).
    @Transactional
    public void markFailed(UUID scoringRunId, FormattedErrorCode errorCode) {
        ScoringRun run = scoringRunRepository.findById(scoringRunId).orElseThrow();
        int updated = scoringRunRepository.markFailedIfRunning(scoringRunId, errorCode.formatted(), Instant.now());
        if (updated == 0) {
            log.warn(
                    "Bo qua markFailed: scoringRunId={} khong con RUNNING (da bi xu ly boi luong khac)",
                    scoringRunId);
            return;
        }
        unlockRubricIfSafe(run.getApplicationId());
    }

    // Dot 4e (chore/hardening) - loi LLM TAM THOI (CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE)
    // hoac stale-claim (ScoringRunErrorCode.STALE_CLAIM_TIMEOUT, Dot 4h nhip sau) di qua day thay vi
    // markFailed. TUYET DOI KHONG goi markFailed o nhanh exhausted: markFailed hien la UPDATE dieu
    // kien status='RUNNING' KHONG mang attempt_count, se mat lop chong race cua chinh method nay va
    // ghi sai attempt_count cuoi cung - nhanh exhausted tu chay markRetryExhausted (dieu kien VA
    // GHI attempt_count trong CUNG mot UPDATE), roi tu goi unlockRubricIfSafe rieng.
    @Transactional
    public void markTemporaryFailure(UUID scoringRunId, FormattedErrorCode errorCode) {
        ScoringRun current = scoringRunRepository.findById(scoringRunId).orElseThrow();
        int expectedCount = current.getAttemptCount();
        int nextAttempt = expectedCount + 1;
        boolean exhausted = nextAttempt >= retryPolicy.maxAttempts();

        if (exhausted) {
            int updated = scoringRunRepository.markRetryExhausted(
                    scoringRunId,
                    expectedCount,
                    nextAttempt,
                    ScoringRunErrorCode.LLM_RETRY_EXHAUSTED.formatted(),
                    Instant.now());
            if (updated == 0) {
                log.warn(
                        "Bo qua markTemporaryFailure (exhausted): scoringRunId={} da bi thay doi boi "
                                + "luong khac (expectedCount={})",
                        scoringRunId,
                        expectedCount);
                return;
            }
            unlockRubricIfSafe(current.getApplicationId());
        } else {
            Instant nextAttemptAt = Instant.now().plusMillis(retryPolicy.backoffMillisAfterAttempt(nextAttempt));
            int updated = scoringRunRepository.markTemporaryFailure(
                    scoringRunId, expectedCount, nextAttempt, errorCode.formatted(), nextAttemptAt);
            if (updated == 0) {
                log.warn(
                        "Bo qua markTemporaryFailure: scoringRunId={} da bi thay doi boi luong khac "
                                + "(expectedCount={})",
                        scoringRunId,
                        expectedCount);
            }
        }
    }

    // Dot 4h (chore/hardening) - CHI doc, KHONG vong lap va KHONG goi markTemporaryFailure o day
    // (cung ly do voi ResumeParsingStateService.findStaleClaimIds - tranh bay self-invocation).
    // Vong lap that su nam o ScoringRunScheduler, bean KHAC. Dieu kien finished_at IS NULL o
    // ScoringRunRepository.findIdsByStatusAndFinishedAtIsNullAndStartedAtBefore loai tru luot DA
    // cham xong dang cho D3 tong hop (xem CLAUDE.md muc 2b) - reaper KHONG duoc dong toi luot do.
    @Transactional(readOnly = true)
    public List<UUID> findStaleClaimIds(Instant threshold) {
        return scoringRunRepository.findIdsByStatusAndFinishedAtIsNullAndStartedAtBefore(
                ScoringRunStatus.RUNNING, threshold);
    }

    // Trich tu markFailed cu (Dot 4e) - method private KHONG @Transactional rieng nen goi tu
    // markFailed/markTemporaryFailure trong CUNG class chay trong transaction cua caller, KHONG
    // phai self-invocation qua proxy (bay chi xay ra khi method duoc goi mang @Transactional rieng
    // cua chinh no - xem CLAUDE.md muc 3c).
    private void unlockRubricIfSafe(UUID applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        if (scoringRunRepository.isSafeToUnlock(application.getJobId())) {
            Rubric rubric = rubricRepository.findByJobId(application.getJobId()).orElseThrow();
            rubric.setLocked(false);
            rubricRepository.save(rubric);
        }
    }

    // Moc "D2 da cham xong toan bo tieu chi" (Q1, ke hoach D2) - status VAN la RUNNING, KHONG doi
    // sang DONE (viec cua D3 khi da tinh xong total_score). finished_at la tin hieu DUY NHAT de
    // frontend dung polling, ca cho case thanh cong (RUNNING+finished_at) lan case FAILED
    // (markFailed o tren).
    @Transactional
    public void markFinished(UUID scoringRunId) {
        ScoringRun run = scoringRunRepository.findById(scoringRunId).orElseThrow();
        run.setFinishedAt(Instant.now());
        scoringRunRepository.save(run);
    }

    // Ghi ket qua tong hop cua D3 (FR-H05, Dot 2 cua ke hoach D3): mot UPDATE co dieu kien VUA la
    // buoc ghi VUA la chot chan duy nhat, KHONG co claim rieng truoc do - xem ly do day du tai
    // ScoringRunRepository.finishAggregation (Q3 cua dot nay trong ke hoach D3). Tra ve false neu
    // khong con thoa dieu kien WHERE nua (da duoc mot lan goi khac ghi truoc do) - day la NO-OP AN
    // TOAN, khong phai loi: ca hai lan goi deu tinh total_score tu CUNG du lieu criterion_scores/
    // rubric_snapshot da commit (ham thuan, khong tac dung phu) nen luon ra cung gia tri.
    @Transactional
    public boolean finishAggregation(UUID scoringRunId, BigDecimal totalScore) {
        return scoringRunRepository.finishAggregation(scoringRunId, totalScore) == 1;
    }
}

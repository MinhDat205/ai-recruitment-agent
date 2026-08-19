package com.recruitment.scoring;

import com.recruitment.ai.criterion.CriterionScorePayload;
import com.recruitment.ai.criterion.CriterionScoringResult;
import com.recruitment.common.FormattedErrorCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho scoring - xem CLAUDE.md muc 3c va ResumeParsingStateService. KHONG method nao o
// day duoc goi qua self-invocation tu ScoringRunService/ScoringRunOrchestrator - phai qua bean nay de
// @Transactional di qua proxy Spring.
@Service
public class ScoringRunStateService {

    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public ScoringRunStateService(
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository,
            RubricRepository rubricRepository,
            RubricCriterionRepository rubricCriterionRepository,
            JobApplicationRepository jobApplicationRepository) {
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
        this.rubricRepository = rubricRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.jobApplicationRepository = jobApplicationRepository;
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
        criterionScoreRepository.save(criterionScore);

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
    @Transactional
    public void markFailed(UUID scoringRunId, FormattedErrorCode errorCode) {
        ScoringRun run = scoringRunRepository.findById(scoringRunId).orElseThrow();
        run.setStatus(ScoringRunStatus.FAILED);
        // CHI set finished_at khi dang NULL - KHONG ghi de mot moc da co san (sua o Dot 2, ke
        // hoach D3, sau khi phat hien qua cau hoi Q2+Q3 gop). Call site D2
        // (ScoringRunOrchestrator.doProcess, tieu chi loi giua chung) luon goi ham nay khi
        // finished_at CON NULL (luot chua tung cham xong) nen hanh vi KHONG DOI: van la lan dau
        // tien finished_at duoc set, giong het truoc khi sua (xem
        // ScoringRunStateServiceTest#markFailed_finishedAtNullBeforeCall_setsFinishedAtNow). Call
        // site MOI cua D3 (AggregationOrchestrator, Dot 3) goi ham nay khi luot DA CO finished_at
        // do D2 set tu truoc (moc "D2 cham xong toan bo tieu chi", xem CLAUDE.md muc 2b) - neu
        // khong co guard nay, D3 se ghi de moc do bang thoi diem D3 phat hien loi toan ven, lam
        // mat dau vet that su "khi nao D2 cham xong", sai lech audit (xem
        // ScoringRunStateServiceTest#markFailed_finishedAtAlreadySetBeforeCall_keepsOriginalTimestamp).
        if (run.getFinishedAt() == null) {
            run.setFinishedAt(Instant.now());
        }
        run.setErrorMessage(errorCode.formatted());
        scoringRunRepository.save(run);

        JobApplication application =
                jobApplicationRepository.findById(run.getApplicationId()).orElseThrow();
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

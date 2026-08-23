package com.recruitment.scoring;

import com.recruitment.ai.criterion.CriterionScoringErrorCode;
import com.recruitment.ai.criterion.CriterionScoringFailedException;
import com.recruitment.ai.criterion.CriterionScoringResult;
import com.recruitment.ai.criterion.CriterionScoringService;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ResumeParsedDataRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// KHONG method nao o day duoc @Transactional - xem CLAUDE.md muc 3c va ResumeParsingOrchestrator.
// Vong lap tieu chi goi CriterionScoringService.score() (LLM, co the toi vai chuc giay MOI tieu chi)
// NGOAI bat ky transaction nao; ghi ket qua qua ScoringRunStateService.recordCriterionScore() bang
// mot transaction NGAN rieng NGAY SAU MOI tieu chi (khong doi het vong lap moi ghi mot lan - Q4, ke
// hoach D2: mot tieu chi loi thi ca luot loi, nhung cac dong da ghi truoc do van giu nguyen).
@Component
public class ScoringRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ScoringRunOrchestrator.class);

    private final ScoringRunStateService stateService;
    private final ScoringRunRepository scoringRunRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final CriterionScoringService criterionScoringService;
    private final CriterionScoreRepository criterionScoreRepository;

    public ScoringRunOrchestrator(
            ScoringRunStateService stateService,
            ScoringRunRepository scoringRunRepository,
            JobApplicationRepository jobApplicationRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            CriterionScoringService criterionScoringService,
            CriterionScoreRepository criterionScoreRepository) {
        this.stateService = stateService;
        this.scoringRunRepository = scoringRunRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.criterionScoringService = criterionScoringService;
        this.criterionScoreRepository = criterionScoreRepository;
    }

    public void processOne(UUID scoringRunId) {
        if (!stateService.claim(scoringRunId)) {
            return;
        }
        try {
            doProcess(scoringRunId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi cung: BAT KY loi khong luong truoc nao sau khi da claim (vd
            // resume_parsed_data bien mat, don ung tuyen bi xoa...) deu phai duoc bat va chuyen
            // thanh markFailed. QUAN TRONG HON o D1: mot luot cham ket vinh vien o RUNNING/
            // finished_at NULL khong chi "kep" nhu resume PROCESSING, ma con bi
            // uq_scoring_run_in_progress (V4) chan CUNG moi lan HR thu cham lai don do sau nay -
            // khong con duong nao khac tao duoc luot moi cho ung dung nay.
            log.debug("Loi khong luong truoc trong luc cham diem: scoringRunId={}", scoringRunId, e);
            stateService.markFailed(scoringRunId, ScoringRunErrorCode.UNEXPECTED_ERROR);
        }
    }

    private void doProcess(UUID scoringRunId) {
        ScoringRun run = scoringRunRepository.findById(scoringRunId).orElseThrow();
        JobApplication application =
                jobApplicationRepository.findById(run.getApplicationId()).orElseThrow();
        String rawText = resumeParsedDataRepository
                .findByResumeId(application.getResumeId())
                .orElseThrow()
                .getRawText();

        // Dot 4g (chore/hardening) - bo qua tieu chi DA CO criterion_scores cho dung scoringRunId
        // nay (lan thu truoc da cham xong truoc khi loi tam thoi xay ra o mot tieu chi khac), tranh
        // goi lai LLM cho tieu chi da xong. So khop bang TEN CHINH XAC (criterionNameSnapshot,
        // KHONG chuan hoa hoa/thuong) - snapshot chup nguyen van, va NOT NULL/duy nhat trong pham vi
        // mot rubric nho uq_criterion_name_per_rubric, nen an toan hon criterionId (co the NULL sau
        // khi HR xoa tieu chi goc, FK ON DELETE SET NULL - xem CriterionScore.java).
        Set<String> alreadyScored = criterionScoreRepository.findByScoringRunId(scoringRunId).stream()
                .map(CriterionScore::getCriterionNameSnapshot)
                .collect(Collectors.toSet());

        for (RubricSnapshot.CriterionSnapshot criterion : run.getRubricSnapshot().criteria()) {
            if (alreadyScored.contains(criterion.name())) {
                continue;
            }
            CriterionScoringResult result;
            try {
                result = criterionScoringService.score(criterion, rawText);
            } catch (CriterionScoringFailedException e) {
                // Dot 4e (chore/hardening) - loi TAM THOI di qua markTemporaryFailure de tu dong thu
                // lai; cac ma con lai (JSON hong, score/evidence khong hop le, loi vinh vien) giu
                // nguyen duong markFailed cu. Q4 (ke hoach D2) van dung: MOT tieu chi loi la CA luot
                // loi (tru truong hop tam thoi se duoc thu lai), dung vong lap NGAY - cac dong
                // criterion_scores da ghi truoc do van giu nguyen (khong xoa), phuc vu audit.
                if (e.errorCode() == CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE) {
                    stateService.markTemporaryFailure(scoringRunId, e.errorCode());
                } else {
                    stateService.markFailed(scoringRunId, e.errorCode());
                }
                return;
            }
            try {
                stateService.recordCriterionScore(scoringRunId, criterion, result);
            } catch (DataIntegrityViolationException e) {
                if (!isUniqueViolation(e, "uq_score_per_criterion")) {
                    throw e; // Vi pham rang buoc khac - de catch-all cua processOne xu ly nhu cu.
                }
                // Dot 4g - mot worker khac (vd worker "thang" trong race voi mot worker "zombie" vua
                // het stale-timeout, Dot 4h) da ghi dung tieu chi nay truoc. Du lieu VAN DUNG (chi
                // mot dong ton tai, DB da chan dong thu hai) - day khong phai loi, chi la thua mot
                // cuoc dua vo hai. TUYET DOI KHONG markFailed o day: markFailed dieu kien
                // status='RUNNING' se THANH CONG (lot dung la RUNNING, do worker thang vua claim) va
                // giet nham lot dang cham do cua worker thang. Bo qua tieu chi nay, tiep tuc vong lap.
                log.warn(
                        "Tieu chi da duoc mot luong khac ghi truoc (thua race, du lieu van dung): "
                                + "scoringRunId={}, criterion={}",
                        scoringRunId,
                        criterion.name());
            }
        }

        stateService.markFinished(scoringRunId);
    }

    // Khop theo TEN rang buoc qua PSQLException.getServerErrorMessage().getConstraint() - xac minh
    // bang javap tren postgresql-42.7.11.jar that (khong doan/khong khop chuoi message tu do nhu
    // GlobalExceptionHandler.handleDataIntegrityViolation dang lam cho cac rang buoc khac).
    private static boolean isUniqueViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return constraintName.equals(psql.getServerErrorMessage().getConstraint());
            }
            cause = cause.getCause();
        }
        return false;
    }
}

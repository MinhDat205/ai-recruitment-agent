package com.recruitment.scoring;

import com.recruitment.ai.criterion.CriterionScoringFailedException;
import com.recruitment.ai.criterion.CriterionScoringResult;
import com.recruitment.ai.criterion.CriterionScoringService;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ResumeParsedDataRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ScoringRunOrchestrator(
            ScoringRunStateService stateService,
            ScoringRunRepository scoringRunRepository,
            JobApplicationRepository jobApplicationRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            CriterionScoringService criterionScoringService) {
        this.stateService = stateService;
        this.scoringRunRepository = scoringRunRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.criterionScoringService = criterionScoringService;
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

        for (RubricSnapshot.CriterionSnapshot criterion : run.getRubricSnapshot().criteria()) {
            CriterionScoringResult result;
            try {
                result = criterionScoringService.score(criterion, rawText);
            } catch (CriterionScoringFailedException e) {
                // Q4 (ke hoach D2): MOT tieu chi loi la CA luot loi, khong cham-duoc-bao-nhieu-ghi-
                // bay-nhieu. Dung vong lap NGAY, KHONG goi cac tieu chi con lai - cac dong
                // criterion_scores da ghi truoc do van giu nguyen (khong xoa), phuc vu audit.
                stateService.markFailed(scoringRunId, e.errorCode());
                return;
            }
            stateService.recordCriterionScore(scoringRunId, criterion, result);
        }

        stateService.markFinished(scoringRunId);
    }
}

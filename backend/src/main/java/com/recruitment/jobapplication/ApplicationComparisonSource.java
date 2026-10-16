package com.recruitment.jobapplication;

import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoringRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H14 (REQUIREMENT muc 4.2) - du lieu MOT don cho bang so sanh, rut tu dung dong ma danh sach theo Job dung
// (ApplicationOwnerService.evaluateApplications -> rankApplications): mot cong thuc xep hang duy nhat (FR-H05), hang
// o day luon bang hang o danh sach. Record RIENG cho H14 - ApplicationEvaluation (E3/E4 cua FR-H09) khong doi.
// scoringRunId/scoredAt/totalScore/rank/criterionScores/rubricCriteria/explanation* = lot DONE moi nhat (null/rong
// khi don chua co lot DONE nao). latestRunStatus = lot moi nhat BAT KE trang thai (nguon newerRunInProgress, R-D10).
// rank do Backend tinh (FR-H05), khong phai AI. KHONG co field verdict/label/... (CLAUDE.md muc 7).
public record ApplicationComparisonSource(
        UUID applicationId,
        String candidateName,
        ApplicationStatus status,
        Instant appliedAt,
        UUID resumeId,
        int recruitmentCycle,
        Instant screeningCapturedAt,
        UUID scoringRunId,
        Instant scoredAt,
        BigDecimal totalScore,
        Integer rank,
        ScoringRunStatus latestRunStatus,
        List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores,
        List<RubricSnapshot.CriterionSnapshot> rubricCriteria,
        ApplicationHrListItemResponse.ExplanationStatus explanationStatus,
        ApplicationHrListItemResponse.Explanation explanation) {

    public ApplicationComparisonSource {
        criterionScores = criterionScores == null ? List.of() : criterionScores;
        rubricCriteria = rubricCriteria == null ? List.of() : rubricCriteria;
    }
}

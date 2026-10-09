package com.recruitment.jobapplication;

import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H09 Q1 - ket qua cham cua MOT don, rut tu dung dong ma danh sach theo Job dung
// (ApplicationOwnerService.evaluateApplication). Moi field null/rong khi don chua co lot DONE nao.
// scoringRunId = lot DONE moi nhat: diem, tieu chi va giai thich deu cua CHINH lot nay (R-D3, R-D6).
// rank do Backend tinh (FR-H05), khong phai AI. KHONG co field verdict/label/... (CLAUDE.md muc 7).
public record ApplicationEvaluation(
        UUID scoringRunId,
        Instant scoredAt,
        BigDecimal totalScore,
        Integer rank,
        List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores,
        ApplicationHrListItemResponse.ExplanationStatus explanationStatus,
        ApplicationHrListItemResponse.Explanation explanation) {

    public ApplicationEvaluation {
        criterionScores = criterionScores == null ? List.of() : criterionScores;
    }
}

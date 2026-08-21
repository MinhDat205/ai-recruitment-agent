package com.recruitment.scoring.dto;

import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.scoring.ScoringRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// GET /api/hr/candidates/{applicationId}/audit/scoring-runs (F3, FR-H08) - lich su MOI luot cham
// cua MOT don, phuc vu audit/doi chieu tuan thu. KHAC HAN ScoringRunResponse cua D2/FR-H04
// (endpoint /api/hr/applications/{id}/scoring-runs, chi phuc vu FE poll tien do, KHONG co
// model/promptVersion/tokenUsage/diem tung tieu chi/thong tin bao cao) - hai DTO/path tach rieng
// co chu dinh, khong dung chung.
//
// criterionScores tai su dung DUNG type ApplicationHrListItemResponse.CriterionScoreItem (D3/D4,
// khong dinh nghia lai record trung cau truc) - criterion_scores KHONG co cot model/prompt_version
// rieng (chi scoring_runs va score_explanations co), nen CriterionScoreItem khong co hai truong do
// la dung, model/promptVersion cua CA luot cham nam o cap ngoai (scoringRun) trong record nay.
//
// explanation CHI chua model/promptVersion/generatedAt cua score_explanations (dung log ky thuat/
// lineage) - KHONG chua summary/strengths/weaknesses (noi dung bao cao da co san o Sheet cua D4,
// khong lap lai o day). null khi luot nay CHUA co bao cao (DONE nhung D4 chua sinh xong, hoac
// khong phai DONE).
public record ScoringRunAuditItemResponse(
        UUID scoringRunId,
        ScoringRunStatus status,
        BigDecimal totalScore,
        String model,
        String promptVersion,
        Integer tokenUsage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores,
        ExplanationMeta explanation) {

    public ScoringRunAuditItemResponse {
        criterionScores = criterionScores == null ? List.of() : criterionScores;
    }

    public record ExplanationMeta(String model, String promptVersion, Instant generatedAt) {}
}

package com.recruitment.jobapplication.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H09 E3 - GET /api/hr/applications/{id}/scores: diem cua lot DONE MOI NHAT (R-D3). Moi field
// null, criterionScores rong khi don chua co lot DONE nao (chua cham, chi toan FAILED, hoac dang
// cham dang) - khong suy dien gia tri thay the. Tien do lot moi nhat (bat ke trang thai) KHONG o day,
// frontend doc qua GET .../scoring-runs co san.
//
// totalScore/rank do Backend tinh bang CHINH code FR-H05 cua danh sach theo Job (R-D4) - rank luon
// bang rank cua don nay o GET /api/hr/jobs/{jobId}/applications. criterionScores dung lai
// CriterionScoreItem (reasoning + evidence) theo thu tu rubric_snapshot cua lot do (R-D5).
// KHONG co field verdict/label/isQualified/passed/recommendation (CLAUDE.md muc 7).
public record ApplicationScoresResponse(
        UUID scoringRunId,
        Instant scoredAt,
        BigDecimal totalScore,
        Integer rank,
        List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores) {

    public ApplicationScoresResponse {
        criterionScores = criterionScores == null ? List.of() : criterionScores;
    }
}

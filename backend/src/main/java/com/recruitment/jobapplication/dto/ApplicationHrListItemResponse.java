package com.recruitment.jobapplication.dto;

import com.recruitment.resume.ParseStatus;
import com.recruitment.scoring.ScoringRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// GET /api/hr/jobs/{jobId}/applications - danh sach don CHO HR XEM TIEN DO + KET QUA XEP HANG (D4,
// FR-H05, mo rong tu D2). latestScoringRun* van CHI phan anh trang thai/tien do cua lot cham GAN
// NHAT (co the dang chay/FAILED), KHONG lien quan gi toi totalScore/rank/criterionScores - ba
// truong sau doc tu lot DONE MOI NHAT cua don (Q5, ke hoach D3), co the la mot lot KHAC voi lot
// dang hien thi tien do.
//
// totalScore/rank = null khi don chua co lot DONE nao (chua cham, chi toan FAILED, hoac dang cham
// dang) - KHONG suy dien gia tri thay the (vd 0), de tang doc phan biet duoc "chua co diem" voi
// "diem bang 0 that". rank tinh o Java theo kieu 1-2-2-4 (hoa diem -> cung hang), xem
// ApplicationOwnerService.
//
// criterionScores: diem TUNG tieu chi cua lot DONE do, CO Y mang reasoning (da co san tu D2, khong
// phai AI sinh moi o day) nhung KHONG mang evidence (viec hien thi trich dan nguyen van la cua D4,
// xem ke hoach D3 Q6) va KHONG co bat ky field ten verdict/label/isQualified/passed/recommendation
// nao (CLAUDE.md muc 7).
public record ApplicationHrListItemResponse(
        UUID id,
        String candidateName,
        ParseStatus resumeParseStatus,
        Instant appliedAt,
        UUID latestScoringRunId,
        ScoringRunStatus latestScoringRunStatus,
        Instant latestScoringRunFinishedAt,
        BigDecimal totalScore,
        Integer rank,
        List<CriterionScoreItem> criterionScores) {

    public ApplicationHrListItemResponse {
        criterionScores = criterionScores == null ? List.of() : criterionScores;
    }

    // maxScoreSnapshot la int nguyen thuy (khong wrapper) - khop CriterionScore.maxScoreSnapshot
    // (cot NOT NULL, luon co gia tri). score/weightSnapshot la BigDecimal khop NUMERIC(5,2).
    public record CriterionScoreItem(
            String criterionNameSnapshot, BigDecimal score, int maxScoreSnapshot, BigDecimal weightSnapshot, String reasoning) {
    }
}

package com.recruitment.jobapplication.dto;

import com.recruitment.resume.ParseStatus;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import com.recruitment.scoring.ScoringRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// GET /api/hr/jobs/{jobId}/applications - danh sach don CHO HR XEM TIEN DO + KET QUA XEP HANG (D4,
// FR-H05, mo rong tu D2). latestScoringRun* van CHI phan anh trang thai/tien do cua lot cham GAN
// NHAT (co the dang chay/FAILED), KHONG lien quan gi toi totalScore/rank/criterionScores - ba
// truong sau doc tu lot DONE MOI NHAT cua don (Q5, ke hoach D3), co the la mot lot KHAC voi lot
// dang hien thi tien do. explanation/explanationStatus (D4, FR-H06, Dot 4) doc theo DUNG lot DONE
// do (KHONG lot khac) - xem ApplicationOwnerService.
//
// totalScore/rank = null khi don chua co lot DONE nao (chua cham, chi toan FAILED, hoac dang cham
// dang) - KHONG suy dien gia tri thay the (vd 0), de tang doc phan biet duoc "chua co diem" voi
// "diem bang 0 that". rank tinh o Java theo kieu 1-2-2-4 (hoa diem -> cung hang), xem
// ApplicationOwnerService.
//
// criterionScores: diem TUNG tieu chi cua lot DONE do, mang reasoning VA evidence (ca hai da co san
// tu D2, khong phai AI sinh moi o day - xem CriterionScore.evidence) va KHONG co bat ky field ten
// verdict/label/isQualified/passed/recommendation nao (CLAUDE.md muc 7).
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
        List<CriterionScoreItem> criterionScores,
        ExplanationStatus explanationStatus,
        Explanation explanation) {

    public ApplicationHrListItemResponse {
        criterionScores = criterionScores == null ? List.of() : criterionScores;
    }

    // maxScoreSnapshot la int nguyen thuy (khong wrapper) - khop CriterionScore.maxScoreSnapshot
    // (cot NOT NULL, luon co gia tri). score/weightSnapshot la BigDecimal khop NUMERIC(5,2).
    // evidence tai su dung DUNG type scoring.EvidenceEntry (khong dinh nghia lai record trung lap) -
    // cung tinh than voi ExplanationPoint ben duoi.
    public record CriterionScoreItem(
            String criterionNameSnapshot,
            BigDecimal score,
            int maxScoreSnapshot,
            BigDecimal weightSnapshot,
            String reasoning,
            List<EvidenceEntry> evidence) {

        public CriterionScoreItem {
            evidence = evidence == null ? List.of() : evidence;
        }
    }

    // Bao cao giai thich (FR-H06) cua DUNG lot DONE dang hien thi diem/rank o dong nay. summary/
    // strengths/weaknesses la LLM sinh (Dot 2, khong duoc chua trich dan nguyen van CV - xem
    // ScoreExplanationService); metCriteria/missingCriteria la Java thuan tinh tu score > 0 (Dot 3,
    // ScoreExplanationOrchestrator) - khong phai LLM sinh. KHONG co field verdict/label/isQualified/
    // passed/recommendation (CLAUDE.md muc 7) - day la bao cao mo ta, khong phai phan quyet.
    public record Explanation(
            String summary,
            List<ExplanationPoint> strengths,
            List<ExplanationPoint> weaknesses,
            List<String> metCriteria,
            List<String> missingCriteria) {
    }

    // Tin hieu THUAN TUY VE TRANG THAI XU LY nen (khong mang ham y danh gia ung vien) - phan biet
    // "chua toi luot/con co the duoc thu lai" voi "da thu du so lan cho phep
    // (app.explanation.max-attempts), se KHONG con duoc thu lai nua". CHI duoc ghi khac null khi lot
    // DONE nay CHUA co explanation (explanation != null tu no da la tin hieu "xong", khong can lap
    // lai o day) VA don da co it nhat mot lot DONE (giong quy uoc totalScore/rank == null khi chua co
    // lot DONE nao). Dat ten PENDING/FAILED theo dung tien le ScoringRunStatus/JobApplicationStatus
    // da dung trong du an - trung ten nhung khac enum, khac ngu canh, khong xung dot.
    public enum ExplanationStatus {
        PENDING,
        FAILED
    }
}

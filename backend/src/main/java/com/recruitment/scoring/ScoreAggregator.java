package com.recruitment.scoring;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

// Ham thuan (KHONG @Service): khong co state, khong can Spring inject gi - dung mau
// RubricSnapshotMapper (constructor private, method static). FR-H05 (D3): total =
// Sigma(score_i/max_i * weight_i) / Sigma(weight_i) * 100, xem CLAUDE.md muc 2b va ke hoach D3 Q4.
// KHONG goi LLM o bat ky dau (rang buoc cung nhat cua nhanh nay) - day la phep cong co trong so
// xac dinh, kiem chung duoc bang tay (xem ScoreAggregatorTest).
//
// Chuan hoa ve thang 100 bang CACH CHIA CHO Sigma(weight_i) THUC TE - khong gia dinh no da bang
// 100. Khi Sigma(weight_i) = 100 (truong hop binh thuong, da kiem luc TAO luot cham o
// ScoringRunService.requireRubricComplete) cong thuc nay cho ket qua giong het cach viet "khong
// chia" trong PHASES.md; khi rubric_snapshot cu bi lech (ly thuyet khong xay ra nhung snapshot la
// du lieu cu khong doi lai duoc) no tu chuan hoa dung ma khong can nhanh re rieng trong code. Tang
// goi (Dot 3, AggregationOrchestrator) doc weightSum tra ve trong AggregationResult de log.warn
// khi lech 100 kem scoringRunId - lop nay KHONG tu log, khong biet scoringRunId.
public final class ScoreAggregator {

    // Do chinh xac lam viec noi bo cho phep chia (score_i/max_i) - cao hon nhieu so voi scale luu
    // DB (NUMERIC(6,3)) de khong mat do chinh xac truoc khi cong don nhieu tieu chi, va de tranh
    // ArithmeticException khi ket qua chia la so thap phan vo han tuan hoan (vd 1/3). 20 chu so co
    // nghia du du cho moi rubric thuc te (toi da vai chuc tieu chi).
    private static final MathContext DIVISION_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    // Khop NUMERIC(6,3) cua scoring_runs.total_score.
    private static final int TOTAL_SCORE_SCALE = 3;

    private ScoreAggregator() {
    }

    // Cong don o do chinh xac cao (DIVISION_CONTEXT) qua TAT CA tieu chi TRUOC, roi moi lam tron
    // MOT LAN DUY NHAT o buoc cuoi cung (setScale) - KHONG lam tron tung tieu chi roi moi cong.
    // Lam tron som cong don sai so qua nhieu tieu chi (vd 3 tieu chi moi cai lech toi 0.0005 sau
    // lam tron -> tong co the lech toi 0.0015 so voi lam tron mot lan); lam tron mot lan o cuoi
    // vua chinh xac hon vua giong cach mot nguoi cam may tinh tay se lam (cong het roi moi lam
    // tron) - de doi chieu bang tay hon. Xem ScoreAggregatorTest, case chung minh hai cach cho ket
    // qua khac nhau bang mot bo so cu the.
    public static AggregationResult aggregate(List<CriterionScoreInput> criteria) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (CriterionScoreInput criterion : criteria) {
            BigDecimal ratio = criterion.score().divide(BigDecimal.valueOf(criterion.maxScore()), DIVISION_CONTEXT);
            weightedSum = weightedSum.add(ratio.multiply(criterion.weight()));
            weightSum = weightSum.add(criterion.weight());
        }

        // Danh sach rong cho weightSum = 0 mot cach tu nhien (tong tren tap rong) - khong can
        // nhanh re rieng cho truong hop "danh sach rong", no tu quy ve dung nhanh loi ben duoi (xem
        // ScoreAggregatorTest, case danh sach rong).
        if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
            throw new ScoreAggregationException(
                    "Tổng trọng số (weight_snapshot) của lượt chấm này bằng 0, không thể chuẩn hoá về thang 100.");
        }

        BigDecimal totalScore = weightedSum
                .divide(weightSum, DIVISION_CONTEXT)
                .multiply(ONE_HUNDRED)
                .setScale(TOTAL_SCORE_SCALE, RoundingMode.HALF_UP);
        return new AggregationResult(totalScore, weightSum);
    }
}

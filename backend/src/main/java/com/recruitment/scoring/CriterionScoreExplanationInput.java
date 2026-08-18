package com.recruitment.scoring;

import java.math.BigDecimal;

// Du lieu gui sang ai/explanation (Dot 2, FR-H06) khi tong hop bao cao giai thich cho MOT luot cham
// DONE - mau CriterionScoreInput (ke hoach D3) nhung day du hon: D4 (khac D3) can criterionName de
// LLM tra loi dung ve DUNG tieu chi trong strengths/weaknesses/metCriteria/missingCriteria (kiem tap
// dong o Dot 2), va can reasoning (D2 da sinh san) de LLM co nguyen lieu ma tong hop.
//
// CO Y KHONG co evidence (Q1, ke hoach D4, dot duyet lai): khong dua nguyen lieu de LLM chep lai/dien
// giai thanh "trich dan" trong bao cao tong - day la lop phong thu manh nhat cho nguyen tac "khong
// sinh evidence moi" (Q2). metCriteria/missingCriteria KHONG doc tu record nay o Dot 2 - Q3 da chot
// hai truong do tinh bang Java thuan (score > 0 / score = 0) o ScoreExplanationOrchestrator (Dot 3),
// khong nho LLM phan doan.
public record CriterionScoreExplanationInput(
        String criterionName, BigDecimal weightSnapshot, int maxScoreSnapshot, BigDecimal score, String reasoning) {
}

package com.recruitment.scoring;

import java.math.BigDecimal;

// Input cho MOT tieu chi da cham, dung cho ScoreAggregator.aggregate() - CO Y tao record rieng,
// KHONG tai su dung entity CriterionScore truc tiep: CriterionScore la @Entity keo theo JPA
// (annotation, hanh vi lazy tiem an) vao mot lop tinh toan thuan tuy, va mang theo nhieu truong
// khong lien quan gi toi phep cong co trong so (id, scoringRunId, criterionId, reasoning,
// evidence, createdAt). Dot 2 se anh xa CriterionScore -> CriterionScoreInput ngay o tang doc DB
// (noi da san JPA/repository), giu ScoreAggregator khong phu thuoc JPA, test duoc bang Java thuan
// khong can Spring context/Testcontainers (xem ScoreAggregatorTest).
//
// CO Y KHONG co truong criterionId: phep cong trong so khong bao gio can biet tieu chi goc con
// song hay da bi HR xoa giua luc chup snapshot va luc cham (criterion_scores.criterion_id = NULL o
// DB trong tinh huong do, xem CriterionScore.java) - viec record nay khong co truong do LA chinh
// co che dam bao ScoreAggregator khong the vo tinh phu thuoc vao no, khong chi la ky luat "dung
// dung toi" (xem ScoreAggregatorTest, case tieu chi da bi xoa).
//
// maxScore la int nguyen thuy (khong wrapper) - khop max_score_snapshot cua CriterionScore (cot
// NOT NULL, luon co gia tri tu snapshot). score/weight la BigDecimal khop NUMERIC(5,2) cua
// score/weight_snapshot.
public record CriterionScoreInput(BigDecimal score, int maxScore, BigDecimal weight) {
}

package com.recruitment.scoring;

import java.math.BigDecimal;

// Ket qua tinh cua ScoreAggregator.aggregate(). totalScore da lam tron dung NUMERIC(6,3) cua
// scoring_runs.total_score (setScale(3, HALF_UP) o buoc cuoi cung, xem ScoreAggregator).
//
// weightSum la TONG THAT cua weight_snapshot da dung de chuan hoa (ve ly thuyet luon la 100, da
// kiem luc TAO luot cham o ScoringRunService.requireRubricComplete - nhung snapshot la du lieu cu
// khong doi lai duoc, nen co the lech neu du lieu that su co van de). Tang goi (Dot 3,
// AggregationOrchestrator) doc gia tri nay de log.warn khi lech 100 kem scoringRunId -
// ScoreAggregator KHONG tu log, khong biet scoringRunId.
public record AggregationResult(BigDecimal totalScore, BigDecimal weightSum) {
}

package com.recruitment.scoring;

// Nem khi khong the chuan hoa duoc phep tinh (Sigma weight_snapshot = 0, xem
// ScoreAggregator.aggregate) - loi toan ven du lieu. Ve ly thuyet khong xay ra (rubric phai du
// 100% trong so moi tao duoc luot cham, xem ScoringRunService.requireRubricComplete) nhung
// rubric_snapshot la du lieu cu chup tai thoi diem tao luot, khong doi lai duoc.
//
// Dot 2 (AggregationOrchestrator) se bat exception nay va anh xa sang mot FormattedErrorCode cu
// the (ScoreAggregationErrorCode, chua tao o Dot 1) roi goi ScoringRunStateService.markFailed - o
// day chi can mot kieu loi ro nghia de nem/bat, chua can gan ma loi chuan hoa.
public class ScoreAggregationException extends RuntimeException {

    public ScoreAggregationException(String message) {
        super(message);
    }
}

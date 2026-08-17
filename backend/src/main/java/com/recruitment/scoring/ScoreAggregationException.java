package com.recruitment.scoring;

// Nem tu ScoreAggregator.aggregate() (Sigma weight_snapshot = 0) hoac
// AggregationIntegrityChecker.requireMatchesSnapshot() (lech tap tieu chi) - gan THANG voi mot
// ScoreAggregationErrorCode (khong con RuntimeException tran nhu o Dot 1), mau
// CriterionScoringFailedException (ai.criterion). Tang goi (Dot 3, AggregationOrchestrator) bat
// exception nay va goi ScoringRunStateService.markFailed(scoringRunId, e.errorCode()).
public class ScoreAggregationException extends RuntimeException {

    private final ScoreAggregationErrorCode errorCode;

    public ScoreAggregationException(ScoreAggregationErrorCode errorCode) {
        super(errorCode.formatted());
        this.errorCode = errorCode;
    }

    public ScoreAggregationErrorCode errorCode() {
        return errorCode;
    }
}

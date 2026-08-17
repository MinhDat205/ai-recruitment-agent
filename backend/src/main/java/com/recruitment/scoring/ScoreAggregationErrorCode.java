package com.recruitment.scoring;

import com.recruitment.common.FormattedErrorCode;

// Ma loi chuan hoa cho scoring_runs.error_message khi D3 (FR-H05) khong the tong hop duoc, mau
// ScoringRunErrorCode (D2).
//
// CRITERIA_MISMATCH: tap ten tieu chi da cham (criterion_scores.criterion_name_snapshot) khong
// khop TAP ten tieu chi trong chinh rubric_snapshot cua luot do (xem AggregationIntegrityChecker) -
// ve ly thuyet khong xay ra vi mot tieu chi loi la CA luot loi (D2, xem
// docs/walkthrough/fr-h04-scoring.md muc 4e), day la luoi an toan truoc khi cong.
//
// INVALID_WEIGHT_SUM: Sigma(weight_snapshot) cua rubric_snapshot bang 0, khong the chia de chuan
// hoa ve thang 100 (xem ScoreAggregator.aggregate).
public enum ScoreAggregationErrorCode implements FormattedErrorCode {

    CRITERIA_MISMATCH(
            "Số tiêu chí đã chấm không khớp với rubric đã chụp lúc tạo lượt chấm này, không thể tính tổng điểm"),
    INVALID_WEIGHT_SUM(
            "Tổng trọng số của rubric đã chụp lúc tạo lượt chấm này bằng 0, không thể chuẩn hoá về thang 100");

    private final String description;

    ScoreAggregationErrorCode(String description) {
        this.description = description;
    }

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}

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
//
// UNEXPECTED_ERROR: luoi an toan cuoi cung o AggregationOrchestrator.processOne (Dot 3) - mau
// ScoringRunErrorCode.UNEXPECTED_ERROR cua D2 nhung KHONG tai su dung thang enum do: mo ta cua
// ScoringRunErrorCode noi ro "trong qua trinh cham diem" (giai doan D2, goi LLM), con loi cua D3
// xay ra sau khi cham diem da xong, trong qua trinh TONG HOP - dung lai nguyen van se sai giai
// doan trong error_message hien thi cho HR. Moi orchestrator co ma catch-all rieng, dung mo ta
// dung giai doan cua chinh no (tien le da co tu D1/D2, khong phai D3 tu bia them quy uoc moi).
public enum ScoreAggregationErrorCode implements FormattedErrorCode {

    CRITERIA_MISMATCH(
            "Số tiêu chí đã chấm không khớp với rubric đã chụp lúc tạo lượt chấm này, không thể tính tổng điểm"),
    INVALID_WEIGHT_SUM(
            "Tổng trọng số của rubric đã chụp lúc tạo lượt chấm này bằng 0, không thể chuẩn hoá về thang 100"),
    UNEXPECTED_ERROR("Có lỗi không lường trước xảy ra trong quá trình tổng hợp điểm hồ sơ này");

    private final String description;

    ScoreAggregationErrorCode(String description) {
        this.description = description;
    }

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}

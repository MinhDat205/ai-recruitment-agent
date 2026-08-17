package com.recruitment.ai.criterion;

// Ma loi chuan hoa cho ket qua cham mot tieu chi - mau y het ResumeParsingErrorCode. Dot 4 (package
// scoring) doc errorCode() tu CriterionScoringFailedException va anh xa sang thong diep tieng Viet
// co dinh ghi vao scoring_runs.error_message qua formatted(), khong bao gio ghi stack trace hay
// output tho cua LLM vao DB (chi log.debug).
public enum CriterionScoringErrorCode {
    LLM_INVALID_JSON("AI trả về dữ liệu không đúng định dạng sau khi đã thử lại"),
    LLM_ERROR("Có lỗi xảy ra khi gọi AI để chấm điểm tiêu chí"),
    SCORE_OUT_OF_RANGE("AI trả về điểm số nằm ngoài thang điểm cho phép của tiêu chí"),
    EVIDENCE_MISSING_WITH_NONZERO_SCORE("AI chấm điểm khác 0 nhưng không kèm minh chứng trích dẫn từ CV"),
    EVIDENCE_INVALID_SECTION("AI trả về minh chứng thuộc một mục không hợp lệ trong CV"),
    EVIDENCE_NOT_VERIFIED("Minh chứng AI trích dẫn không khớp với nội dung gốc của CV");

    private final String description;

    CriterionScoringErrorCode(String description) {
        this.description = description;
    }

    public String formatted() {
        return name() + ": " + description;
    }
}

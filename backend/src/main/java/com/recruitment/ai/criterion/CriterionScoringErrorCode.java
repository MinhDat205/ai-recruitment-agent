package com.recruitment.ai.criterion;

import com.recruitment.common.FormattedErrorCode;

// Ma loi chuan hoa cho ket qua cham mot tieu chi - mau y het ResumeParsingErrorCode. Dot 4 (package
// scoring) doc errorCode() tu CriterionScoringFailedException va truyen THANG enum nay (khong phai
// String) vao ScoringRunStateService.markFailed(FormattedErrorCode) - implement FormattedErrorCode
// (com.recruitment.common) de trinh bien dich chan viec ai do lo truyen mot chuoi tu do (stack
// trace, output tho cua LLM) vao cot scoring_runs.error_message. Xem FormattedErrorCode.java ly do
// interface nay dat o common/ chu khong phai scoring/.
public enum CriterionScoringErrorCode implements FormattedErrorCode {
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

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}

package com.recruitment.ai.cvimprovement;

import com.recruitment.common.FormattedErrorCode;

// Ma loi chuan hoa cho ket qua sinh goi y cai thien CV - mau y het ScoreExplanationErrorCode
// (ai/explanation). implement FormattedErrorCode (com.recruitment.common) de trinh bien dich chan
// viec truyen mot chuoi tu do (stack trace, output tho cua LLM) vao cot loi cua cv_improvement_requests.
public enum CvImprovementErrorCode implements FormattedErrorCode {
    LLM_INVALID_JSON("AI trả về dữ liệu không đúng định dạng sau khi đã thử lại"),
    LLM_ERROR("Có lỗi xảy ra khi gọi AI để sinh gợi ý cải thiện CV"),
    EMPTY_RESULT("AI trả về gợi ý rỗng cho cả ba mục"),
    INVALID_SECTION("AI trả về tên mục CV không hợp lệ"),
    INVALID_LEARNING_PATH_ITEM("AI trả về mục lộ trình học tập không hợp lệ"),
    INVALID_MISSING_KEYWORD("AI trả về từ khoá còn thiếu không hợp lệ");

    private final String description;

    CvImprovementErrorCode(String description) {
        this.description = description;
    }

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}

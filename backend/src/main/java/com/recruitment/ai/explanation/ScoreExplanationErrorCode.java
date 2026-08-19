package com.recruitment.ai.explanation;

import com.recruitment.common.FormattedErrorCode;

// Ma loi chuan hoa cho ket qua sinh bao cao giai thich - mau y het CriterionScoringErrorCode (ke
// hoach D2). Dot 3 (package scoring) doc errorCode() tu ScoreExplanationFailedException va truyen
// THANG enum nay (khong phai String) vao ScoreExplanationAttemptRepository.recordFailedAttempt -
// implement FormattedErrorCode (com.recruitment.common) de trinh bien dich chan viec ai do lo truyen
// mot chuoi tu do (stack trace, output tho cua LLM) vao cot last_error.
public enum ScoreExplanationErrorCode implements FormattedErrorCode {
    LLM_INVALID_JSON("AI trả về dữ liệu không đúng định dạng sau khi đã thử lại"),
    LLM_ERROR("Có lỗi xảy ra khi gọi AI để sinh báo cáo giải thích"),
    SUMMARY_BLANK("AI trả về tóm tắt rỗng cho báo cáo giải thích"),
    CRITERION_NAME_MISMATCH("AI nhắc tới một tiêu chí không thuộc danh sách đã chấm của lượt này");

    private final String description;

    ScoreExplanationErrorCode(String description) {
        this.description = description;
    }

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}

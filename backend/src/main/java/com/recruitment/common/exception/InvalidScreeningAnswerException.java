package com.recruitment.common.exception;

// FR-U09 R-V1 - 400 INVALID_SCREENING_ANSWER qua ErrorResponse. Chi nhan enum, khong nhan String tu do - trinh bien dich
// chan viec lo dua cau tra loi cua ung vien vao message.
public class InvalidScreeningAnswerException extends RuntimeException {

    private final ScreeningAnswerViolation violation;

    public InvalidScreeningAnswerException(ScreeningAnswerViolation violation) {
        super(violation.message());
        this.violation = violation;
    }

    public ScreeningAnswerViolation violation() {
        return violation;
    }
}

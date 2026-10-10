package com.recruitment.common.exception;

// FR-H10 R-Q4b - 400 INVALID_SCREENING_QUESTION qua ErrorResponse (frontend doc data.message). Chi nhan enum, khong
// nhan String tu do - trinh bien dich chan viec lo dua noi dung nguoi dung vao message.
public class InvalidScreeningQuestionException extends RuntimeException {

    private final ScreeningQuestionViolation violation;

    public InvalidScreeningQuestionException(ScreeningQuestionViolation violation) {
        super(violation.message());
        this.violation = violation;
    }

    public ScreeningQuestionViolation violation() {
        return violation;
    }
}

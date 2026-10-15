package com.recruitment.common.exception;

// FR-C08 R-H1 - cau hoi khong hop le (400 INVALID_CV_QA_QUESTION). Khuon InvalidDraftRequestException: mot class, moi
// loai loi mot factory, cau tieng Viet co dinh (muc 4.4) - KHONG nhan String tu do.
public class InvalidCvQaQuestionException extends RuntimeException {

    private InvalidCvQaQuestionException(String message) {
        super(message);
    }

    // null / thieu khoa / rong sau trim
    public static InvalidCvQaQuestionException emptyQuestion() {
        return new InvalidCvQaQuestionException("Vui lòng nhập câu hỏi.");
    }

    // dai hon 500 (String.length() sau doi CRLF -> LF va trim)
    public static InvalidCvQaQuestionException questionTooLong() {
        return new InvalidCvQaQuestionException("Câu hỏi tối đa 500 ký tự.");
    }
}

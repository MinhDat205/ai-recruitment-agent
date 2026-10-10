package com.recruitment.common.exception;

// FR-H10 R-Q1 - them cau thu 11.
public class ScreeningQuestionLimitReachedException extends RuntimeException {

    public ScreeningQuestionLimitReachedException() {
        super("Mỗi tin tuyển dụng có tối đa 10 câu hỏi sàng lọc.");
    }
}

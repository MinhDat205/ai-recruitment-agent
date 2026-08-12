package com.recruitment.common.exception;

public class RubricLockedException extends RuntimeException {

    public RubricLockedException() {
        super("Rubric đã bị khoá vì đã có lượt chấm điểm đầu tiên, không thể sửa tiêu chí. "
                + "Muốn thay đổi, hãy tạo một phiên bản rubric mới.");
    }
}

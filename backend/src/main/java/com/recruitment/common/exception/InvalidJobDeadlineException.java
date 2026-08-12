package com.recruitment.common.exception;

public class InvalidJobDeadlineException extends RuntimeException {

    public InvalidJobDeadlineException() {
        super("Hạn nộp không được ở trong quá khứ. Vui lòng chọn từ hôm nay trở đi.");
    }
}

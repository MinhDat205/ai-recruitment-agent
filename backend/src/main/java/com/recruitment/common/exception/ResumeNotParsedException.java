package com.recruitment.common.exception;

public class ResumeNotParsedException extends RuntimeException {

    public ResumeNotParsedException() {
        super("CV của đơn ứng tuyển này chưa được AI trích xuất xong, vui lòng chờ xử lý xong rồi thử lại.");
    }
}

package com.recruitment.common.exception;

// FR-C06 R-Q4 - tin khong ton tai HOAC thuoc don khac (nap bang ca messageId lan applicationId).
public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException() {
        super("Không tìm thấy tin nhắn");
    }
}

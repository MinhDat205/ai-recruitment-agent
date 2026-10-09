package com.recruitment.common.exception;

// FR-C06 R-M4 - don WITHDRAWN: cuoc trao doi chi con xem, khong gui them tin.
public class ConversationReadOnlyException extends RuntimeException {

    public ConversationReadOnlyException() {
        super("Đơn đã rút, không thể gửi thêm tin nhắn");
    }
}

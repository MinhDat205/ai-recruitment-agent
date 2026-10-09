package com.recruitment.common.exception;

// FR-C06 R-M1 - tin khong co noi dung chu (sau R-M2) va khong co tep.
public class MessageEmptyException extends RuntimeException {

    public MessageEmptyException() {
        super("Tin nhắn chưa có nội dung");
    }
}

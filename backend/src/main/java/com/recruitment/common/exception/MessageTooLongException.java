package com.recruitment.common.exception;

// FR-C06 R-M3 - noi dung sau chuan hoa dai hon 4000 (String.length()).
public class MessageTooLongException extends RuntimeException {

    public MessageTooLongException() {
        super("Tin nhắn vượt quá 4000 ký tự");
    }
}

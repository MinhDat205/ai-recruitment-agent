package com.recruitment.common.exception;

import java.util.UUID;

public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException(UUID id) {
        super("Không tìm thấy CV: " + id);
    }

    public ResumeNotFoundException(String message) {
        super(message);
    }
}

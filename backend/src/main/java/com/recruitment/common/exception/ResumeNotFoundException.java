package com.recruitment.common.exception;

import java.util.UUID;

public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException(UUID id) {
        super("Khong tim thay CV: " + id);
    }

    public ResumeNotFoundException(String message) {
        super(message);
    }
}

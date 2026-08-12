package com.recruitment.common.exception;

import java.util.UUID;

public class InterviewTemplateNotFoundException extends RuntimeException {

    public InterviewTemplateNotFoundException(UUID jobId) {
        super("Khong tim thay mau giay moi phong van cho job: " + jobId);
    }
}

package com.recruitment.common.exception;

import java.util.UUID;

public class InterviewTemplateNotFoundException extends RuntimeException {

    public InterviewTemplateNotFoundException(UUID jobId) {
        super("Không tìm thấy mẫu giấy mời phỏng vấn cho job: " + jobId);
    }
}

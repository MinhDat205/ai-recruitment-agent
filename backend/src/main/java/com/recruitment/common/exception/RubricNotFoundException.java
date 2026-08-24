package com.recruitment.common.exception;

import java.util.UUID;

public class RubricNotFoundException extends RuntimeException {

    public RubricNotFoundException(UUID jobId) {
        super("Không tìm thấy rubric cho job: " + jobId);
    }
}

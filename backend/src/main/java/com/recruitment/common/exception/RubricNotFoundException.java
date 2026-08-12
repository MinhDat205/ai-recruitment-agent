package com.recruitment.common.exception;

import java.util.UUID;

public class RubricNotFoundException extends RuntimeException {

    public RubricNotFoundException(UUID jobId) {
        super("Khong tim thay rubric cho job: " + jobId);
    }
}

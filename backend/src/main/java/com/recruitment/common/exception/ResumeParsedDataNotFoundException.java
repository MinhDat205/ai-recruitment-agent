package com.recruitment.common.exception;

import java.util.UUID;

public class ResumeParsedDataNotFoundException extends RuntimeException {

    public ResumeParsedDataNotFoundException(UUID resumeId) {
        super("Chưa có dữ liệu trích xuất cho CV: " + resumeId);
    }
}

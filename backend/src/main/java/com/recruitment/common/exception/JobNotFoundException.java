package com.recruitment.common.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("Không tìm thấy tin tuyển dụng: " + id);
    }
}

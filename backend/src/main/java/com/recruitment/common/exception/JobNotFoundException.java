package com.recruitment.common.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("Khong tim thay tin tuyen dung: " + id);
    }
}

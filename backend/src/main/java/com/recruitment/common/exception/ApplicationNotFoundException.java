package com.recruitment.common.exception;

import java.util.UUID;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(UUID id) {
        super("Khong tim thay don ung tuyen: " + id);
    }
}

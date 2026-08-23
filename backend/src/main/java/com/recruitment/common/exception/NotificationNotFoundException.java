package com.recruitment.common.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID id) {
        super("Khong tim thay thong bao: " + id);
    }
}

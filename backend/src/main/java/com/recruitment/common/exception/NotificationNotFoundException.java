package com.recruitment.common.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID id) {
        super("Không tìm thấy thông báo: " + id);
    }
}

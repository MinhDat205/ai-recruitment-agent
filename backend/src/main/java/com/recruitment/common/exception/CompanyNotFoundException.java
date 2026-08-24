package com.recruitment.common.exception;

import java.util.UUID;

public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(UUID id) {
        super("Không tìm thấy công ty: " + id);
    }

    public CompanyNotFoundException(String message) {
        super(message);
    }
}

package com.recruitment.common.exception;

import java.util.UUID;

public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(UUID id) {
        super("Khong tim thay cong ty: " + id);
    }
}

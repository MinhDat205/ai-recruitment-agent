package com.recruitment.common.exception;

import java.util.UUID;

public class CompanyAlreadyExistsException extends RuntimeException {

    public CompanyAlreadyExistsException(UUID ownerId) {
        super("HR này đã có công ty: " + ownerId);
    }
}

package com.recruitment.common.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email da duoc su dung: " + email);
    }
}

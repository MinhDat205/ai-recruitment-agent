package com.recruitment.common.exception;

// FR-H15 R-K2 - ten kho sau strip() ngoai 1-100 ky tu, null, hoac body P2/P4 khong doc duoc (400 INVALID_TALENT_POOL_NAME).
public class InvalidTalentPoolNameException extends RuntimeException {

    public InvalidTalentPoolNameException() {
        super("Tên kho phải có từ 1 đến 100 ký tự.");
    }
}

package com.recruitment.common.exception;

// FR-C05 R-J5 - ma gui len khong co trong danh muc. Tra 400 qua ErrorResponse (khong phai map field
// cua bean validation) vi frontend doc thong diep tu data.message.
public class InvalidCatalogCodeException extends RuntimeException {

    private InvalidCatalogCodeException(String message) {
        super(message);
    }

    public static InvalidCatalogCodeException industry() {
        return new InvalidCatalogCodeException("Ngành nghề không có trong danh mục.");
    }

    public static InvalidCatalogCodeException province() {
        return new InvalidCatalogCodeException("Tỉnh/thành không có trong danh mục.");
    }
}

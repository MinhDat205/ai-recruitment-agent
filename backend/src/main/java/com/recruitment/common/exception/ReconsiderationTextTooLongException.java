package com.recruitment.common.exception;

// FR-C09 R-V2 - ly do (C1) hoac phan hoi (H2) dai hon 1000 ky tu sau khi chuan hoa xuong dong.
public class ReconsiderationTextTooLongException extends RuntimeException {

    public ReconsiderationTextTooLongException() {
        super("Nội dung tối đa 1000 ký tự.");
    }
}

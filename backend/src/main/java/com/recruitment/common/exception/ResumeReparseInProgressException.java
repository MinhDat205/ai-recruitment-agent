package com.recruitment.common.exception;

// FR-C05 R-R2 - 409: CV da co yeu cau trich xuat lai PENDING/RUNNING. Kiem truoc o service chi de tra
// loi som; chot chan that la uq_resume_reparse_request_active (V8) - service bat vi pham do va nem lai
// chinh exception nay, nen hai duong cung mot ma loi/thong diep.
public class ResumeReparseInProgressException extends RuntimeException {

    public ResumeReparseInProgressException() {
        super("CV đang được cập nhật dữ liệu trích xuất.");
    }
}

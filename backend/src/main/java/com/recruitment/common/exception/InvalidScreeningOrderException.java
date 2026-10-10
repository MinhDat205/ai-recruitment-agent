package com.recruitment.common.exception;

// FR-H10 R-Q6 - danh sach id gui len khong dung bang tap cau dang hoat dong (thieu, thua, trung, id Job khac, id
// da xoa) - thuong do bo cau hoi da doi o tab khac.
public class InvalidScreeningOrderException extends RuntimeException {

    public InvalidScreeningOrderException() {
        super("Danh sách câu hỏi đã thay đổi, vui lòng tải lại.");
    }
}

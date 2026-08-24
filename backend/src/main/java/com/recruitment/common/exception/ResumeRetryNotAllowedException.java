package com.recruitment.common.exception;

// 409 - CV khong o trang thai FAILED luc kiem tra (con PENDING/PROCESSING/DONE), hoac da bi mot
// luong khac doi khoi FAILED giua luc ResumeService kiem tra va luc ghi thuc su (race hiem, xem
// ResumeParsingStateService.retry). Ca hai truong hop deu bao cung mot thong diep - nguoi dung chi
// can tai lai trang de thay trang thai moi nhat, khong can phan biet nguyen nhan.
public class ResumeRetryNotAllowedException extends RuntimeException {

    public ResumeRetryNotAllowedException() {
        super("CV này không ở trạng thái phân tích thất bại, không thể thử lại. Vui lòng tải lại trang.");
    }
}

package com.recruitment.common.exception;

// FR-C09 R-E2 - don da co yeu cau xem xet lai (moi trang thai). GlobalExceptionHandler dung lai CHINH cau nay cho nhanh
// dich uq_reconsideration_per_application, de duong kiem truoc va chot chan o DB tra cung mot ma va mot chuoi.
public class ReconsiderationAlreadyRequestedException extends RuntimeException {

    public ReconsiderationAlreadyRequestedException() {
        super("Bạn đã gửi yêu cầu xem xét lại cho đơn này. Mỗi đơn chỉ gửi được một lần.");
    }
}

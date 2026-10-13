package com.recruitment.common.exception;

// FR-H12 R-G2 - gui lai giay moi khi don khong con INTERVIEW_INVITED -> 409 (B1): "tai nguyen khong con o trang thai cho
// phep hanh dong", cung nhom ApplicationNotWithdrawableException, khong phai 400 INVALID_APPLICATION_STATUS_TRANSITION.
public class InvitationResendNotAllowedException extends RuntimeException {

    public InvitationResendNotAllowedException() {
        super("Chỉ gửi lại giấy mời được khi đơn đang ở trạng thái Đã mời phỏng vấn.");
    }
}

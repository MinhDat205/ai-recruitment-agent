package com.recruitment.common.exception;

// FR-U10 R-W1 buoc 2 - ung vien chon khung khi don khong con INTERVIEW_INVITED (lockIfInterviewInvited tra 0 dong) -> 409:
// tai nguyen khong con o trang thai cho phep hanh dong, cung nhom InvitationResendNotAllowedException.
public class SlotSelectionNotAllowedException extends RuntimeException {

    public SlotSelectionNotAllowedException() {
        super("Chỉ chọn được khung giờ khi đơn đang ở trạng thái Đã mời phỏng vấn.");
    }
}

package com.recruitment.common.exception;

// FR-U10 R-W5 dong 3, 5 - khung khong thuoc giay moi hieu luc (khong ton tai / giay moi cu / don khac - KHONG phan biet) ->
// 409 INTERVIEW_INVITATION_CONFLICT. Cung ma + cung cau voi nhanh uq_invitation_active cua GlobalExceptionHandler (FR-H12
// R-G6): nhanh do lay cau tu chinh exception nay de hai duong tra dung mot chuoi.
public class InterviewInvitationConflictException extends RuntimeException {

    public InterviewInvitationConflictException() {
        super("Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang.");
    }
}

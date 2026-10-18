package com.recruitment.common.exception;

// FR-H16 R-I3 - gui giay moi lan dau cho don dang an danh: sau khi thay {{candidateName}} bang ho ten that, subject > 255
// hoac content > 10000 ky tu (400 INVITATION_RENDERED_TOO_LONG). Khong ghi gi, don giu PENDING.
public class InvitationRenderedTooLongException extends RuntimeException {

    public InvitationRenderedTooLongException() {
        super("Tiêu đề hoặc nội dung thư mời vượt giới hạn sau khi điền họ tên ứng viên.");
    }
}

package com.recruitment.common.exception;

// 409 - hai request PATCH gan nhu dong thoi tren cung mot don (double-click, hai tab HR) cung doc
// duoc mot trang thai goc, chi mot trong hai duoc phep ghi. Nem khi updateStatusIfCurrent (UPDATE co
// dieu kien, xem JobApplicationRepository) tra ve rowcount 0 - trang thai da bi mot request khac
// doi truoc do, khong phai last-write-wins nhu truoc khi sua (Dot 2, chore/hardening).
public class ApplicationStatusConflictException extends RuntimeException {

    public ApplicationStatusConflictException() {
        super("Đơn ứng tuyển đã bị thay đổi trạng thái bởi một thao tác khác, vui lòng tải lại.");
    }
}

package com.recruitment.common.exception;

// Dung String thay vi import ApplicationStatus - common/exception hien khong phu thuoc nguoc vao
// bat ky package tinh nang nao, giu nguyen quy uoc do.
public class InvalidApplicationStatusTransitionException extends RuntimeException {

    public InvalidApplicationStatusTransitionException(String fromStatus, String toStatus) {
        super("Không thể chuyển đơn ứng tuyển từ trạng thái " + fromStatus + " sang " + toStatus + ".");
    }

    // Dung khi tu choi mot duong chuyen trang thai vi LY DO KHAC ngoai ban chuyen tiep hop le/khong
    // hop le (vi du: INTERVIEW_INVITED bat buoc phai co lich hen kem theo, xem
    // ApplicationStatusController - HR phai qua endpoint gui loi moi phong van, khong duoc PATCH
    // thang trang thai nay).
    public InvalidApplicationStatusTransitionException(String message) {
        super(message);
    }
}

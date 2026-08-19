package com.recruitment.common.exception;

// Dung String thay vi import ApplicationStatus - common/exception hien khong phu thuoc nguoc vao
// bat ky package tinh nang nao, giu nguyen quy uoc do.
public class InvalidApplicationStatusTransitionException extends RuntimeException {

    public InvalidApplicationStatusTransitionException(String fromStatus, String toStatus) {
        super("Không thể chuyển đơn ứng tuyển từ trạng thái " + fromStatus + " sang " + toStatus + ".");
    }
}

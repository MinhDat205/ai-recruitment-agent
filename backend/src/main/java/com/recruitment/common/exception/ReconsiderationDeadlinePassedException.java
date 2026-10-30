package com.recruitment.common.exception;

// FR-C09 R-T2 - qua han 168 gio ke tu luc bi tu choi o vong ho so (so bang now() cua Postgres).
public class ReconsiderationDeadlinePassedException extends RuntimeException {

    public ReconsiderationDeadlinePassedException() {
        super("Đã quá hạn 7 ngày để gửi yêu cầu xem xét lại.");
    }
}

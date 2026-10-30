package com.recruitment.common.exception;

// FR-C09 R-E4 (Q1) - tin cua don da xoa mem. Tin qua han nop/PAUSED/CLOSED van gui duoc.
public class ReconsiderationJobRemovedException extends RuntimeException {

    public ReconsiderationJobRemovedException() {
        super("Tin tuyển dụng của đơn này đã bị gỡ nên không gửi được yêu cầu xem xét lại.");
    }
}

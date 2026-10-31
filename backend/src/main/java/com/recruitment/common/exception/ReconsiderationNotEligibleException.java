package com.recruitment.common.exception;

// FR-C09 R-E1 b, c, R-E3 - don khong o REJECTED, hoac bi tu choi sau phong van, hoac khong co dong lich su vao REJECTED.
public class ReconsiderationNotEligibleException extends RuntimeException {

    public ReconsiderationNotEligibleException() {
        super("Chỉ đơn bị từ chối ở vòng hồ sơ mới gửi được yêu cầu xem xét lại.");
    }
}

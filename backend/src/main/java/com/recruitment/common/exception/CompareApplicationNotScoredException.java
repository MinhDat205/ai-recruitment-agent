package com.recruitment.common.exception;

// FR-H14 R-S5 - co don chua co luot cham DONE nao (409 COMPARE_APPLICATION_NOT_SCORED). Cau co dinh, khong chua ten.
public class CompareApplicationNotScoredException extends RuntimeException {

    public CompareApplicationNotScoredException() {
        super("Có đơn chưa có lượt chấm điểm hoàn tất. Chỉ so sánh được các đơn đã chấm xong.");
    }
}

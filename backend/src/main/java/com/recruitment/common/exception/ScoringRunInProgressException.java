package com.recruitment.common.exception;

public class ScoringRunInProgressException extends RuntimeException {

    public ScoringRunInProgressException() {
        super("Đơn ứng tuyển này đang có một lượt chấm điểm chưa hoàn tất, vui lòng chờ lượt trước kết thúc.");
    }
}

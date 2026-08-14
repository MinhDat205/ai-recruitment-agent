package com.recruitment.common.exception;

public class ApplicationNotWithdrawableException extends RuntimeException {

    public ApplicationNotWithdrawableException() {
        super("Đơn này không còn ở trạng thái có thể rút.");
    }
}

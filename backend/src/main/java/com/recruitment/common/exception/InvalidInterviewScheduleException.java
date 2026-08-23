package com.recruitment.common.exception;

public class InvalidInterviewScheduleException extends RuntimeException {

    public InvalidInterviewScheduleException() {
        super("Thời gian phỏng vấn phải ở trong tương lai.");
    }
}

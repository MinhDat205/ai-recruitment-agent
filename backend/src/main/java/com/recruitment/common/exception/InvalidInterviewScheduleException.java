package com.recruitment.common.exception;

// FR-H12 R-S8 - giu ma INVALID_INTERVIEW_SCHEDULE (B1), cau tieng Viet co dinh theo tung truong hop/khung do
// InterviewSlotValidator dung; khong bao gio mang du lieu nguoi dung hay e.getMessage() vao day.
public class InvalidInterviewScheduleException extends RuntimeException {

    public InvalidInterviewScheduleException(String message) {
        super(message);
    }
}

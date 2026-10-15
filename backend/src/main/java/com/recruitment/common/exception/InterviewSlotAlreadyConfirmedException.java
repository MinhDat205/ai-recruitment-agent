package com.recruitment.common.exception;

// FR-U10 R-W5 dong 2, R-W6 (Q3) - giay moi hieu luc da chot khung (ke ca chon lai dung khung da chot) -> 409. Chon mot lan,
// khong tu doi.
public class InterviewSlotAlreadyConfirmedException extends RuntimeException {

    public InterviewSlotAlreadyConfirmedException() {
        super("Giấy mời này đã được chốt khung giờ. Muốn đổi lịch, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi.");
    }
}

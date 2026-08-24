package com.recruitment.common.exception;

import java.util.UUID;

public class InterviewInvitationNotFoundException extends RuntimeException {

    public InterviewInvitationNotFoundException(UUID applicationId) {
        super("Đơn ứng tuyển chưa có giấy mời phỏng vấn: " + applicationId);
    }
}

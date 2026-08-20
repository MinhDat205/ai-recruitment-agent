package com.recruitment.common.exception;

import java.util.UUID;

public class InterviewInvitationNotFoundException extends RuntimeException {

    public InterviewInvitationNotFoundException(UUID applicationId) {
        super("Don ung tuyen chua co giay moi phong van: " + applicationId);
    }
}

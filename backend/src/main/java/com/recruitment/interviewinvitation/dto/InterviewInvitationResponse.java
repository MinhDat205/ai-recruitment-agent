package com.recruitment.interviewinvitation.dto;

import java.time.Instant;
import java.util.UUID;

public record InterviewInvitationResponse(
        UUID id,
        UUID applicationId,
        Instant scheduledAt,
        String location,
        String subject,
        String renderedContent,
        Instant sentAt,
        UUID sentBy) {
}

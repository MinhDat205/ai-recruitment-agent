package com.recruitment.interviewtemplate.dto;

import java.time.Instant;
import java.util.UUID;

public record InterviewTemplateResponse(
        UUID id,
        UUID jobId,
        String companyName,
        String subject,
        String body,
        String senderName,
        String senderTitle,
        String address,
        Instant createdAt,
        Instant updatedAt) {
}

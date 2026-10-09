package com.recruitment.messaging.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

// M5 phia ung vien (FR-C06 muc 4.4) - companyName thay cho ten ben kia; KHONG co ho ten/email HR (R-I2).
public record ConversationCandidateResponse(
        UUID applicationId, UUID jobId, String jobTitle, String companyName,
        ApplicationStatus applicationStatus, Instant lastMessageAt, String lastMessageExcerpt,
        boolean lastMessageMine, boolean lastMessageHasAttachment, int unreadCount) {}

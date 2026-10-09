package com.recruitment.messaging.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

// M5 phia HR (FR-C06 muc 4.4).
public record ConversationHrResponse(
        UUID applicationId, UUID jobId, String jobTitle, String candidateName,
        ApplicationStatus applicationStatus, Instant lastMessageAt, String lastMessageExcerpt,
        boolean lastMessageMine, boolean lastMessageHasAttachment, int unreadCount) {}

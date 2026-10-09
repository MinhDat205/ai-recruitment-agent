package com.recruitment.messaging.dto;

import com.recruitment.messaging.AttachmentType;
import com.recruitment.messaging.MessageSenderRole;
import java.time.Instant;
import java.util.UUID;

// M1, M2 (FR-C06 muc 4.4). KHONG them senderId, readAt, attachmentKey hay URL tep (khoa cam, T6).
public record MessageResponse(
        UUID id,
        MessageSenderRole senderRole, // HR | CANDIDATE
        boolean mine,                 // senderRole == vai tro nguoi goi
        String body,                  // nguyen van sau R-M2; null khi chi co tep
        Attachment attachment,        // null khi khong co tep
        Instant createdAt) {

    public record Attachment(String fileName, AttachmentType fileType, long fileSize) {}
}

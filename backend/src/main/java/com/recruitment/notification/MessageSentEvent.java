package com.recruitment.notification;

import java.util.UUID;

// FR-C06 R-N1 - MessageService.send publish TRONG transaction ghi tin; NotificationEventListener.onMessageSent
// xu ly AFTER_COMMIT. Nguoi nhan suy o listener: sentByHr -> candidateId; nguoc lai -> chu cong ty cua job.
// excerpt da qua MessageExcerpt (R-N2), null khi tin khong co chu. Khong mang diem/rubric/du lieu cham (R-N6).
public record MessageSentEvent(
        UUID applicationId, UUID jobId, UUID candidateId, boolean sentByHr, String excerpt, boolean hasAttachment) {}

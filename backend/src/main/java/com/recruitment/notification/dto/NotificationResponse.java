package com.recruitment.notification.dto;

import com.recruitment.notification.Notification;
import com.recruitment.notification.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String link,
        String entityType,
        UUID entityId,
        boolean isRead,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.getEntityType(),
                n.getEntityId(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}

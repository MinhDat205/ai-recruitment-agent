package com.recruitment.notification;

import java.util.UUID;

// Mot dot cham diem hoan tat (D3, FR-H05) - publish o AggregationOrchestrator.doProcess, NGOAI moi
// transaction (xem CLAUDE.md muc 3c: khong giu transaction ghi cho muc dich thong bao). Vi vay
// NotificationEventListener xu ly event nay bang @EventListener thuong, KHONG phai
// @TransactionalEventListener - khong co gi de hoan toi AFTER_COMMIT.
public record AggregationFinishedEvent(UUID scoringRunId, UUID applicationId, UUID jobId) {}

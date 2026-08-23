package com.recruitment.notification;

import java.util.UUID;

// Don moi (ApplicationService.apply) - nguoi nhan la HR so huu cong ty cua job (suy tu jobId o
// listener, khong mang san ownerId trong event).
public record ApplicationSubmittedEvent(UUID applicationId, UUID jobId, UUID candidateId) {}

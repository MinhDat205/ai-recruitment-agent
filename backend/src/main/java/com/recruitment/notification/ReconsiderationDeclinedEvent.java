package com.recruitment.notification;

import java.util.UUID;

// FR-C09 R-N1, R-N3 - HR giu quyet dinh tu choi (ReconsiderationHrService.decline) - nguoi nhan la ung vien. Khong mang
// phan hoi (Q5).
public record ReconsiderationDeclinedEvent(UUID applicationId, UUID jobId, UUID candidateId) {}

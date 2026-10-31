package com.recruitment.notification;

import java.util.UUID;

// FR-C09 R-N1, R-N2 - ung vien gui yeu cau xem xet lai (ReconsiderationCandidateService.request) - nguoi nhan la HR so
// huu cong ty cua job. Khong mang ly do (R-A3).
public record ReconsiderationRequestedEvent(UUID applicationId, UUID jobId, UUID candidateId) {}

package com.recruitment.notification;

import java.util.UUID;

// Ung vien tu rut don (ApplicationService.withdraw) - nguoi nhan la HR so huu cong ty cua job.
public record ApplicationWithdrawnEvent(UUID applicationId, UUID jobId, UUID candidateId) {}

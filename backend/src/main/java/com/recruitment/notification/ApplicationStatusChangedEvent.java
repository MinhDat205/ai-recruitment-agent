package com.recruitment.notification;

import com.recruitment.jobapplication.ApplicationStatus;
import java.util.UUID;

// HR doi trang thai don (ApplicationStatusService.changeStatus) - nguoi nhan la ung vien
// (candidateId). Publish TRONG transaction chinh, NotificationEventListener xu ly sau AFTER_COMMIT.
public record ApplicationStatusChangedEvent(
        UUID applicationId, UUID jobId, UUID candidateId, ApplicationStatus fromStatus, ApplicationStatus toStatus) {}

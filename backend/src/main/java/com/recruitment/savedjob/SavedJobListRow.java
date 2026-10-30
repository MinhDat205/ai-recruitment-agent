package com.recruitment.savedjob;

import java.time.Instant;
import java.util.UUID;

// FR-U16 Q7 - projection cua SavedJobRepository.findActiveList (khuon ApplicationSummaryView). applicationId/
// applicationStatus null khi chua co don o chu ky hien tai; status la chuoi, service doi sang ApplicationStatus.
public interface SavedJobListRow {

    UUID getSavedJobId();

    UUID getJobId();

    Instant getSavedAt();

    UUID getApplicationId();

    String getApplicationStatus();
}

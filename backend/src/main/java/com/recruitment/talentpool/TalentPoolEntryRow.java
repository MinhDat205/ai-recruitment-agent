package com.recruitment.talentpool;

import java.time.Instant;
import java.util.UUID;

// FR-H15 - interface projection cho native query P8 (TalentPoolEntryRepository.findReadableByPoolId).
// getApplicationStatus() la String, KHONG phai ApplicationStatus: Spring Data khong tu convert String->enum cho projection
// cua native query (khuon ApplicationSummaryView). Convert o tang service.
public interface TalentPoolEntryRow {

    UUID getApplicationId();

    String getCandidateName();

    UUID getJobId();

    String getJobTitle();

    Boolean getJobDeleted();

    String getApplicationStatus();

    Instant getAppliedAt();

    Instant getAddedAt();
}

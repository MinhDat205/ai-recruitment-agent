package com.recruitment.talentpool;

import java.time.Instant;
import java.util.UUID;

// FR-H15 - interface projection cho native query P1/P3/P4 (TalentPoolRepository). entryCount dem theo R-M6 (duong doc).
public interface TalentPoolRow {

    UUID getId();

    String getName();

    Long getEntryCount();

    Instant getCreatedAt();
}

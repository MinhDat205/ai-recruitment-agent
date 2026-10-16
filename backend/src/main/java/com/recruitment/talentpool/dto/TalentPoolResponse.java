package com.recruitment.talentpool.dto;

import java.time.Instant;
import java.util.UUID;

// FR-H15 P1-P4 (REQUIREMENT muc 4.4). entryCount theo R-M6 (duong doc, loc dong y).
public record TalentPoolResponse(UUID id, String name, long entryCount, Instant createdAt) {
}

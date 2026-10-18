package com.recruitment.blindreview.dto;

import java.time.Instant;
import java.util.UUID;

// FR-H16 B1. enabled = gia tri hien tai sau thao tac; changedAt = changed_at cua dong luu vet moi nhat (null neu chua tung
// doi).
public record BlindReviewResponse(UUID jobId, boolean enabled, Instant changedAt) {
}

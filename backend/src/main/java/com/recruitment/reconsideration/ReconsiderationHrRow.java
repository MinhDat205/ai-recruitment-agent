package com.recruitment.reconsideration;

import java.time.Instant;
import java.util.UUID;

// FR-C09 - projection phia HR (khoi o E1 cua FR-H09, moi phan tu cua H1). Day la du lieu THO tu DB: ho ten va
// reason/hrResponse chua che - ReconsiderationReader che (R-A2) TRUOC khi dung DTO, khong tra row nay ra API (R-A2a).
// Alias phai khop ten getter.
public interface ReconsiderationHrRow {

    UUID getApplicationId();

    UUID getCandidateId();

    String getCandidateFullName();

    Instant getAppliedAt();

    // changed_at cua dong PENDING -> REJECTED (rejectedAt cua muc 4.4).
    Instant getRejectedAt();

    Instant getRequestedAt();

    String getReason();

    String getStatus();

    Instant getDecidedAt();

    String getHrResponse();
}

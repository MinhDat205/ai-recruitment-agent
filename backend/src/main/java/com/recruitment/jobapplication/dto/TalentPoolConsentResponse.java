package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.TalentPoolConsentState;
import java.time.Instant;

// FR-U09 - khoa long talentPoolConsent cua E1 va than response F4 (R-D1, R-K7).
public record TalentPoolConsentResponse(
        TalentPoolConsentState state, // NOT_GIVEN | ACTIVE | REVOKED (R-K5)
        Instant consentedAt, // null khi NOT_GIVEN
        Instant revokedAt) { // khac null chi khi REVOKED
}

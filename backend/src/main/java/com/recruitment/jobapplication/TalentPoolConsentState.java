package com.recruitment.jobapplication;

// FR-U09 R-K5 - trang thai dong y luu ho so cua MOT don, do backend tinh tu ba cot (frontend khong tu suy).
public enum TalentPoolConsentState {
    // talent_pool_consent = FALSE, chua tung rut (moi don cu, don khong tick luc nop).
    NOT_GIVEN,
    // talent_pool_consent = TRUE.
    ACTIVE,
    // talent_pool_consent = FALSE, talent_pool_consent_revoked_at khac NULL.
    REVOKED;

    static TalentPoolConsentState of(JobApplication application) {
        if (application.isTalentPoolConsent()) {
            return ACTIVE;
        }
        return application.getTalentPoolConsentRevokedAt() != null ? REVOKED : NOT_GIVEN;
    }
}

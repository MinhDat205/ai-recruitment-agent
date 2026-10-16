package com.recruitment.jobapplication;

import java.time.Instant;

// FR-U09 R-K5 - trang thai dong y luu ho so cua MOT don, do backend tinh tu ba cot (frontend khong tu suy).
public enum TalentPoolConsentState {
    // talent_pool_consent = FALSE, chua tung rut (moi don cu, don khong tick luc nop).
    NOT_GIVEN,
    // talent_pool_consent = TRUE.
    ACTIVE,
    // talent_pool_consent = FALSE, talent_pool_consent_revoked_at khac NULL.
    REVOKED;

    public static TalentPoolConsentState of(JobApplication application) {
        return of(application.isTalentPoolConsent(), application.getTalentPoolConsentRevokedAt());
    }

    // FR-H15 - quy tac DUY NHAT. Ban nhan entity goi lai day; P7 goi thang voi hai cot doc tu cau khoa FOR SHARE (R-C2).
    // ACTIVE chi xet co: CHECK chk_talent_pool_consent_state (V14) buoc revoked_at khac NULL => co FALSE.
    public static TalentPoolConsentState of(boolean consent, Instant revokedAt) {
        if (consent) {
            return ACTIVE;
        }
        return revokedAt != null ? REVOKED : NOT_GIVEN;
    }
}

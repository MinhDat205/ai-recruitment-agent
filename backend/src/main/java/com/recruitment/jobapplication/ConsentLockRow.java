package com.recruitment.jobapplication;

import java.time.Instant;

// FR-H15 R-C2 - hai cot dong y doc tu cau khoa JobApplicationRepository.lockConsentForShare. Suy trang thai bang
// TalentPoolConsentState.of(boolean, Instant) - khong tu viet dieu kien ACTIVE o noi goi.
public interface ConsentLockRow {

    Boolean getTalentPoolConsent();

    Instant getTalentPoolConsentRevokedAt();
}

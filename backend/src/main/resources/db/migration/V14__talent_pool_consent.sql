-- FR-U09 - Dong y luu ho so cho dot tuyen sau, tinh theo TUNG don (REQUIREMENT.md muc 3.5).
--
-- Quy uoc CHECK (L7 cua FR-H10): Postgres coi CHECK ra NULL la DAT. Cot duoc NULL co chu dich thi CHECK xu ly NULL
-- tuong minh (IS NULL / IS NOT NULL); so sanh hai cot chi dat trong ve da khang dinh ca hai IS NOT NULL.
--
-- Ba trang thai hop le (R-K5):
--   NOT_GIVEN : consent FALSE, consent_at NULL,     revoked_at NULL      (moi don cu - DEFAULT FALSE, khong backfill)
--   ACTIVE    : consent TRUE,  consent_at NOT NULL, revoked_at NULL
--   REVOKED   : consent FALSE, consent_at NOT NULL, revoked_at NOT NULL, revoked_at >= consent_at
-- Chi duong rut dong y (UPDATE co dieu kien) duoc doi ACTIVE -> REVOKED; entity map ba cot updatable = false.
ALTER TABLE job_applications
    ADD COLUMN talent_pool_consent            BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULL co chu dich: don chua tung dong y (NOT_GIVEN).
    ADD COLUMN talent_pool_consent_at         TIMESTAMPTZ,
    -- NULL co chu dich: chua rut dong y (NOT_GIVEN, ACTIVE).
    ADD COLUMN talent_pool_consent_revoked_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_talent_pool_consent_state CHECK (
        (NOT talent_pool_consent
             AND talent_pool_consent_at IS NULL
             AND talent_pool_consent_revoked_at IS NULL)
        OR (talent_pool_consent
             AND talent_pool_consent_at IS NOT NULL
             AND talent_pool_consent_revoked_at IS NULL)
        OR (NOT talent_pool_consent
             AND talent_pool_consent_at IS NOT NULL
             AND talent_pool_consent_revoked_at IS NOT NULL
             AND talent_pool_consent_revoked_at >= talent_pool_consent_at));

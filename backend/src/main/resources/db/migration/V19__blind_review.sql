-- FR-H16 - Che do an danh theo Job (REQUIREMENT.md muc 4.1). Trang thai an danh cua don KHONG luu: suy tu
-- application_status_history (R-S1).
ALTER TABLE jobs ADD COLUMN blind_review_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Luu vet bat/tat (R-B3). Chi ghi, khong sua, khong xoa.
CREATE TABLE job_blind_review_changes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    enabled     BOOLEAN NOT NULL,
    changed_by  UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp()
);
CREATE INDEX idx_job_blind_review_changes_job ON job_blind_review_changes (job_id, changed_at DESC);

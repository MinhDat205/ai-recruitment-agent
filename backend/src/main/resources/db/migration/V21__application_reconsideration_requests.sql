-- FR-C09 - Yeu cau xem xet lai don bi tu choi (REQUIREMENT.md muc 4.1). Don van dung 5 trang thai; yeu cau nam o bang
-- rieng. Quy uoc CHECK (FR-H10 L7): Postgres coi CHECK ra NULL la DAT - cot duoc NULL co chu dich phai xu ly tuong minh.
-- Khong cot job_id (Q4): suy tu job_applications.job_id (bat bien). Khong updated_at/trigger/deleted_at: moi dong chi
-- ghi mot lan sau khi tao (OPEN -> quyet), khong co duong xoa.
CREATE TABLE application_reconsideration_requests (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- RESTRICT: don khong bao gio xoa cung; script reset du lieu test phai xoa bang nay truoc (tien le V17).
    application_id UUID NOT NULL REFERENCES job_applications(id) ON DELETE RESTRICT,
    reason         TEXT,                                   -- NULL = ung vien khong nhap (R-V1)
    status         VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN', 'ACCEPTED', 'DECLINED')),
    hr_response    TEXT,                                   -- chi khi DECLINED, NULL = HR khong nhap
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at     TIMESTAMPTZ,
    decided_by     UUID REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_reconsideration_per_application UNIQUE (application_id),                     -- R-E2
    CONSTRAINT chk_reconsideration_reason_length
        CHECK (reason IS NULL OR char_length(reason) <= 1000),
    CONSTRAINT chk_reconsideration_response_length
        CHECK (hr_response IS NULL OR char_length(hr_response) <= 1000),
    -- OPEN <=> chua quyet; da quyet thi co ca thoi diem lan nguoi quyet.
    CONSTRAINT chk_reconsideration_decision
        CHECK ((status = 'OPEN') = (decided_at IS NULL) AND (decided_at IS NULL) = (decided_by IS NULL)),
    CONSTRAINT chk_reconsideration_response_declined
        CHECK (hr_response IS NULL OR status = 'DECLINED')
);

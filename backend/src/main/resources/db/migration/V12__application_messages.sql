-- FR-C06 - Nhan tin theo don ung tuyen. Khoa cua cuoc trao doi la application_id, khong co bang
-- conversations rieng (R-P1). Khong co updated_at/deleted_at: tin khong sua, khong xoa (R-P5); cot
-- duy nhat doi sau khi tao la read_at. KHONG dang ky trigger set_updated_at cho bang nay.
CREATE TABLE application_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sender_role     VARCHAR(20) NOT NULL CHECK (sender_role IN ('HR', 'CANDIDATE')),
    body            TEXT,
    attachment_key  TEXT,
    attachment_name VARCHAR(255),
    attachment_type VARCHAR(10) CHECK (attachment_type IN ('PDF', 'DOCX', 'PNG', 'JPEG', 'WEBP')),
    attachment_size BIGINT,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_app_msg_has_content CHECK (body IS NOT NULL OR attachment_key IS NOT NULL),
    CONSTRAINT chk_app_msg_body_length CHECK (body IS NULL OR char_length(body) <= 4000),
    CONSTRAINT chk_app_msg_attachment_complete CHECK (
        (attachment_key IS NULL AND attachment_name IS NULL AND attachment_type IS NULL AND attachment_size IS NULL)
        OR (attachment_key IS NOT NULL AND attachment_name IS NOT NULL AND attachment_type IS NOT NULL
            AND attachment_size IS NOT NULL))
);
CREATE INDEX idx_app_msg_thread ON application_messages(application_id, created_at, id);
CREATE INDEX idx_app_msg_unread ON application_messages(application_id, sender_role) WHERE read_at IS NULL;

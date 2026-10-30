-- FR-U16: ung vien luu tin tuyen dung de xem lai. Moi cap (ung vien, tin) dung MOT dong: bo luu = dat deleted_at
-- (soft delete, khong DELETE FROM); luu lai = xoa deleted_at + cap nhat saved_at. Tran 100 tin dang luu chot o
-- service bang khoa hang users (REQUIREMENT R-L3) - Postgres khong co rang buoc khai bao "toi da N dong".
-- FK ON DELETE CASCADE: dau trang khong co gia tri luu vet, mat theo ban ghi cha la dung nghia (khac
-- job_applications va yeu cau xem xet lai FR-C09 la du lieu quyet dinh, giu RESTRICT). Ung dung khong xoa cung
-- users/jobs (soft delete); CASCADE chi co tac dung voi script don du lieu va thao tac tay.
CREATE TABLE saved_jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id       UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    saved_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_saved_job_per_candidate UNIQUE (candidate_id, job_id)
);

-- Danh sach "Tin da luu" va dem tran: chi dong dang luu, moi luu gan nhat truoc.
CREATE INDEX idx_saved_jobs_candidate_active
    ON saved_jobs (candidate_id, saved_at DESC)
    WHERE deleted_at IS NULL;

-- FR-H15 - Kho ung vien (REQUIREMENT.md muc 4.1). Xoa mem ca hai bang (Q2): kho dat deleted_at, muc dat removed_at;
-- khong duong nao xoa cung kho hay don (FK ON DELETE RESTRICT o bang muc).
CREATE TABLE talent_pools (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL co chu dich: kho chua xoa.
    deleted_at  TIMESTAMPTZ
);
-- R-K3 - ten khong trung (khong phan biet hoa thuong) trong cac kho chua xoa cua cung cong ty. Chot chan that; service
-- chi kiem truoc de tra 409 som.
CREATE UNIQUE INDEX uq_talent_pool_name_per_company
    ON talent_pools (company_id, lower(name)) WHERE deleted_at IS NULL;
-- Ham set_updated_at() o V1__init_schema.sql:362-367; dang ky trigger cho bang moi theo mau V13:32-34.
CREATE TRIGGER trg_talent_pools_updated_at BEFORE UPDATE ON talent_pools
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Khong updated_at, khong trigger: chi removed_at doi mot lan (giong application_messages, V12).
CREATE TABLE talent_pool_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pool_id         UUID NOT NULL REFERENCES talent_pools(id) ON DELETE RESTRICT,
    application_id  UUID NOT NULL REFERENCES job_applications(id) ON DELETE RESTRICT,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL co chu dich: muc dang hieu luc.
    removed_at      TIMESTAMPTZ,
    CHECK (removed_at IS NULL OR removed_at >= added_at)
);
-- R-M2 - mot don nhieu nhat mot muc hieu luc trong mot kho. Cau them muc dung ON CONFLICT tren chinh index nay (L1).
CREATE UNIQUE INDEX uq_talent_pool_entry_active
    ON talent_pool_entries (pool_id, application_id) WHERE removed_at IS NULL;
-- R-C1 go theo don; R-M7 liet ke theo kho.
CREATE INDEX idx_talent_pool_entries_application ON talent_pool_entries (application_id) WHERE removed_at IS NULL;
CREATE INDEX idx_talent_pool_entries_pool_added  ON talent_pool_entries (pool_id, added_at DESC, id DESC)
    WHERE removed_at IS NULL;

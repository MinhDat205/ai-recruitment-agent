-- FR-H12 - Giay moi phong van nhieu khung gio (REQUIREMENT.md muc 4.1).
-- Quy uoc CHECK (FR-H10 L7): Postgres coi CHECK ra NULL la DAT - cot duoc NULL co chu dich phai xu ly NULL tuong minh.

-- 1. Khung gio cua giay moi. BAT BIEN (R-C2): khong updated_at, khong trigger.
CREATE TABLE interview_invitation_slots (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id  UUID NOT NULL REFERENCES interview_invitations(id) ON DELETE CASCADE,
    display_order  SMALLINT NOT NULL CHECK (display_order BETWEEN 1 AND 5),
    -- Thoi diem BAT DAU, khong co thoi luong (R-S2). Service cat ve dau phut (R-S3); du lieu chuyen tu V1 giu
    -- nguyen gia tri cu nen KHONG dat CHECK giay = 0 o day.
    scheduled_at   TIMESTAMPTZ NOT NULL,
    -- NULL co chu dich CHI cho du lieu cu chuyen tu V1 (location V1 tuy chon). API moi bat buoc (R-S6); do dai 500
    -- kiem o service (du lieu cu khong gioi han) - khong CHECK.
    location       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invitation_slot_time  UNIQUE (invitation_id, scheduled_at),
    CONSTRAINT uq_invitation_slot_order UNIQUE (invitation_id, display_order),
    -- Dich cua FK ghep fk_invitation_confirmed_slot ben duoi.
    CONSTRAINT uq_invitation_slot_owner UNIQUE (invitation_id, id)
);

-- 2. Cot moi cua giay moi. Ca ba NULL co chu dich: chua chot (R-K2) / dang hieu luc (R-A1).
ALTER TABLE interview_invitations
    ADD COLUMN confirmed_slot_id UUID,
    ADD COLUMN confirmed_at      TIMESTAMPTZ,
    ADD COLUMN superseded_at     TIMESTAMPTZ;

-- 3. Chuyen du lieu cu: moi giay moi -> 1 khung tu scheduled_at/location cu, danh dau da chot (confirmed_at =
--    sent_at, sent_at NULL thi created_at).
INSERT INTO interview_invitation_slots (invitation_id, display_order, scheduled_at, location, created_at)
SELECT id, 1, scheduled_at, location, created_at FROM interview_invitations;

UPDATE interview_invitations i
SET confirmed_slot_id = s.id,
    confirmed_at      = COALESCE(i.sent_at, i.created_at)
FROM interview_invitation_slots s
WHERE s.invitation_id = i.id;

-- 4. Moi don giu DUNG MOT giay moi hieu luc (moi nhat theo created_at, hoa thi theo id). API cu khong tao duoc hai
--    giay moi cho mot don nen buoc nay du kien cap nhat 0 dong; created_at chi dung o day, MOT lan, de chon ban giu.
UPDATE interview_invitations i
SET superseded_at = now()
FROM (SELECT id, row_number() OVER (PARTITION BY application_id ORDER BY created_at DESC, id DESC) AS rn
      FROM interview_invitations) r
WHERE r.id = i.id AND r.rn > 1;

-- 5. Rang buoc.
CREATE UNIQUE INDEX uq_invitation_active ON interview_invitations(application_id) WHERE superseded_at IS NULL;

-- Khung da chot phai thuoc chinh giay moi do (R-K3). MATCH SIMPLE: confirmed_slot_id NULL thi khong kiem. Mac dinh NO
-- ACTION (khong RESTRICT): xoa giay moi -> CASCADE xoa khung, FK nay chi kiem o cuoi cau lenh khi dong giay moi tham
-- chieu da bi xoa (REQUIREMENT muc 12 L1, T16).
ALTER TABLE interview_invitations
    ADD CONSTRAINT fk_invitation_confirmed_slot
        FOREIGN KEY (id, confirmed_slot_id) REFERENCES interview_invitation_slots(invitation_id, id),
    -- Hai cot cung NULL hoac cung khac NULL. Bieu thuc chi dung IS NULL -> khong bao gio ra NULL.
    ADD CONSTRAINT chk_invitation_confirmed_pair
        CHECK ((confirmed_slot_id IS NULL) = (confirmed_at IS NULL));

-- 6. Bo cot cu - khong giu song song (muc 8).
ALTER TABLE interview_invitations DROP COLUMN scheduled_at, DROP COLUMN location;

-- 7. Noi dung email rieng cua mot thong bao (R-N3, R-N3a). NULL co chu dich: moi loai thong bao cu va moi dong da co
--    -> email giu hanh vi hien co (dung body). Khac NULL -> email gui NGUYEN VAN cot nay. Khong CHECK.
ALTER TABLE notifications ADD COLUMN email_body TEXT;

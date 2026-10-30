-- ---------------------------------------------------------------------------
-- db/seed/seed-demo-structural-3.sql
--
-- Tang 3 cua seed demo (FR-U16) - du lieu cau truc KHONG phu thuoc AI, chay SAU tang 2b
-- (seed-demo-ai-output-2b.sql). Ke hoach: docs/features/UV/U16/REQUIREMENT.md muc 9 (dot 7), muc 11 L3.
-- FR-C09 them phan cua minh vao CUNG file nay.
--
-- Dai id: d3a00000-TTTT-0000-0000-NNNNNNNNNNNN (TTTT = ma bang, bang ma o docs/walkthrough/chore-seed-final.md
-- muc K2; them 0020 = saved_jobs). KHONG dung d3000000-: tang 1 da dung tien to do cho 6 rubric
-- (seed-demo-structural.sql muc 4).
--
-- IDEMPOTENT: dau file xoa moi dong dai d3a00000-% roi chen lai. Moc thoi gian TUONG DOI theo luc nap (han nop
-- CURRENT_DATE - 1, saved_at now() - ...) - nhu quy uoc tang 1.
--
-- Noi dung:
--   - 1 tin "Nhan vien Ho tro ky thuat (IT Helpdesk)" cua cong ty Hoan My, status OPEN, han nop CURRENT_DATE - 1
--     -> tinh trang EXPIRED (FR-U08/FR-U16 R-A). Khong co rubric, khong co don, khong co embedding (seed khong
--     sinh embedding - backend chay voi khoa OpenAI that va bat job-embedding thi JobEmbeddingScheduler goi
--     OpenAI MOT lan cho tin nay, vi tin van OPEN).
--   - Pham Quoc Bao luu 4 tin: tin qua han tren + 3 tin OPEN cua cong ty Ngan Ha (tang 1b). Bao chua co don o
--     tin nao trong 4 tin (don duy nhat cua Bao la tin QA chu ky 1, tin da sang chu ky 2) - trang thai
--     "Da ung tuyen" demo bang cach nop don qua UI tu trang Tin da luu (docs/DEMO.md).
--
-- Khong sua dong nao cua tang khac. Ngoai le co chu dich: xoa dong saved_jobs (tao qua UI, khong thuoc tang
-- nao) cua Bao tren dung 3 tin Ngan Ha o tren - neu con thi INSERT vo uq_saved_job_per_candidate. Tin qua han
-- khong nam trong 12 tin OPEN con han cua demo (bang viec lam cong khai van 12 tin).
--
-- Chot chan dau file (sai thi RAISE EXCEPTION, khong ghi gi - ca file la MOT transaction):
--   1. DB o V20 tro len (co bang saved_jobs).
--   2. Du HR, cong ty Hoan My, ung vien Pham Quoc Bao (tang 1) va 3 tin Ngan Ha (tang 1b).
--
-- CACH CHAY (PowerShell, tu thu muc goc repo):
--   docker compose cp db\seed\seed-demo-structural-3.sql postgres:/tmp/seed-demo-structural-3.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural-3.sql
-- ---------------------------------------------------------------------------

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

BEGIN;

-- ---------------------------------------------------------------------------
-- 0. Chot chan
-- ---------------------------------------------------------------------------

DO $guard$
DECLARE
    v_version INT;
    v_found   INT;
BEGIN
    SELECT max(version::int) INTO v_version FROM flyway_schema_history WHERE success AND version IS NOT NULL;
    IF v_version IS NULL OR v_version < 20 THEN
        RAISE EXCEPTION 'CHOT CHAN 3 THAT BAI: DB dang o V%, can V20 tro len. Khoi dong backend mot lan de Flyway chay xong.',
            v_version;
    END IF;

    SELECT (SELECT count(*) FROM users WHERE id IN ('d0000000-0000-0000-0000-000000000001',
                                                    'd0000000-0000-0000-0000-000000000004'))
         + (SELECT count(*) FROM companies WHERE id = 'd1000000-0000-0000-0000-000000000001')
         + (SELECT count(*) FROM jobs WHERE id IN ('d1b00000-0004-0000-0000-000000000002',
                                                   'd1b00000-0004-0000-0000-000000000004',
                                                   'd1b00000-0004-0000-0000-000000000005'))
      INTO v_found;
    IF v_found <> 6 THEN
        RAISE EXCEPTION 'CHOT CHAN 3 THAT BAI: chi thay %/6 dong tang 1/1b can dung. Nap reset -> 1 -> 2 -> 1b -> 2b truoc.',
            v_found;
    END IF;
END
$guard$;

-- ---------------------------------------------------------------------------
-- 1. Xoa dai d3a00000-% (con truoc cha). saved_jobs tro toi tin d3a cung tu xoa theo ON DELETE CASCADE (V20).
-- ---------------------------------------------------------------------------

DELETE FROM saved_jobs WHERE id::text LIKE 'd3a00000-%';
DELETE FROM saved_jobs
WHERE candidate_id = 'd0000000-0000-0000-0000-000000000004'
  AND job_id IN ('d1b00000-0004-0000-0000-000000000002',
                 'd1b00000-0004-0000-0000-000000000004',
                 'd1b00000-0004-0000-0000-000000000005');
DELETE FROM jobs WHERE id::text LIKE 'd3a00000-%';

-- ===========================================================================
-- PHAN A - Tin luu (FR-U16)
-- ===========================================================================

-- A1. Tin qua han cua cong ty Hoan My (HR Nguyen Thi Hanh tao). OPEN + han nop hom qua = EXPIRED
--     (JobAvailabilityResolver.classify: OPEN nhung khong nam trong findOpenJobsByIdIn).
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at, created_at, updated_at
) VALUES (
    'd3a00000-0004-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Nhân viên Hỗ trợ kỹ thuật (IT Helpdesk)',
    'Tiếp nhận và xử lý yêu cầu hỗ trợ kỹ thuật của nhân viên nội bộ, cài đặt và bảo trì máy tính, '
    || 'thiết bị văn phòng.',
    'Có kiến thức về Windows, mạng LAN cơ bản. Ưu tiên ứng viên từng làm helpdesk.',
    'IT_SOFTWARE', 'HO_CHI_MINH', 'FULL_TIME', 'ONSITE',
    9000000.00, 13000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE - 1, now() - INTERVAL '40 days', NULL,
    now() - INTERVAL '40 days', now() - INTERVAL '40 days'
);

-- A2. Pham Quoc Bao luu 4 tin. saved_at tuong doi, cach nhau ro rang de thu tu saved_at DESC co dinh
--     (tren cung: UI/UX, cuoi cung: tin qua han). deleted_at NULL = dang luu.
INSERT INTO saved_jobs (id, candidate_id, job_id, saved_at, deleted_at) VALUES
    ('d3a00000-0020-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000004',
     'd3a00000-0004-0000-0000-000000000001', now() - INTERVAL '3 days', NULL),
    ('d3a00000-0020-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000004',
     'd1b00000-0004-0000-0000-000000000004', now() - INTERVAL '2 days', NULL),
    ('d3a00000-0020-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004',
     'd1b00000-0004-0000-0000-000000000005', now() - INTERVAL '1 day', NULL),
    ('d3a00000-0020-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000004',
     'd1b00000-0004-0000-0000-000000000002', now() - INTERVAL '2 hours', NULL);

COMMIT;

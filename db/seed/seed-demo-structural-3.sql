-- ---------------------------------------------------------------------------
-- db/seed/seed-demo-structural-3.sql
--
-- Tang 3 cua seed demo (FR-U16, FR-C09) - du lieu cau truc KHONG phu thuoc AI, chay SAU tang 2b
-- (seed-demo-ai-output-2b.sql). Ke hoach: docs/features/UV/U16/REQUIREMENT.md muc 9 (dot 7), muc 11 L3;
-- docs/features/CHUNG/C09/REQUIREMENT.md muc 4.5 va muc 11 (S1, L9).
--
-- Dai id: d3a00000-TTTT-0000-0000-NNNNNNNNNNNN (TTTT = ma bang, bang ma o docs/walkthrough/chore-seed-final.md
-- muc K2; them 0020 = saved_jobs, 0021 = application_reconsideration_requests). KHONG dung d3000000-: tang 1 da
-- dung tien to do cho 6 rubric (seed-demo-structural.sql muc 4).
--
-- IDEMPOTENT: dau file xoa moi dong dai d3a00000-% (ke ca dong phat sinh qua UI tren don dai d3a) roi chen lai. Moc
-- thoi gian TUONG DOI theo luc nap (han nop CURRENT_DATE - 1, saved_at/applied_at/changed_at now() - ...) - nhu quy
-- uoc tang 1: nap lai truoc buoi demo thi yeu cau xem xet lai van con han 7 ngay.
--
-- Noi dung Phan A (FR-U16):
--   - 1 tin "Nhan vien Ho tro ky thuat (IT Helpdesk)" cua cong ty Hoan My, status OPEN, han nop CURRENT_DATE - 1
--     -> tinh trang EXPIRED (FR-U08/FR-U16 R-A). Khong co rubric, khong co don, khong co embedding (seed khong
--     sinh embedding - backend chay voi khoa OpenAI that va bat job-embedding thi JobEmbeddingScheduler goi
--     OpenAI MOT lan cho tin nay, vi tin van OPEN).
--   - Pham Quoc Bao luu 4 tin: tin qua han tren + 3 tin OPEN cua cong ty Ngan Ha (tang 1b). Bao chua co don o
--     tin nao trong 4 tin (don duy nhat cua Bao la tin QA chu ky 1, tin da sang chu ky 2) - trang thai
--     "Da ung tuyen" demo bang cach nop don qua UI tu trang Tin da luu (docs/DEMO.md).
--
-- Noi dung Phan B (FR-C09, hai kich ban, HR hr@demo.local; KHONG dung Pham Quoc Bao):
--   B1 - ung vien TU GUI: Bui Ngoc Mai -> "Ke toan tong hop" (d2..04), nop now() - 3 days, bi tu choi o vong ho so
--        now() - 1 day (han ~ now() + 6 days luc nap), CHUA co yeu cau -> khoi "Xem xet lai" AVAILABLE.
--   B2 - HR XU LY: Nguyen Hai Son -> "Chuyen vien Marketing" (d2..05), nop now() - 5 days, bi tu choi now() - 2 days,
--        yeu cau OPEN gui now() - 1 day kem ly do hai dong -> tab "Yeu cau xem xet lai (1)" cua tin Marketing.
--   Hai tin khong bat an danh, khong co cau hoi sang loc (screening_captured_at = applied_at, NO_QUESTIONS). CV = CV
--   chinh cua ung vien (tang 2, UUID ngau nhien - tra bang SELECT). Thong bao khop 1-1 su kien that
--   (NotificationEventListener): nop don -> HR, bi tu choi -> ung vien, gui yeu cau (B2) -> HR; noi dung theo
--   NotificationContentBuilder, email_status SKIPPED. Phieu cong ty 1 sau tang 3: tong +2, REJECTED 1 -> 3.
--
-- Khong sua dong nao cua tang khac. Ngoai le co chu dich: xoa dong saved_jobs (tao qua UI, khong thuoc tang
-- nao) cua Bao tren dung 3 tin Ngan Ha o tren - neu con thi INSERT vo uq_saved_job_per_candidate. Tin qua han
-- khong nam trong 12 tin OPEN con han cua demo (bang viec lam cong khai van 12 tin).
--
-- Chot chan dau file (sai thi RAISE EXCEPTION, khong ghi gi - ca file la MOT transaction):
--   1. DB o V21 tro len (co bang saved_jobs va application_reconsideration_requests).
--   2. Du HR, cong ty Hoan My, ung vien Pham Quoc Bao (tang 1) va 3 tin Ngan Ha (tang 1b).
--   3. (Phan B) Mai, Son co CV chinh; tin Ke toan, Marketing con OPEN, chua xoa, khong an danh; Mai/Son chua co don
--      NGOAI dai d3a o chu ky hien tai cua hai tin (vd don tao qua UI khi soat tay) - neu co thi INSERT vo
--      uq_application_per_cycle.
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
    IF v_version IS NULL OR v_version < 21 THEN
        RAISE EXCEPTION 'CHOT CHAN 3 THAT BAI: DB dang o V%, can V21 tro len. Khoi dong backend mot lan de Flyway chay xong.',
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

    -- Phan B: CV chinh cua Mai (d0..09) va Son (d0..05) - tang 2.
    SELECT count(DISTINCT candidate_id) INTO v_found FROM resumes
    WHERE is_primary AND candidate_id IN ('d0000000-0000-0000-0000-000000000009', 'd0000000-0000-0000-0000-000000000005');
    IF v_found <> 2 THEN
        RAISE EXCEPTION 'CHOT CHAN 3 THAT BAI: chi thay CV chinh cua %/2 ung vien (Mai, Son). Nap tang 2 truoc.', v_found;
    END IF;

    -- Phan B: hai tin con nhan don duoc va khong an danh.
    SELECT count(*) INTO v_found FROM jobs
    WHERE id IN ('d2000000-0000-0000-0000-000000000004', 'd2000000-0000-0000-0000-000000000005')
      AND status = 'OPEN' AND deleted_at IS NULL AND NOT blind_review_enabled;
    IF v_found <> 2 THEN
        RAISE EXCEPTION 'CHOT CHAN 3 THAT BAI: chi %/2 tin (Ke toan, Marketing) con OPEN, chua xoa, khong an danh.', v_found;
    END IF;

    -- Phan B: khong co don ngoai dai d3a cua Mai/Son o chu ky hien tai cua hai tin.
    SELECT count(*) INTO v_found FROM job_applications a JOIN jobs j ON j.id = a.job_id
    WHERE a.id::text NOT LIKE 'd3a00000-%'
      AND a.recruitment_cycle = j.recruitment_cycle
      AND ((a.candidate_id = 'd0000000-0000-0000-0000-000000000009' AND a.job_id = 'd2000000-0000-0000-0000-000000000004')
        OR (a.candidate_id = 'd0000000-0000-0000-0000-000000000005' AND a.job_id = 'd2000000-0000-0000-0000-000000000005'));
    IF v_found <> 0 THEN
        RAISE EXCEPTION 'CHOT CHAN 3 THAT BAI: Mai/Son da co % don (ngoai tang 3) o hai tin Phan B. Nap lai tu dau (K5).',
            v_found;
    END IF;
END
$guard$;

-- ---------------------------------------------------------------------------
-- 1. Xoa dai d3a00000-% (con truoc cha). saved_jobs tro toi tin d3a cung tu xoa theo ON DELETE CASCADE (V20).
--    Phan B: yeu cau / thong bao / lich su / giay moi cua DON dai d3a xoa theo application_id/entity_id (gom ca dong
--    phat sinh qua UI khi soat tay - id ngau nhien). application_reconsideration_requests.application_id la ON DELETE
--    RESTRICT (V21) nen phai xoa TRUOC don; giay moi (va khung) tu xoa theo CASCADE cua don, xoa tuong minh cho ro.
-- ---------------------------------------------------------------------------

DELETE FROM application_reconsideration_requests
WHERE application_id::text LIKE 'd3a00000-%' OR id::text LIKE 'd3a00000-%';
DELETE FROM notifications
WHERE (entity_type = 'APPLICATION' AND entity_id::text LIKE 'd3a00000-%') OR id::text LIKE 'd3a00000-%';
DELETE FROM interview_invitations WHERE application_id::text LIKE 'd3a00000-%';
DELETE FROM application_status_history
WHERE application_id::text LIKE 'd3a00000-%' OR id::text LIKE 'd3a00000-%';
DELETE FROM job_applications WHERE id::text LIKE 'd3a00000-%';
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

-- ===========================================================================
-- PHAN B - Yeu cau xem xet lai don bi tu choi (FR-C09)
-- ===========================================================================

-- B-1. Hai don, CV chinh cua ung vien (tang 2, tra bang SELECT). Chu ky = chu ky hien tai cua tin. Tin khong co cau
--      hoi sang loc -> screening_captured_at = applied_at (NO_QUESTIONS, nhu ApplicationService.apply). Khong dong y
--      luu ho so. updated_at = luc bi tu choi (lan ghi cuoi len hang don).
INSERT INTO job_applications (
    id, job_id, candidate_id, resume_id, recruitment_cycle, status,
    ai_consent, ai_consent_at, cover_letter, applied_at, updated_at, screening_captured_at,
    talent_pool_consent, talent_pool_consent_at, talent_pool_consent_revoked_at
)
SELECT v.id::uuid, j.id, v.candidate_id::uuid,
       (SELECT r.id FROM resumes r WHERE r.candidate_id = v.candidate_id::uuid AND r.is_primary
        ORDER BY r.uploaded_at DESC, r.id DESC LIMIT 1),
       j.recruitment_cycle, 'REJECTED', TRUE, now() - v.applied_ago, v.cover_letter,
       now() - v.applied_ago, now() - v.rejected_ago, now() - v.applied_ago, FALSE, NULL, NULL
FROM (VALUES
    -- B1 Mai -> Ke toan tong hop
    ('d3a00000-0009-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000009',
     INTERVAL '3 days', INTERVAL '1 day', NULL),
    -- B2 Son -> Chuyen vien Marketing
    ('d3a00000-0009-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000005',
     INTERVAL '5 days', INTERVAL '2 days',
     'Tôi muốn chuyển sang mảng marketing số và đã tự chạy chiến dịch quảng cáo cho dự án cá nhân.')
) AS v(id, job_id, candidate_id, applied_ago, rejected_ago, cover_letter)
JOIN jobs j ON j.id = v.job_id::uuid;

-- B-2. Lich su: NULL -> PENDING do chinh ung vien, PENDING -> REJECTED do HR (d0..01). Moc PENDING -> REJECTED quyet
--      dinh han 7 ngay (changed_at + 168 gio, REQUIREMENT R-T1, B2).
INSERT INTO application_status_history (id, application_id, from_status, to_status, changed_by, note, changed_at)
SELECT v.id::uuid, v.app_id::uuid, v.from_status, v.to_status, v.changed_by::uuid, NULL, now() - v.ago
FROM (VALUES
    ('d3a00000-0010-0000-0000-000000000001', 'd3a00000-0009-0000-0000-000000000001', NULL, 'PENDING',
     'd0000000-0000-0000-0000-000000000009', INTERVAL '3 days'),
    ('d3a00000-0010-0000-0000-000000000002', 'd3a00000-0009-0000-0000-000000000001', 'PENDING', 'REJECTED',
     'd0000000-0000-0000-0000-000000000001', INTERVAL '1 day'),
    ('d3a00000-0010-0000-0000-000000000003', 'd3a00000-0009-0000-0000-000000000002', NULL, 'PENDING',
     'd0000000-0000-0000-0000-000000000005', INTERVAL '5 days'),
    ('d3a00000-0010-0000-0000-000000000004', 'd3a00000-0009-0000-0000-000000000002', 'PENDING', 'REJECTED',
     'd0000000-0000-0000-0000-000000000001', INTERVAL '2 days')
) AS v(id, app_id, from_status, to_status, changed_by, ago);

-- B-3. Yeu cau OPEN cua Son (B2). Ly do hai dong, nguyen van (R-V1). B1 KHONG co yeu cau - Mai tu gui khi demo.
INSERT INTO application_reconsideration_requests (id, application_id, reason, status, hr_response, requested_at)
VALUES (
    'd3a00000-0021-0000-0000-000000000001', 'd3a00000-0009-0000-0000-000000000002',
    'Tôi đã quản lý fanpage 12.000 người theo dõi cho dự án cá nhân, tự chạy quảng cáo Facebook với ngân sách 5 triệu/tháng.'
        || E'\n' || 'Kinh nghiệm này có ghi ở mục Dự án trong CV, mong anh chị xem lại giúp.',
    'OPEN', NULL, now() - INTERVAL '1 day'
);

-- B-4. Thong bao khop 1-1 su kien (NotificationEventListener + NotificationContentBuilder). Nhan ung vien gui HR = "Ung
--      vien " + ho ten (hai tin khong an danh). HR chua doc thong bao yeu cau; cac thong bao khac da doc.
INSERT INTO notifications (id, user_id, type, title, body, link, email_body, entity_type, entity_id, is_read, read_at, email_status, created_at)
SELECT v.id::uuid, v.user_id::uuid, v.type, v.title,
       replace(replace(v.body, '{title}', j.title), '{name}', u.full_name),
       v.link, NULL, 'APPLICATION', a.id, v.read_ago IS NOT NULL, now() - v.read_ago, 'SKIPPED', now() - v.created_ago
FROM (VALUES
    ('d3a00000-0015-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'd3a00000-0009-0000-0000-000000000001',
     'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới', 'Ứng viên {name} vừa ứng tuyển vị trí "{title}"',
     '/hr/applications/d3a00000-0009-0000-0000-000000000001', INTERVAL '2 days 23 hours', INTERVAL '3 days'),
    ('d3a00000-0015-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000009', 'd3a00000-0009-0000-0000-000000000001',
     'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "{title}" của bạn đã chuyển sang trạng thái: Bị từ chối',
     '/candidate/applications/d3a00000-0009-0000-0000-000000000001', INTERVAL '20 hours', INTERVAL '1 day'),
    ('d3a00000-0015-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000001', 'd3a00000-0009-0000-0000-000000000002',
     'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới', 'Ứng viên {name} vừa ứng tuyển vị trí "{title}"',
     '/hr/applications/d3a00000-0009-0000-0000-000000000002', INTERVAL '4 days 23 hours', INTERVAL '5 days'),
    ('d3a00000-0015-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000005', 'd3a00000-0009-0000-0000-000000000002',
     'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "{title}" của bạn đã chuyển sang trạng thái: Bị từ chối',
     '/candidate/applications/d3a00000-0009-0000-0000-000000000002', INTERVAL '1 day 20 hours', INTERVAL '2 days'),
    ('d3a00000-0015-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000001', 'd3a00000-0009-0000-0000-000000000002',
     'RECONSIDERATION_REQUESTED', 'Ứng viên yêu cầu xem xét lại',
     'Ứng viên {name} đã gửi yêu cầu xem xét lại đơn ứng tuyển vị trí "{title}".',
     '/hr/applications/d3a00000-0009-0000-0000-000000000002', NULL, INTERVAL '1 day')
) AS v(id, user_id, app_id, type, title, body, link, read_ago, created_ago)
JOIN job_applications a ON a.id = v.app_id::uuid
JOIN jobs j ON j.id = a.job_id
JOIN users u ON u.id = a.candidate_id;

COMMIT;

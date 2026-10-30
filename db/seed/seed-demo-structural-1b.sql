-- ---------------------------------------------------------------------------
-- db/seed/seed-demo-structural-1b.sql
--
-- Tang 1b cua seed demo (chore/seed-final) - du lieu cau truc KHONG phu thuoc AI, chay SAU tang 1
-- (seed-demo-structural.sql) va tang 2 (seed-demo-ai-output.sql), TRUOC tang 2b. Ke hoach:
-- docs/walkthrough/chore-seed-final.md (K1 B1-B3/P1-P5, K2 dai id, K3 cac dong "Demo · 1b").
--
-- IDEMPOTENT: dau file xoa moi dong dai d1b00000-% (theo thu tu khoa ngoai) roi chen lai; cac UPDATE len dong
-- tang 1/2 ghi gia tri CO DINH nen chay lai cho cung ket qua. Chi moc tuong doi (khung phong van con han, han
-- nop, published_at cong ty 2) doi theo luc nap - nhu quy uoc tang 1 va seed-test.
--
-- Chot chan dau file (sai thi RAISE EXCEPTION, khong ghi gi - ca file la MOT transaction):
--   1. DB o V19 tro len (flyway_schema_history). Truoc FR-U16 la "dung V19" - V20 (saved_jobs) lam chuoi nap tu
--      DB trong dung o day; 1b khong dung bang nao moi tu V20, nen chi can >= V19.
--   2. Du 9 don demo cua tang 2 (K3: APP-*).
--   3. Chua co du lieu tang 2b tro toi dai d1b (luot cham cua don d1b, embedding tin d1b, diem theo tieu chi
--      d1b): xoa dai d1b se CASCADE xoa mat chung. Da nap 2b thi phai nap lai tu dau (K5), khong chay lai 1b.
--
-- Moi dong la trang thai app TAO DUOC qua API (thu tu va noi dung theo service that - xem comment tung phan).
-- Moc thoi gian su kien: TUYET DOI 09-10/2026 (sau tang 2 ngay 28/08/2026, K2).
-- Thong bao viet tay: email_status = 'SKIPPED' (NotificationMailScheduler bo qua).
--
-- updated_at: trigger set_updated_at (V1) ghi de moi gia tri bang now() khi UPDATE - tat TAM 4 trigger
-- trg_<bang>_updated_at trong transaction nay (bat lai truoc COMMIT) de:
--   - job_applications (dong tang 2): updated_at = moc tuyet doi cua lan doi cuoi (giong app);
--   - jobs/companies/rubric_criteria (dong tang 1): GIU NGUYEN updated_at = luc nap tang 1 (da sau moi moc
--     tuyet doi o day) thay vi now() moi lan chay.
-- Chi tat trigger updated_at; trigger rang buoc trg_rubric_weight_sum (V1) KHONG dong toi.
--
-- MAT KHAU HR cong ty 2: "Demo1234" (cung hash voi tang 1).
--
-- CACH CHAY (PowerShell, tu thu muc goc repo) - K5:
--   docker compose cp db\seed\seed-demo-structural-1b.sql postgres:/tmp/seed-demo-structural-1b.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural-1b.sql
--   .\db\seed\install-demo-files.ps1
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
    v_tier2   INT;
    v_tier2b  INT;
BEGIN
    SELECT max(version::int) INTO v_version FROM flyway_schema_history WHERE success AND version IS NOT NULL;
    IF v_version IS NULL OR v_version < 19 THEN
        RAISE EXCEPTION 'CHOT CHAN 1b THAT BAI: DB dang o V% , can V19 tro len. Khoi dong backend mot lan de Flyway chay xong (K5).',
            v_version;
    END IF;

    SELECT count(*) INTO v_tier2 FROM job_applications WHERE id IN (
        '3ec35ae2-c8ae-4100-a819-3b59f6948707', '967d5c84-1519-4a70-8bed-6252781ac052',
        'a611e169-fa6c-4b6f-a1fd-da36503ae0ac', '1029b885-ddae-4417-8e0c-8284d6198f79',
        '31447cc8-8702-442e-8fbc-9dd320afef02', 'bb83d9cd-cb26-4ef3-9970-1f9b347deba4',
        'cd3857e1-71b4-4f46-bd81-b04c897e2cd0', '28f563b1-603b-4191-b5bd-23bc65a9af5b',
        'dd1d46b2-fb7e-49f9-9976-594c7cd8237d');
    IF v_tier2 <> 9 THEN
        RAISE EXCEPTION 'CHOT CHAN 1b THAT BAI: chi thay %/9 don demo tang 2. Nap seed-demo-structural.sql roi seed-demo-ai-output.sql truoc.',
            v_tier2;
    END IF;

    SELECT (SELECT count(*) FROM scoring_runs WHERE application_id::text LIKE 'd1b00000-%')
         + (SELECT count(*) FROM job_embeddings WHERE job_id::text LIKE 'd1b00000-%')
         + (SELECT count(*) FROM criterion_scores WHERE criterion_id::text LIKE 'd1b00000-%')
      INTO v_tier2b;
    IF v_tier2b > 0 THEN
        RAISE EXCEPTION 'CHOT CHAN 1b THAT BAI: da co % dong tang 2b tro toi dai d1b00000 - chay lai 1b se xoa mat chung. Nap lai tu dau: reset -> 1 -> 2 -> 1b -> 2b (K5).',
            v_tier2b;
    END IF;
END
$guard$;

-- Tat tam trigger updated_at (xem dau file). Bat lai o cuoi file, truoc COMMIT.
ALTER TABLE job_applications DISABLE TRIGGER trg_job_applications_updated_at;
ALTER TABLE jobs             DISABLE TRIGGER trg_jobs_updated_at;
ALTER TABLE companies        DISABLE TRIGGER trg_companies_updated_at;
ALTER TABLE rubric_criteria  DISABLE TRIGGER trg_rubric_criteria_updated_at;

-- ---------------------------------------------------------------------------
-- 1. Xoa dai d1b00000-% (con truoc cha). interview_invitation_slots xoa theo ON DELETE CASCADE cua
--    interview_invitations (xoa khung truoc se vo fk_invitation_confirmed_slot).
-- ---------------------------------------------------------------------------

DELETE FROM talent_pool_entries           WHERE id::text LIKE 'd1b00000-%';
DELETE FROM talent_pools                  WHERE id::text LIKE 'd1b00000-%';
DELETE FROM notifications                 WHERE id::text LIKE 'd1b00000-%';
DELETE FROM application_messages          WHERE id::text LIKE 'd1b00000-%';
DELETE FROM interview_invitations         WHERE id::text LIKE 'd1b00000-%';
DELETE FROM application_screening_answers WHERE id::text LIKE 'd1b00000-%';
DELETE FROM application_status_history    WHERE id::text LIKE 'd1b00000-%';
DELETE FROM job_blind_review_changes      WHERE id::text LIKE 'd1b00000-%';
-- FR-C09 (V21) - phong ho: tang 1b khong tao yeu cau xem xet lai, nhung application_id ON DELETE RESTRICT se chan cau
-- xoa don ngay duoi neu sau nay co yeu cau gan vao don dai d1b.
DELETE FROM application_reconsideration_requests
WHERE application_id::text LIKE 'd1b00000-%' OR id::text LIKE 'd1b00000-%';
DELETE FROM job_applications              WHERE id::text LIKE 'd1b00000-%';
DELETE FROM job_screening_questions       WHERE id::text LIKE 'd1b00000-%';
DELETE FROM rubric_criteria               WHERE id::text LIKE 'd1b00000-%';
DELETE FROM rubrics                       WHERE id::text LIKE 'd1b00000-%';
DELETE FROM interview_templates           WHERE id::text LIKE 'd1b00000-%';
DELETE FROM jobs                          WHERE id::text LIKE 'd1b00000-%';
DELETE FROM companies                     WHERE id::text LIKE 'd1b00000-%';
DELETE FROM users                         WHERE id::text LIKE 'd1b00000-%';

-- ===========================================================================
-- PHAN A - Cong ty 2 (C02/U07/U13: demo du 12 tin OPEN) + logo hai cong ty (H01, B2, P1, L3)
-- ===========================================================================

-- A1. HR cong ty 2 (mat khau Demo1234). Moc tuong doi cung cong ty 2 - tin cua no dung published_at tuong doi.
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified, created_at, updated_at) VALUES
    ('d1b00000-0001-0000-0000-000000000001', 'hr.nganha@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'HR', 'Phạm Thu Trang', '0902000001', TRUE, TRUE, now() - INTERVAL '60 days', now() - INTERVAL '60 days');

-- A2. Cong ty 2. logo_url = URL cong khai cua LocalStorageService ('/uploads/logos/<id cong ty>.<duoi>',
--     CompanyOwnerService.uploadLogo). File nguon: db/seed/demo-files/logos/ (install-demo-files.ps1 chep).
INSERT INTO companies (
    id, owner_id, name, logo_url, description, company_size, industry,
    contact_email, contact_phone, address, created_at, updated_at
) VALUES (
    'd1b00000-0002-0000-0000-000000000001', 'd1b00000-0001-0000-0000-000000000001',
    'Công ty TNHH Dịch vụ Số Ngân Hà',
    '/uploads/logos/d1b00000-0002-0000-0000-000000000001.png',
    'Doanh nghiệp hư cấu dùng cho dữ liệu demo: phát triển sản phẩm số, chăm sóc khách hàng và vận hành kho vận '
    || 'cho các đối tác bán lẻ.',
    '100-199', 'Công nghệ & Dịch vụ',
    'tuyendung@nganha-demo.local', '02363999888',
    'Tầng 8, 25 Trần Phú, Quận Hải Châu, Đà Nẵng',
    now() - INTERVAL '60 days', now() - INTERVAL '60 days'
);

-- A3. Logo cong ty 1 (tang 1). updated_at giu nguyen (trigger dang tat).
UPDATE companies
SET logo_url = '/uploads/logos/d1000000-0000-0000-0000-000000000001.png'
WHERE id = 'd1000000-0000-0000-0000-000000000001';

-- A4. 6 tin OPEN cua cong ty 2. Du ma danh muc C05 (R-J3) va rubric du 100% (requireRubricComplete) - dieu
--     kien cua JobOwnerService.changeStatus -> OPEN. published_at rai khac tang 1 (1/4/9/15/27/48 ngay truoc
--     luc nap) de "Moi nhat" xen ke hai cong ty. Tin -03 KHONG cong bo luong (salary_min/max NULL).
--     Tin -05 lam tu xa (REMOTE) van khai location_code.
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at, created_at, updated_at
)
SELECT v.id::uuid, 'd1b00000-0002-0000-0000-000000000001', 'd1b00000-0001-0000-0000-000000000001',
       v.title, v.description, v.requirements, v.category_code, v.location_code, 'FULL_TIME', v.work_mode,
       v.salary_min, v.salary_max, 'VND',
       'OPEN', 1, CURRENT_DATE + 45, now() - v.age, NULL, now() - v.age - INTERVAL '1 hour', now() - v.age
FROM (VALUES
    ('d1b00000-0004-0000-0000-000000000001', 'Lập trình viên Frontend (React)',
     'Phát triển giao diện web cho các sản phẩm số của công ty, phối hợp với đội thiết kế và backend để hoàn thiện '
     || 'tính năng theo từng đợt phát hành.',
     'Tối thiểu 2 năm kinh nghiệm React và TypeScript. Hiểu về REST API, có kinh nghiệm viết kiểm thử giao diện là lợi thế.',
     'IT_SOFTWARE', 'HA_NOI', 'HYBRID', 20000000.00, 35000000.00, INTERVAL '1 day'),
    ('d1b00000-0004-0000-0000-000000000002', 'Chuyên viên Thiết kế UI/UX',
     'Nghiên cứu người dùng, thiết kế luồng và giao diện cho ứng dụng web, di động; xây dựng và duy trì bộ thành phần '
     || 'giao diện dùng chung.',
     'Thành thạo Figma, có portfolio sản phẩm số. Ưu tiên ứng viên từng làm việc với hệ thống thiết kế.',
     'DESIGN_CREATIVE', 'HA_NOI', 'ONSITE', 15000000.00, 25000000.00, INTERVAL '4 days'),
    ('d1b00000-0004-0000-0000-000000000003', 'Chuyên viên Nhân sự tổng hợp',
     'Phụ trách tuyển dụng, quản lý hồ sơ nhân sự, chấm công và các chương trình gắn kết nội bộ cho văn phòng Đà Nẵng.',
     'Tối thiểu 2 năm kinh nghiệm hành chính nhân sự. Nắm vững quy định về lao động, bảo hiểm xã hội.',
     'HUMAN_RESOURCES', 'DA_NANG', 'ONSITE', NULL, NULL, INTERVAL '9 days'),
    ('d1b00000-0004-0000-0000-000000000004', 'Nhân viên Chăm sóc khách hàng',
     'Tiếp nhận yêu cầu của khách hàng qua tổng đài, email và trò chuyện trực tuyến; theo dõi xử lý đến khi hoàn tất.',
     'Giao tiếp tốt, sử dụng thành thạo máy tính. Có kinh nghiệm tổng đài hoặc CRM là lợi thế.',
     'CUSTOMER_SERVICE', 'DA_NANG', 'ONSITE', 9000000.00, 13000000.00, INTERVAL '15 days'),
    ('d1b00000-0004-0000-0000-000000000005', 'Chuyên viên Phân tích dữ liệu',
     'Xây dựng báo cáo và bảng điều khiển phục vụ đội kinh doanh, phân tích dữ liệu bán hàng để đề xuất cải tiến.',
     'Thành thạo SQL và một công cụ trực quan hoá dữ liệu. Biết Python là lợi thế.',
     'IT_SOFTWARE', 'HO_CHI_MINH', 'REMOTE', 25000000.00, 40000000.00, INTERVAL '27 days'),
    ('d1b00000-0004-0000-0000-000000000006', 'Nhân viên Điều phối kho vận',
     'Điều phối nhập xuất hàng tại kho, lập kế hoạch giao nhận và phối hợp với đơn vị vận chuyển.',
     'Có kinh nghiệm kho vận hoặc chuỗi cung ứng. Sử dụng tốt Excel, chịu được áp lực thời gian.',
     'LOGISTICS_IMPORT_EXPORT', 'HAI_PHONG', 'ONSITE', 10000000.00, 15000000.00, INTERVAL '48 days')
) AS v(id, title, description, requirements, category_code, location_code, work_mode, salary_min, salary_max, age);

-- A5. Rubric (1/tin, tao cung tin - JobOwnerService.create) + 3 tieu chi, tong trong so = 100.
INSERT INTO rubrics (id, job_id, name, is_locked, created_at, updated_at)
SELECT ('d1b00000-0005-0000-0000-00000000000' || n)::uuid, j.id, 'Rubric ' || j.title, FALSE, j.created_at, j.created_at
FROM generate_series(1, 6) AS n
JOIN jobs j ON j.id = ('d1b00000-0004-0000-0000-00000000000' || n)::uuid;

INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order, created_at, updated_at)
SELECT ('d1b00000-0006-0000-0000-0000000000' || v.job_no || v.ord)::uuid,
       ('d1b00000-0005-0000-0000-00000000000' || v.job_no)::uuid,
       v.name, v.description, v.weight, 5, v.ord, r.created_at, r.created_at
FROM (VALUES
    (1, 1, 'Kinh nghiệm phát triển Frontend', 'Số năm làm React/TypeScript, độ phức tạp của giao diện đã xây dựng.', 40.00),
    (1, 2, 'Kỹ năng kiểm thử & chất lượng mã', 'Kinh nghiệm viết kiểm thử giao diện, review mã, tối ưu hiệu năng.', 35.00),
    (1, 3, 'Kỹ năng phối hợp nhóm', 'Khả năng làm việc với thiết kế, backend và sản phẩm.', 25.00),
    (2, 1, 'Kinh nghiệm thiết kế sản phẩm số', 'Số dự án web/di động đã thiết kế, vai trò trong dự án.', 40.00),
    (2, 2, 'Kỹ năng nghiên cứu người dùng', 'Phỏng vấn người dùng, kiểm thử khả dụng, rút ra yêu cầu thiết kế.', 35.00),
    (2, 3, 'Thành thạo công cụ thiết kế', 'Mức độ thành thạo Figma và hệ thống thiết kế.', 25.00),
    (3, 1, 'Kinh nghiệm hành chính nhân sự', 'Số năm làm nhân sự tổng hợp, quy mô doanh nghiệp đã làm việc.', 40.00),
    (3, 2, 'Am hiểu pháp luật lao động', 'Nắm vững quy định hợp đồng lao động, bảo hiểm xã hội.', 35.00),
    (3, 3, 'Kỹ năng tuyển dụng', 'Kinh nghiệm đăng tin, sàng lọc hồ sơ và phỏng vấn.', 25.00),
    (4, 1, 'Kinh nghiệm chăm sóc khách hàng', 'Số năm làm tổng đài/chăm sóc khách hàng.', 40.00),
    (4, 2, 'Kỹ năng xử lý yêu cầu', 'Khả năng tiếp nhận, phân loại và theo dõi yêu cầu đến khi hoàn tất.', 35.00),
    (4, 3, 'Kỹ năng giao tiếp', 'Diễn đạt rõ ràng, thái độ phục vụ khách hàng.', 25.00),
    (5, 1, 'Kinh nghiệm phân tích dữ liệu', 'Số năm làm phân tích, loại báo cáo đã xây dựng.', 40.00),
    (5, 2, 'Kỹ năng SQL & trực quan hoá', 'Mức độ thành thạo SQL và công cụ trực quan hoá dữ liệu.', 35.00),
    (5, 3, 'Kỹ năng trình bày kết quả', 'Khả năng trình bày phát hiện cho đội kinh doanh.', 25.00),
    (6, 1, 'Kinh nghiệm kho vận', 'Số năm làm kho vận/chuỗi cung ứng.', 40.00),
    (6, 2, 'Kỹ năng lập kế hoạch giao nhận', 'Khả năng lập lịch nhập xuất, phối hợp đơn vị vận chuyển.', 35.00),
    (6, 3, 'Kỹ năng sử dụng Excel', 'Mức độ thành thạo Excel trong theo dõi tồn kho.', 25.00)
) AS v(job_no, ord, name, description, weight)
JOIN rubrics r ON r.id = ('d1b00000-0005-0000-0000-00000000000' || v.job_no)::uuid;

-- A6. Mau giay moi (1/tin, tao cung tin): du 3 placeholder that cua InterviewInvitationService o tieu de va than
--     thu (L4); thu KHONG chua ngay gio/dia diem (FR-H12 R-C1) - cung cau chu voi tang 1.
INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address, created_at, updated_at)
SELECT ('d1b00000-0007-0000-0000-00000000000' || n)::uuid, j.id,
       'Công ty TNHH Dịch vụ Số Ngân Hà',
       'Thư mời phỏng vấn vị trí {{jobTitle}} - {{companyName}}',
       'Kính gửi {{candidateName}},' || E'\n\n'
       || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí {{jobTitle}} tại '
       || '{{companyName}}. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
       || 'theo một trong các khung giờ phỏng vấn được gửi kèm.' || E'\n\n'
       || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
       || 'Trân trọng,',
       'Phạm Thu Trang', 'Trưởng phòng Nhân sự',
       'Tầng 8, 25 Trần Phú, Quận Hải Châu, Đà Nẵng',
       j.created_at, j.created_at
FROM generate_series(1, 6) AS n
JOIN jobs j ON j.id = ('d1b00000-0004-0000-0000-00000000000' || n)::uuid;

-- ===========================================================================
-- PHAN B - Pipeline cong ty 1 (B1, H07/H08) + giay moi (H12, U10)
--   Doi trang thai di DUNG duong cua app:
--   - PENDING -> INTERVIEW_INVITED CHI qua gui giay moi (InterviewInvitationService.sendInvitation; PATCH trang thai
--     chan dich INTERVIEW_INVITED - ApplicationStatusController). Gui = 1 dong lich su (HR) + thong bao
--     APPLICATION_STATUS_CHANGED cho ung vien; KHONG thong bao gui lai (R-F2). Giay moi 1 khung: chot khung do,
--     confirmed_at = sent_at; nhieu khung: confirmed_* NULL.
--   - INTERVIEW_INVITED -> HIRED, PENDING -> REJECTED: PATCH (ApplicationStatusService) = 1 dong lich su + 1 thong bao.
--   - Ung vien chon khung (confirmSlot): khong doi trang thai, khong ghi lich su; UPDATE khoa hang don lam
--     updated_at = luc chon (lockIfInterviewInvited + trigger); thong bao INTERVIEW_SLOT_CONFIRMED cho HR.
--   KHONG dong toi scoring_runs / criterion_scores / score_explanations.
-- ===========================================================================

-- B1. Trang thai cuoi cua 4 don co diem. updated_at = moc lan ghi cuoi cung len hang don.
UPDATE job_applications a
SET status = v.status, updated_at = v.updated_at
FROM (VALUES
    ('cd3857e1-71b4-4f46-bd81-b04c897e2cd0'::uuid, 'HIRED',             '2026-09-08 02:00:00+00'::timestamptz), -- APP-KT-HA
    ('a611e169-fa6c-4b6f-a1fd-da36503ae0ac'::uuid, 'REJECTED',          '2026-09-03 02:00:00+00'::timestamptz), -- APP-QA-BAO
    ('dd1d46b2-fb7e-49f9-9976-594c7cd8237d'::uuid, 'INTERVIEW_INVITED', '2026-10-06 01:30:00+00'::timestamptz), -- APP-SALES-TUNG (chon khung)
    ('28f563b1-603b-4191-b5bd-23bc65a9af5b'::uuid, 'INTERVIEW_INVITED', '2026-10-10 02:00:00+00'::timestamptz)  -- APP-MKT-LINH
) AS v(id, status, updated_at)
WHERE a.id = v.id;

-- B2. Lich su: moi lan doi trang thai cua HR (changed_by = HR cong ty 1).
INSERT INTO application_status_history (id, application_id, from_status, to_status, changed_by, note, changed_at) VALUES
    ('d1b00000-0010-0000-0000-000000000001', 'cd3857e1-71b4-4f46-bd81-b04c897e2cd0', 'PENDING', 'INTERVIEW_INVITED',
     'd0000000-0000-0000-0000-000000000001', NULL, '2026-09-01 03:00:00+00'),
    ('d1b00000-0010-0000-0000-000000000002', 'cd3857e1-71b4-4f46-bd81-b04c897e2cd0', 'INTERVIEW_INVITED', 'HIRED',
     'd0000000-0000-0000-0000-000000000001', NULL, '2026-09-08 02:00:00+00'),
    ('d1b00000-0010-0000-0000-000000000003', 'a611e169-fa6c-4b6f-a1fd-da36503ae0ac', 'PENDING', 'REJECTED',
     'd0000000-0000-0000-0000-000000000001', NULL, '2026-09-03 02:00:00+00'),
    ('d1b00000-0010-0000-0000-000000000004', 'dd1d46b2-fb7e-49f9-9976-594c7cd8237d', 'PENDING', 'INTERVIEW_INVITED',
     'd0000000-0000-0000-0000-000000000001', NULL, '2026-10-05 03:00:00+00'),
    ('d1b00000-0010-0000-0000-000000000005', '28f563b1-603b-4191-b5bd-23bc65a9af5b', 'PENDING', 'INTERVIEW_INVITED',
     'd0000000-0000-0000-0000-000000000001', NULL, '2026-10-10 02:00:00+00');

-- B3. Giay moi (FR-H12). subject/rendered_content = mau cua Job render bang ten ung vien + ten Job + company_name
--     cua mau (previewInvitation), HR gui nguyen van. Ghi theo ba buoc nhu createInvitation (muc 12 L2):
--     giay moi chua chot -> khung -> UPDATE chot.
--     -01 APP-KT-HA     1 khung DA QUA (truoc khi HIRED), chot luc gui.
--     -02 APP-MKT-LINH  3 khung tuong lai, CHUA chon (U10 "truoc").
--     -03 APP-SALES-TUNG 2 khung tuong lai, ung vien da chon khung 2 (U10 "sau").
--     (-04 cua don Vo Thanh Tung -> Java o phan E.)
INSERT INTO interview_invitations (id, application_id, subject, rendered_content, sent_at, sent_by, superseded_at, created_at)
SELECT v.id::uuid, a.id,
       replace(replace(replace(t.subject, '{{candidateName}}', u.full_name), '{{jobTitle}}', j.title), '{{companyName}}', t.company_name),
       replace(replace(replace(t.body,    '{{candidateName}}', u.full_name), '{{jobTitle}}', j.title), '{{companyName}}', t.company_name),
       v.sent_at, 'd0000000-0000-0000-0000-000000000001', NULL, v.sent_at
FROM (VALUES
    ('d1b00000-0012-0000-0000-000000000001', 'cd3857e1-71b4-4f46-bd81-b04c897e2cd0'::uuid, '2026-09-01 03:00:00+00'::timestamptz),
    ('d1b00000-0012-0000-0000-000000000002', '28f563b1-603b-4191-b5bd-23bc65a9af5b'::uuid, '2026-10-10 02:00:00+00'::timestamptz),
    ('d1b00000-0012-0000-0000-000000000003', 'dd1d46b2-fb7e-49f9-9976-594c7cd8237d'::uuid, '2026-10-05 03:00:00+00'::timestamptz)
) AS v(id, application_id, sent_at)
JOIN job_applications a ON a.id = v.application_id
JOIN users u ON u.id = a.candidate_id
JOIN jobs j ON j.id = a.job_id
JOIN interview_templates t ON t.job_id = j.id;

-- Khung: cat dau phut (R-S3), thoi diem sau sent_at (R-S4), dia diem mot dong (R-S6). Khung tuong lai tinh tuong
-- doi theo luc nap (luon con han); khung cua don HIRED la moc tuyet doi da qua.
INSERT INTO interview_invitation_slots (id, invitation_id, display_order, scheduled_at, location, created_at) VALUES
    ('d1b00000-0013-0000-0000-000000000001', 'd1b00000-0012-0000-0000-000000000001', 1,
     '2026-09-04 02:00:00+00', 'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh',
     '2026-09-01 03:00:00+00'),
    ('d1b00000-0013-0000-0000-000000000002', 'd1b00000-0012-0000-0000-000000000002', 1,
     date_trunc('day', now()) + INTERVAL '6 days 2 hours', 'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh',
     '2026-10-10 02:00:00+00'),
    ('d1b00000-0013-0000-0000-000000000003', 'd1b00000-0012-0000-0000-000000000002', 2,
     date_trunc('day', now()) + INTERVAL '7 days 7 hours', 'Phỏng vấn trực tuyến qua Google Meet (đường dẫn gửi trước 1 ngày)',
     '2026-10-10 02:00:00+00'),
    ('d1b00000-0013-0000-0000-000000000004', 'd1b00000-0012-0000-0000-000000000002', 3,
     date_trunc('day', now()) + INTERVAL '9 days 3 hours', 'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh',
     '2026-10-10 02:00:00+00'),
    ('d1b00000-0013-0000-0000-000000000005', 'd1b00000-0012-0000-0000-000000000003', 1,
     date_trunc('day', now()) + INTERVAL '5 days 2 hours', 'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh',
     '2026-10-05 03:00:00+00'),
    ('d1b00000-0013-0000-0000-000000000006', 'd1b00000-0012-0000-0000-000000000003', 2,
     date_trunc('day', now()) + INTERVAL '8 days 3 hours', 'Phỏng vấn trực tuyến qua Google Meet (đường dẫn gửi trước 1 ngày)',
     '2026-10-05 03:00:00+00');

-- Chot khung: -01 (1 khung) chot luc gui (createInvitation); -03 ung vien chon khung 2 luc 2026-10-06 01:30
-- (confirmSlot: confirmed_at > sent_at, khung con o tuong lai luc chon).
UPDATE interview_invitations i
SET confirmed_slot_id = v.slot_id, confirmed_at = v.confirmed_at
FROM (VALUES
    ('d1b00000-0012-0000-0000-000000000001'::uuid, 'd1b00000-0013-0000-0000-000000000001'::uuid, '2026-09-01 03:00:00+00'::timestamptz),
    ('d1b00000-0012-0000-0000-000000000003'::uuid, 'd1b00000-0013-0000-0000-000000000006'::uuid, '2026-10-06 01:30:00+00'::timestamptz)
) AS v(invitation_id, slot_id, confirmed_at)
WHERE i.id = v.invitation_id;

-- ===========================================================================
-- PHAN C - Job DevOps: cau hoi sang loc (H10) + sua rubric (P5) + don Tran Minh Hoang -> DevOps (H14, U09)
--   Cau chu va gia tri dung H14 REQUIREMENT muc 7.4 B1-B3.
-- ===========================================================================

-- C1. Hai cau hoi them sau khi dang tin (ScreeningQuestionOwnerService.add, Job OPEN da co don - H14 a32). Moi lan
--     them tang screening_revision 1 -> 2 (R-Q10, R-Q11).
INSERT INTO job_screening_questions (
    id, job_id, question_type, question_text, options, is_required, display_order, deleted_at, created_at, updated_at
) VALUES
    ('d1b00000-0008-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000003', 'YES_NO',
     'Bạn có thể bắt đầu làm việc trong vòng 2 tuần không?', NULL, TRUE, 1, NULL,
     '2026-09-30 02:00:00+00', '2026-09-30 02:00:00+00'),
    ('d1b00000-0008-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000003', 'SHORT_TEXT',
     'Bạn đã vận hành cụm Kubernetes ở quy mô bao nhiêu node?', NULL, FALSE, 2, NULL,
     '2026-09-30 02:05:00+00', '2026-09-30 02:05:00+00');

UPDATE jobs SET screening_revision = 2 WHERE id = 'd2000000-0000-0000-0000-000000000003';

-- C2. P5 - sua rubric DevOps dang mo khoa (H14 B2), dung thu tu de tong trong so khong vuot 100 sau moi thao tac:
--     sua tai cho tieu chi d4..34 (giu criterionId -> ca R-C5) 15 -> 10, roi them tieu chi moi trong so 5.
UPDATE rubric_criteria
SET name = 'Kỹ năng giao tiếp & phối hợp xử lý sự cố', weight = 10.00
WHERE id = 'd4000000-0000-0000-0000-000000000034';

INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order, created_at, updated_at) VALUES
    ('d1b00000-0006-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000003',
     'Viết tài liệu vận hành', 'Khả năng viết runbook, tài liệu vận hành và báo cáo sau sự cố.',
     5.00, 5, 5, '2026-09-30 02:10:00+00', '2026-09-30 02:10:00+00');

-- C3. Don Tran Minh Hoang -> DevOps (ApplicationService.apply): CV chinh da trich xuat 0b716edc (H14 a36), MOT moc
--     cho ai_consent_at / screening_captured_at (R-S2); khong dong y luu ho so (NOT_GIVEN).
INSERT INTO job_applications (
    id, job_id, candidate_id, resume_id, recruitment_cycle, status,
    ai_consent, ai_consent_at, cover_letter, applied_at, updated_at, screening_captured_at,
    talent_pool_consent, talent_pool_consent_at, talent_pool_consent_revoked_at
) VALUES (
    'd1b00000-0009-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002',
    '0b716edc-083b-434c-8b3d-8047454c7668', 1, 'PENDING', TRUE, '2026-10-02 03:00:00+00', NULL,
    '2026-10-02 03:00:00+00', '2026-10-02 03:00:00+00', '2026-10-02 03:00:00+00', FALSE, NULL, NULL
);

INSERT INTO application_status_history (id, application_id, from_status, to_status, changed_by, note, changed_at) VALUES
    ('d1b00000-0010-0000-0000-000000000006', 'd1b00000-0009-0000-0000-000000000001', NULL, 'PENDING',
     'd0000000-0000-0000-0000-000000000002', NULL, '2026-10-02 03:00:00+00');

-- Ban chup (bat bien, ScreeningCaptureService.persist): chep tu cau hoi luc nop, created_at = luc nop -> CAPTURED.
INSERT INTO application_screening_answers (
    id, application_id, question_id, display_order, question_type_snapshot, question_text_snapshot,
    options_snapshot, required_snapshot, answer_text, answer_yes_no, created_at
)
SELECT v.id::uuid, 'd1b00000-0009-0000-0000-000000000001', q.id, q.display_order, q.question_type, q.question_text,
       q.options, q.is_required, v.answer_text, v.answer_yes_no, '2026-10-02 03:00:00+00'
FROM (VALUES
    ('d1b00000-0011-0000-0000-000000000001', 'd1b00000-0008-0000-0000-000000000001'::uuid, NULL,               TRUE),
    ('d1b00000-0011-0000-0000-000000000002', 'd1b00000-0008-0000-0000-000000000002'::uuid, 'Khoảng 20 node.', NULL::boolean)
) AS v(id, question_id, answer_text, answer_yes_no)
JOIN job_screening_questions q ON q.id = v.question_id;

-- ===========================================================================
-- PHAN D - Dong y luu ho so (U09) + kho ung vien (H15)
--   ACTIVE: consent_at = luc nop (= ai_consent_at, K8). REVOKED: ung vien rut luc 2026-09-15 02:00
--   (revokeTalentPoolConsentIfActive: updated_at doi theo trigger; go don khoi moi kho cung luc - R-C1).
--   Them vao kho chi khi dong y ACTIVE va don khong an danh (Job 3, Job 4 khong bat an danh).
-- ===========================================================================

UPDATE job_applications a
SET talent_pool_consent = v.consent,
    talent_pool_consent_at = a.ai_consent_at,
    talent_pool_consent_revoked_at = v.revoked_at,
    updated_at = COALESCE(v.updated_at, a.updated_at)
FROM (VALUES
    ('cd3857e1-71b4-4f46-bd81-b04c897e2cd0'::uuid, TRUE,  NULL::timestamptz, NULL::timestamptz),                                  -- APP-KT-HA ACTIVE
    ('31447cc8-8702-442e-8fbc-9dd320afef02'::uuid, TRUE,  NULL::timestamptz, NULL::timestamptz),                                  -- APP-DEVOPS-SON ACTIVE
    ('bb83d9cd-cb26-4ef3-9970-1f9b347deba4'::uuid, FALSE, '2026-09-15 02:00:00+00'::timestamptz, '2026-09-15 02:00:00+00'::timestamptz) -- APP-DEVOPS-DUC REVOKED
) AS v(id, consent, revoked_at, updated_at)
WHERE a.id = v.id;

-- Kho: -01, -02 co muc; -03 rong; -04 da xoa (TalentPoolOwnerService.delete chi dat deleted_at, updated_at = luc xoa).
INSERT INTO talent_pools (id, company_id, name, created_at, updated_at, deleted_at) VALUES
    ('d1b00000-0016-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001', 'Kế toán tiềm năng',
     '2026-09-10 01:00:00+00', '2026-09-10 01:00:00+00', NULL),
    ('d1b00000-0016-0000-0000-000000000002', 'd1000000-0000-0000-0000-000000000001', 'Kỹ thuật hạ tầng',
     '2026-09-12 01:00:00+00', '2026-09-12 01:00:00+00', NULL),
    ('d1b00000-0016-0000-0000-000000000003', 'd1000000-0000-0000-0000-000000000001', 'Marketing 2027',
     '2026-09-14 01:00:00+00', '2026-09-14 01:00:00+00', NULL),
    ('d1b00000-0016-0000-0000-000000000004', 'd1000000-0000-0000-0000-000000000001', 'Tuyển gấp quý 3',
     '2026-09-05 01:00:00+00', '2026-09-14 03:00:00+00', '2026-09-14 03:00:00+00');

-- Muc: Ha (HIRED) vao -01; Son va Duc vao -02 khi ca hai con ACTIVE; muc cua Duc go luc rut dong y (removed_at =
-- revoked_at).
INSERT INTO talent_pool_entries (id, pool_id, application_id, added_at, removed_at) VALUES
    ('d1b00000-0017-0000-0000-000000000001', 'd1b00000-0016-0000-0000-000000000001', 'cd3857e1-71b4-4f46-bd81-b04c897e2cd0',
     '2026-09-10 01:05:00+00', NULL),
    ('d1b00000-0017-0000-0000-000000000002', 'd1b00000-0016-0000-0000-000000000002', '31447cc8-8702-442e-8fbc-9dd320afef02',
     '2026-09-12 01:05:00+00', NULL),
    ('d1b00000-0017-0000-0000-000000000003', 'd1b00000-0016-0000-0000-000000000002', 'bb83d9cd-cb26-4ef3-9970-1f9b347deba4',
     '2026-09-12 01:06:00+00', '2026-09-15 02:00:00+00');

-- ===========================================================================
-- PHAN E - Che do an danh Job Java (H16) + don Vo Thanh Tung -> Java
--   Don an danh = Job bat cong tac VA don chua tung co lich su INTERVIEW_INVITED (BlindReviewResolver, R-S1) -
--   APP-JAVA-HOANG, APP-JAVA-HA hien nhan "Ung vien #...". Don cua Tung nop khi dang an danh (thong bao nop don
--   cho HR dung nhan), roi HR gui giay moi -> co lich su INTERVIEW_INVITED -> het an danh; {{candidateName}} trong
--   giay moi duoc thay bang ho ten that luc gui (R-I2) nen rendered_content giong don khong an danh.
-- ===========================================================================

UPDATE jobs SET blind_review_enabled = TRUE WHERE id = 'd2000000-0000-0000-0000-000000000001';

-- Luu vet bat -> tat -> bat (BlindReviewService.update: moi lan doi gia tri = 1 dong).
INSERT INTO job_blind_review_changes (id, job_id, enabled, changed_by, changed_at) VALUES
    ('d1b00000-0018-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001', TRUE,
     'd0000000-0000-0000-0000-000000000001', '2026-09-20 02:00:00+00'),
    ('d1b00000-0018-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000001', FALSE,
     'd0000000-0000-0000-0000-000000000001', '2026-09-25 02:00:00+00'),
    ('d1b00000-0018-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000001', TRUE,
     'd0000000-0000-0000-0000-000000000001', '2026-10-01 02:00:00+00');

-- Job Java khong co cau hoi sang loc -> screening_captured_at = luc nop, 0 dong ban chup (NO_QUESTIONS).
INSERT INTO job_applications (
    id, job_id, candidate_id, resume_id, recruitment_cycle, status,
    ai_consent, ai_consent_at, cover_letter, applied_at, updated_at, screening_captured_at,
    talent_pool_consent, talent_pool_consent_at, talent_pool_consent_revoked_at
) VALUES (
    'd1b00000-0009-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000008',
    '1654697f-0aa7-40ec-94d1-07b9ca6a05dc', 1, 'INTERVIEW_INVITED', TRUE, '2026-10-03 03:00:00+00',
    'Tôi đang chuyển hướng sang lập trình backend và mong được trao đổi thêm về vị trí này.',
    '2026-10-03 03:00:00+00', '2026-10-06 03:00:00+00', '2026-10-03 03:00:00+00', FALSE, NULL, NULL
);

INSERT INTO application_status_history (id, application_id, from_status, to_status, changed_by, note, changed_at) VALUES
    ('d1b00000-0010-0000-0000-000000000007', 'd1b00000-0009-0000-0000-000000000002', NULL, 'PENDING',
     'd0000000-0000-0000-0000-000000000008', NULL, '2026-10-03 03:00:00+00'),
    ('d1b00000-0010-0000-0000-000000000008', 'd1b00000-0009-0000-0000-000000000002', 'PENDING', 'INTERVIEW_INVITED',
     'd0000000-0000-0000-0000-000000000001', NULL, '2026-10-06 03:00:00+00');

-- Giay moi -04: 1 khung tuong lai, chot luc gui.
INSERT INTO interview_invitations (id, application_id, subject, rendered_content, sent_at, sent_by, superseded_at, created_at)
SELECT 'd1b00000-0012-0000-0000-000000000004', a.id,
       replace(replace(replace(t.subject, '{{candidateName}}', u.full_name), '{{jobTitle}}', j.title), '{{companyName}}', t.company_name),
       replace(replace(replace(t.body,    '{{candidateName}}', u.full_name), '{{jobTitle}}', j.title), '{{companyName}}', t.company_name),
       '2026-10-06 03:00:00+00', 'd0000000-0000-0000-0000-000000000001', NULL, '2026-10-06 03:00:00+00'
FROM job_applications a
JOIN users u ON u.id = a.candidate_id
JOIN jobs j ON j.id = a.job_id
JOIN interview_templates t ON t.job_id = j.id
WHERE a.id = 'd1b00000-0009-0000-0000-000000000002';

INSERT INTO interview_invitation_slots (id, invitation_id, display_order, scheduled_at, location, created_at) VALUES
    ('d1b00000-0013-0000-0000-000000000007', 'd1b00000-0012-0000-0000-000000000004', 1,
     date_trunc('day', now()) + INTERVAL '10 days 2 hours', 'Phỏng vấn trực tuyến qua Google Meet (đường dẫn gửi trước 1 ngày)',
     '2026-10-06 03:00:00+00');

UPDATE interview_invitations
SET confirmed_slot_id = 'd1b00000-0013-0000-0000-000000000007', confirmed_at = sent_at
WHERE id = 'd1b00000-0012-0000-0000-000000000004';

-- ===========================================================================
-- PHAN F - Tin nhan (C06) + thong bao (C03)
-- ===========================================================================

-- F1. Tin nhan. Khoa tep = 'message-attachments/<id tin>.<duoi chuan>' (app dung uuid ngau nhien - K2); file nguon
--     db/seed/demo-files/message-attachments/. attachment_size = so byte that cua file nguon (FileSignatures: PDF
--     "%PDF", PNG 8 byte chu ky). read_at = luc BEN NHAN mo cuoc trao doi (NULL = chua doc).
--     -01 HR -> Tung (Sales): kem PDF; Tung da doc luc 09:00.
--     -02 Tung -> HR (Sales): kem PNG; HR CHUA doc.
--     -03 HR -> Linh (MKT): chi chu; Linh CHUA doc.
INSERT INTO application_messages (
    id, application_id, sender_id, sender_role, body,
    attachment_key, attachment_name, attachment_type, attachment_size, read_at, created_at
) VALUES
    ('d1b00000-0014-0000-0000-000000000001', 'dd1d46b2-fb7e-49f9-9976-594c7cd8237d', 'd0000000-0000-0000-0000-000000000001', 'HR',
     'Chào anh Tùng, phòng Tuyển dụng gửi anh bản mô tả công việc chi tiết của vị trí Nhân viên kinh doanh qua điện thoại '
     || 'để anh tham khảo trước buổi phỏng vấn.',
     'message-attachments/d1b00000-0014-0000-0000-000000000001.pdf', 'Mô tả công việc chi tiết.pdf', 'PDF', 19770,
     '2026-10-07 09:00:00+00', '2026-10-07 02:30:00+00'),
    ('d1b00000-0014-0000-0000-000000000002', 'dd1d46b2-fb7e-49f9-9976-594c7cd8237d', 'd0000000-0000-0000-0000-000000000008', 'CANDIDATE',
     'Dạ em cảm ơn anh/chị.' || E'\n' || 'Em gửi kèm ảnh chứng nhận khoá học kỹ năng bán hàng qua điện thoại em đã hoàn thành ạ.',
     'message-attachments/d1b00000-0014-0000-0000-000000000002.png', 'chung-nhan-khoa-hoc.png', 'PNG', 29044,
     NULL, '2026-10-07 09:10:00+00'),
    ('d1b00000-0014-0000-0000-000000000003', '28f563b1-603b-4191-b5bd-23bc65a9af5b', 'd0000000-0000-0000-0000-000000000001', 'HR',
     'Chào Linh, giấy mời phỏng vấn có 3 khung giờ. Bạn vui lòng chọn một khung phù hợp trong mục Giấy mời phỏng vấn '
     || 'của đơn ứng tuyển nhé.',
     NULL, NULL, NULL, NULL, NULL, '2026-10-12 03:00:00+00');

-- F2. Thong bao - khop 1-1 su kien that (NotificationEventListener), noi dung theo NotificationContentBuilder:
--     link moi (/hr/applications/{id}, /candidate/applications/{id}, kem ?tab=...); thong bao NEW_MESSAGE dung doan
--     trich MessageExcerpt (gop khoang trang, cat 120 ky tu + "…"), tinh tu chinh tin o F1; nhan ung vien trong thong
--     bao cho HR tinh TAI LUC su kien (an danh -> CandidateAlias "Ung vien #" + 8 hex cuoi cua id don, R-O1).
--     R-N4: moi ben nhan chi co MOT NEW_MESSAGE chua doc moi don. Doc cuoc trao doi danh dau da doc ca thong bao
--     NEW_MESSAGE cua nguoi doc (R-R3) -> Tung doc luc 09:00.
--     Khong co thong bao cho: gui giay moi lan dau (R-F2), rut dong y (R-K8), kho ung vien, bat/tat an danh.
INSERT INTO notifications (id, user_id, type, title, body, link, email_body, entity_type, entity_id, is_read, read_at, email_status, created_at)
SELECT v.id::uuid, v.user_id::uuid, v.type, v.title, v.body, v.link, NULL, 'APPLICATION', v.app_id::uuid,
       v.read_at IS NOT NULL, v.read_at, 'SKIPPED', v.created_at
FROM (VALUES
    -- APP-KT-HA: moi phong van, roi trung tuyen (ung vien Ha)
    ('d1b00000-0015-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000006', 'APPLICATION_STATUS_CHANGED',
     'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Kế toán tổng hợp" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn',
     '/candidate/applications/cd3857e1-71b4-4f46-bd81-b04c897e2cd0', 'cd3857e1-71b4-4f46-bd81-b04c897e2cd0',
     '2026-09-01 07:00:00+00'::timestamptz, '2026-09-01 03:00:01+00'::timestamptz),
    ('d1b00000-0015-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000006', 'APPLICATION_STATUS_CHANGED',
     'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Kế toán tổng hợp" của bạn đã chuyển sang trạng thái: Trúng tuyển',
     '/candidate/applications/cd3857e1-71b4-4f46-bd81-b04c897e2cd0', 'cd3857e1-71b4-4f46-bd81-b04c897e2cd0',
     NULL, '2026-09-08 02:00:01+00'),
    -- APP-QA-BAO: tu choi (ung vien Bao)
    ('d1b00000-0015-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', 'APPLICATION_STATUS_CHANGED',
     'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Kỹ sư Kiểm thử phần mềm (QA Engineer)" của bạn đã chuyển sang trạng thái: Bị từ chối',
     '/candidate/applications/a611e169-fa6c-4b6f-a1fd-da36503ae0ac', 'a611e169-fa6c-4b6f-a1fd-da36503ae0ac',
     '2026-09-03 08:00:00+00', '2026-09-03 02:00:01+00'),
    -- APP-SALES-TUNG: moi phong van (ung vien Tung)
    ('d1b00000-0015-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000008', 'APPLICATION_STATUS_CHANGED',
     'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Nhân viên kinh doanh qua điện thoại" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn',
     '/candidate/applications/dd1d46b2-fb7e-49f9-9976-594c7cd8237d', 'dd1d46b2-fb7e-49f9-9976-594c7cd8237d',
     '2026-10-05 12:00:00+00', '2026-10-05 03:00:01+00'),
    -- Don Hoang -> DevOps: nop don (HR), Job khong an danh
    ('d1b00000-0015-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED',
     'Có đơn ứng tuyển mới',
     'Ứng viên Trần Minh Hoàng vừa ứng tuyển vị trí "Kỹ sư DevOps"',
     '/hr/applications/d1b00000-0009-0000-0000-000000000001', 'd1b00000-0009-0000-0000-000000000001',
     NULL, '2026-10-02 03:00:01+00'),
    -- Don Tung -> Java: nop khi Job dang an danh -> nhan CandidateAlias (HR da doc)
    ('d1b00000-0015-0000-0000-000000000007', 'd0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED',
     'Có đơn ứng tuyển mới',
     'Ứng viên #' || upper(right(replace('d1b00000-0009-0000-0000-000000000002', '-', ''), 8))
     || ' vừa ứng tuyển vị trí "Senior Java Backend Developer"',
     '/hr/applications/d1b00000-0009-0000-0000-000000000002', 'd1b00000-0009-0000-0000-000000000002',
     '2026-10-03 04:00:00+00', '2026-10-03 03:00:01+00'),
    -- Don Tung -> Java: moi phong van (ung vien Tung, chua doc)
    ('d1b00000-0015-0000-0000-000000000008', 'd0000000-0000-0000-0000-000000000008', 'APPLICATION_STATUS_CHANGED',
     'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Senior Java Backend Developer" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn',
     '/candidate/applications/d1b00000-0009-0000-0000-000000000002', 'd1b00000-0009-0000-0000-000000000002',
     NULL, '2026-10-06 03:00:01+00'),
    -- APP-MKT-LINH: moi phong van (ung vien Linh)
    ('d1b00000-0015-0000-0000-000000000011', 'd0000000-0000-0000-0000-000000000007', 'APPLICATION_STATUS_CHANGED',
     'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Chuyên viên Marketing" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn',
     '/candidate/applications/28f563b1-603b-4191-b5bd-23bc65a9af5b', '28f563b1-603b-4191-b5bd-23bc65a9af5b',
     '2026-10-10 05:00:00+00', '2026-10-10 02:00:01+00')
) AS v(id, user_id, type, title, body, link, app_id, read_at, created_at);

-- INTERVIEW_SLOT_CONFIRMED (forSlotConfirmed): body mot dong co gio Viet Nam, email_body = body + dia diem + lien
-- ket (URL mac dinh app.frontend-base-url, SKIPPED nen khong gui). Gio/dia diem doc tu chinh khung da chot.
INSERT INTO notifications (id, user_id, type, title, body, link, email_body, entity_type, entity_id, is_read, read_at, email_status, created_at)
SELECT 'd1b00000-0015-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000001', 'INTERVIEW_SLOT_CONFIRMED',
       'Ứng viên đã chọn lịch phỏng vấn', x.body,
       '/hr/applications/dd1d46b2-fb7e-49f9-9976-594c7cd8237d?tab=invitation',
       x.body || E'\n\n' || 'Địa điểm: ' || x.location || E'\n\n'
       || 'Xem giấy mời tại: http://localhost:5173/hr/applications/dd1d46b2-fb7e-49f9-9976-594c7cd8237d?tab=invitation',
       'APPLICATION', 'dd1d46b2-fb7e-49f9-9976-594c7cd8237d', TRUE, '2026-10-06 02:00:00+00', 'SKIPPED', '2026-10-06 01:30:01+00'
FROM (
    SELECT 'Ứng viên Võ Thanh Tùng đã chọn khung giờ phỏng vấn '
           || to_char(s.scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI DD/MM/YYYY')
           || ' (giờ Việt Nam) cho vị trí "Nhân viên kinh doanh qua điện thoại".' AS body,
           s.location
    FROM interview_invitation_slots s
    WHERE s.id = 'd1b00000-0013-0000-0000-000000000006'
) AS x;

-- NEW_MESSAGE (forNewMessage): ung vien nhan -> nhan = ten cong ty; HR nhan -> "Ung vien <ho ten>" (Job Sales khong an
-- danh). Doan trich tinh tu tin o F1.
INSERT INTO notifications (id, user_id, type, title, body, link, email_body, entity_type, entity_id, is_read, read_at, email_status, created_at)
SELECT v.id::uuid, v.user_id::uuid, 'NEW_MESSAGE', v.title,
       v.sender_label || ' đã gửi tin nhắn về đơn ứng tuyển vị trí "' || j.title || '": '
       || CASE WHEN char_length(e.c) <= 120 THEN e.c ELSE left(e.c, 120) || '…' END,
       v.link, NULL, 'APPLICATION', m.application_id, v.read_at IS NOT NULL, v.read_at, 'SKIPPED', v.created_at
FROM (VALUES
    ('d1b00000-0015-0000-0000-000000000009', 'd0000000-0000-0000-0000-000000000008', 'd1b00000-0014-0000-0000-000000000001'::uuid,
     'Tin nhắn mới từ nhà tuyển dụng', 'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     '/candidate/applications/dd1d46b2-fb7e-49f9-9976-594c7cd8237d?tab=messages',
     '2026-10-07 09:00:00+00'::timestamptz, '2026-10-07 02:30:01+00'::timestamptz),
    ('d1b00000-0015-0000-0000-000000000010', 'd0000000-0000-0000-0000-000000000001', 'd1b00000-0014-0000-0000-000000000002'::uuid,
     'Tin nhắn mới từ ứng viên', 'Ứng viên Võ Thanh Tùng',
     '/hr/applications/dd1d46b2-fb7e-49f9-9976-594c7cd8237d?tab=messages',
     NULL, '2026-10-07 09:10:01+00'),
    ('d1b00000-0015-0000-0000-000000000012', 'd0000000-0000-0000-0000-000000000007', 'd1b00000-0014-0000-0000-000000000003'::uuid,
     'Tin nhắn mới từ nhà tuyển dụng', 'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     '/candidate/applications/28f563b1-603b-4191-b5bd-23bc65a9af5b?tab=messages',
     NULL, '2026-10-12 03:00:01+00')
) AS v(id, user_id, message_id, title, sender_label, link, read_at, created_at)
JOIN application_messages m ON m.id = v.message_id
JOIN job_applications a ON a.id = m.application_id
JOIN jobs j ON j.id = a.job_id
CROSS JOIN LATERAL (SELECT btrim(regexp_replace(m.body, '\s+', ' ', 'g')) AS c) AS e;

-- F3. HR cong ty 1 da doc phan cu cua thong bao tang 2 (18/22, tao truoc 11:45 ngay 28/08) - con 4 chua doc tang 2.
UPDATE notifications
SET is_read = TRUE, read_at = '2026-08-28 13:00:00+00'
WHERE user_id = 'd0000000-0000-0000-0000-000000000001'
  AND id::text NOT LIKE 'd1b00000-%'
  AND created_at < '2026-08-28 11:45:00+00';

-- ===========================================================================
-- PHAN G - Tin QA mo lai sang chu ky 2 + cau hoi sang loc (demo 1 HR + 1 ung vien, P1)
--   Demo chi dang nhap Pham Quoc Bao. Bao da co don dot 1 vao tin QA (APP-QA-BAO, REJECTED o B1) nen
--   uq_application_per_cycle chan nop lai o chu ky 1. Mo phong HR dong roi mo lai tin (JobOwnerService.changeStatus:
--   CLOSED -> OPEN chi tang recruitment_cycle; status cuoi van OPEN, published_at da co nen giu nguyen) -> Bao nop lai
--   duoc o chu ky 2. Don dot 1 (cua Bao, Duc) giu nguyen chu ky 1.
--   Sau khi mo lai, HR them 2 cau hoi (ScreeningQuestionOwnerService.add: moi lan them tang screening_revision 1,
--   display_order = max + 1) -> revision 0 -> 2. Embedding tin khong doi (chi doi khi tieu de/mo ta/nganh doi) -
--   khong phat sinh luot OpenAI. updated_at cua tin giu nguyen (trigger dang tat, xem dau file).
-- ===========================================================================

UPDATE jobs SET recruitment_cycle = 2, screening_revision = 2 WHERE id = 'd2000000-0000-0000-0000-000000000002';

INSERT INTO job_screening_questions (
    id, job_id, question_type, question_text, options, is_required, display_order, deleted_at, created_at, updated_at
) VALUES
    ('d1b00000-0008-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000002', 'YES_NO',
     'Bạn có thể làm việc tại văn phòng ở TP. Hồ Chí Minh không?', NULL, TRUE, 1, NULL,
     '2026-10-14 02:00:00+00', '2026-10-14 02:00:00+00'),
    ('d1b00000-0008-0000-0000-000000000004', 'd2000000-0000-0000-0000-000000000002', 'SHORT_TEXT',
     'Bạn đã dùng công cụ kiểm thử tự động nào trong dự án thực tế?', NULL, FALSE, 2, NULL,
     '2026-10-14 02:05:00+00', '2026-10-14 02:05:00+00');

-- Bat lai trigger updated_at. trg_rubric_weight_sum la constraint trigger DEFERRED: con su kien cho thi ALTER TABLE
-- rubric_criteria bi tu choi ("pending trigger events") - chay kiem tong trong so NGAY tai day truoc.
SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE job_applications ENABLE TRIGGER trg_job_applications_updated_at;
ALTER TABLE jobs             ENABLE TRIGGER trg_jobs_updated_at;
ALTER TABLE companies        ENABLE TRIGGER trg_companies_updated_at;
ALTER TABLE rubric_criteria  ENABLE TRIGGER trg_rubric_criteria_updated_at;

COMMIT;

-- ---------------------------------------------------------------------------
-- Kiem tra sau khi nap - so dong dai d1b00000 mong doi: users 1, companies 1, jobs 6, rubrics 6,
-- rubric_criteria 19, interview_templates 6, job_screening_questions 4, job_applications 2,
-- application_status_history 8, application_screening_answers 2, interview_invitations 4,
-- interview_invitation_slots 7, application_messages 3, notifications 12, talent_pools 4,
-- talent_pool_entries 3, job_blind_review_changes 3 (tong 91).
-- ---------------------------------------------------------------------------
SELECT 'users' AS bang, count(*) FROM users WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'companies', count(*) FROM companies WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'jobs', count(*) FROM jobs WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'rubrics', count(*) FROM rubrics WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'rubric_criteria', count(*) FROM rubric_criteria WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'interview_templates', count(*) FROM interview_templates WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'job_screening_questions', count(*) FROM job_screening_questions WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'job_applications', count(*) FROM job_applications WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'application_status_history', count(*) FROM application_status_history WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'application_screening_answers', count(*) FROM application_screening_answers WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'interview_invitations', count(*) FROM interview_invitations WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'interview_invitation_slots', count(*) FROM interview_invitation_slots WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'application_messages', count(*) FROM application_messages WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'notifications', count(*) FROM notifications WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'talent_pools', count(*) FROM talent_pools WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'talent_pool_entries', count(*) FROM talent_pool_entries WHERE id::text LIKE 'd1b00000-%'
UNION ALL SELECT 'job_blind_review_changes', count(*) FROM job_blind_review_changes WHERE id::text LIKE 'd1b00000-%';

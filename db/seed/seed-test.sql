-- ---------------------------------------------------------------------------
-- db/seed/seed-test.sql
--
-- BO DU LIEU TEST DOC LAP - CHI de soat giao dien du moi trang thai, KHONG nap
-- khi demo cho hoi dong (tin OPEN cua cong ty test se hien tren bang viec lam
-- cong khai va trong goi y viec lam cua ung vien demo).
--
-- Doc lap voi bo demo (seed-demo-structural.sql / seed-demo-ai-output.sql):
-- nap duoc tren DB chi co schema, va nap cung DB voi hai tang demo khong va
-- cham - UUID tien to rieng e0..ed (demo tang 1 dung d0..d6, tang 2 UUID
-- ngau nhien), email rieng, ten cong ty rieng.
--
-- Idempotent: moi INSERT co ON CONFLICT (id) DO NOTHING, chay lai nhieu lan
-- khong loi, khong nhan doi. Chay lai KHONG khoi phuc dong da bi sua qua UI -
-- muon ve trang thai goc thi chay reset-test-data.sql truoc.
--
-- Moc thoi gian: TUYET DOI (07-10/2026) de thu tu su kien trong lich su don
-- luon nhat quan bat ke ngay nap. Chi deadline cua tin chua dong va lich phong
-- van chua dien ra tinh tuong doi theo luc nap (de luon con han/con o tuong lai).
--
-- KHONG co diem AI, giai thich, evidence, embedding viet tay hay gia. Ngoai le
-- DUY NHAT co output AI la dong resume_parsed_data cua R3 (muc 6) - output
-- THAT cua pipeline, xem comment tai cho.
--
-- Trang thai "dang cho" (CV PENDING, luot cham PENDING) duoc DONG BANG bang
-- next_attempt_at = 2099 - scheduler chi nhat dong co next_attempt_at NULL
-- hoac <= now(). Khong seed PROCESSING/RUNNING (stale-claim reaper se thu hoi).
-- Moi thong bao email_status = 'SKIPPED' de NotificationMailScheduler bo qua.
--
-- MAT KHAU CUA CA 5 TAI KHOAN TEST: "12345678" (BCrypt strength 10, sinh bang
-- BCryptPasswordEncoder that cua spring-security-crypto - khop SecurityConfig).
--
-- CACH CHAY (PowerShell, tu thu muc goc repo) - xem db/seed/README.md muc 8:
--   docker compose cp db/seed/seed-test.sql postgres:/tmp/seed-test.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-test.sql
--   .\db\seed\install-test-files.ps1
-- ---------------------------------------------------------------------------

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Nguoi dung: 1 HR + 1 ung vien test chinh + 3 ung vien phu (@test.local)
-- ---------------------------------------------------------------------------

INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified, created_at, updated_at) VALUES
    ('e0000000-0000-0000-0000-000000000001', 'minhdathr@gmail.com',
     '$2a$10$s./N/XWUeTp1KdR/gblIvOz7AwviQWPKBRQKSOysZ7QYdLj.u/bZG',
     'HR', 'Minh Đạt', '0900000101', TRUE, TRUE, '2026-07-01 08:00:00+00', '2026-07-01 08:00:00+00'),
    ('e0000000-0000-0000-0000-000000000002', 'quochuyuv@gmail.com',
     '$2a$10$s./N/XWUeTp1KdR/gblIvOz7AwviQWPKBRQKSOysZ7QYdLj.u/bZG',
     'CANDIDATE', 'Quốc Huy', '0900000111', TRUE, TRUE, '2026-07-10 08:00:00+00', '2026-07-10 08:00:00+00'),
    ('e0000000-0000-0000-0000-000000000003', 'le.thi.lan@test.local',
     '$2a$10$s./N/XWUeTp1KdR/gblIvOz7AwviQWPKBRQKSOysZ7QYdLj.u/bZG',
     'CANDIDATE', 'Lê Thị Lan', '0900000112', TRUE, TRUE, '2026-08-25 08:00:00+00', '2026-08-25 08:00:00+00'),
    ('e0000000-0000-0000-0000-000000000004', 'pham.van.khoa@test.local',
     '$2a$10$s./N/XWUeTp1KdR/gblIvOz7AwviQWPKBRQKSOysZ7QYdLj.u/bZG',
     'CANDIDATE', 'Phạm Văn Khoa', '0900000113', TRUE, TRUE, '2026-08-26 08:00:00+00', '2026-08-26 08:00:00+00'),
    ('e0000000-0000-0000-0000-000000000005', 'tran.bao.ngoc@test.local',
     '$2a$10$s./N/XWUeTp1KdR/gblIvOz7AwviQWPKBRQKSOysZ7QYdLj.u/bZG',
     'CANDIDATE', 'Trần Bảo Ngọc', '0900000114', TRUE, TRUE, '2026-08-28 08:00:00+00', '2026-08-28 08:00:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Ho so nghe nghiep (FR-U14)
--    - Quoc Huy: khai DU (ca cot V1 lan V10). embedding NULL - scheduler FR-U14
--      se goi OpenAI MOT lan khi backend chay (da chap nhan, khong lam gia).
--    - Le Thi Lan, Pham Van Khoa: "bo qua onboarding" - dung nhu skipOnboarding()
--      (chi dat onboarding_completed_at, moi truong khac rong) -> khong khop dieu
--      kien embedding (headline/skills/bio rong).
--    - Tran Bao Ngoc: KHONG co dong - nguoi dung cu truoc FR-U14 (V10 chi dat co
--      cho ho so da ton tai). Dang nhap se bi dua sang man onboarding.
-- ---------------------------------------------------------------------------

INSERT INTO candidate_profiles (
    id, user_id, headline, location, current_title, years_experience, date_of_birth,
    desired_industry_codes, desired_location_codes, desired_work_modes,
    skills, desired_salary_min, bio, onboarding_completed_at, created_at, updated_at
) VALUES (
    'e6000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002',
    'Chuyên viên Chăm sóc khách hàng', 'Đà Nẵng', 'Chuyên viên Chăm sóc khách hàng', 6.0, '1998-05-14',
    ARRAY['CUSTOMER_SERVICE', 'ADMINISTRATION'], ARRAY['DA_NANG', 'HA_NOI'], ARRAY['ONSITE', 'HYBRID'],
    ARRAY['Xử lý khiếu nại', 'CRM', 'Microsoft Excel', 'Giao tiếp qua điện thoại'], 12000000.00,
    'Hơn 6 năm làm chăm sóc khách hàng và hành chính văn phòng, mong muốn làm việc tại '
    || 'doanh nghiệp có quy trình hỗ trợ khách hàng bài bản.',
    '2026-07-10 08:05:00+00', '2026-07-10 08:05:00+00', '2026-07-10 08:05:00+00'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO candidate_profiles (id, user_id, onboarding_completed_at, created_at, updated_at) VALUES
    ('e6000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000003',
     '2026-08-25 08:02:00+00', '2026-08-25 08:02:00+00', '2026-08-25 08:02:00+00'),
    ('e6000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000004',
     '2026-08-26 08:02:00+00', '2026-08-26 08:02:00+00', '2026-08-26 08:02:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Cong ty test (mot cong ty chi co mot chu - uq_company_per_owner)
-- ---------------------------------------------------------------------------

INSERT INTO companies (
    id, owner_id, name, description, company_size, industry, website,
    contact_email, contact_phone, address, created_at, updated_at
) VALUES (
    'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
    'Công ty TNHH Thử Nghiệm Ánh Dương',
    'Doanh nghiệp dịch vụ hư cấu dùng cho bộ dữ liệu test giao diện: chăm sóc khách hàng, '
    || 'hành chính văn phòng, pháp chế và tư vấn bảo hiểm.',
    '50-99', 'Dịch vụ tổng hợp', 'https://anhduong-test.local',
    'tuyendung@anhduong-test.local', '02363000111',
    'Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng',
    '2026-07-01 09:00:00+00', '2026-07-01 09:00:00+00'
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Sau tin tuyen dung - du 4 trang thai (can >= 5 tin khong phai DRAFT de ung
--    vien test co don o ca 5 trang thai, vi uq_application_per_cycle).
--    J1 OPEN   - Cham soc khach hang, co luong
--    J2 OPEN   - Hanh chinh, KHONG cong bo luong (salary_min/max NULL)
--    J3 PAUSED - CHUA CHUAN HOA: chi co category/location cu, ma NULL. Trang thai
--                nay chi ton tai o tin cu do V9 chuyen (R-J3 chan tao/mo tin
--                thieu ma) - HR bam "Mo lai" se bi chan, dung hanh vi can soat.
--    J4 CLOSED, J5 CLOSED - da dong, co don HIRED/REJECTED
--    J6 DRAFT  - chua dang (published_at NULL)
--    Hai tin OPEN chua co job_embeddings -> JobEmbeddingScheduler goi OpenAI moi
--    tin MOT lan khi backend chay (da chap nhan, khong lam gia embedding).
-- ---------------------------------------------------------------------------

INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category, location, category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at, created_at, updated_at
) VALUES
    ('e2000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
     'Chuyên viên Chăm sóc khách hàng',
     'Tiếp nhận và xử lý yêu cầu, khiếu nại của khách hàng qua điện thoại và email, '
     || 'phối hợp với các bộ phận liên quan để giải quyết dứt điểm.',
     'Tối thiểu 1 năm kinh nghiệm chăm sóc khách hàng. Biết sử dụng phần mềm CRM là lợi thế.',
     NULL, NULL, 'CUSTOMER_SERVICE', 'DA_NANG', 'FULL_TIME', 'ONSITE',
     9000000.00, 14000000.00, 'VND',
     'OPEN', 1, CURRENT_DATE + 30, '2026-09-01 02:00:00+00', NULL, '2026-08-30 02:00:00+00', '2026-09-01 02:00:00+00'),
    ('e2000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
     'Nhân viên Hành chính văn phòng',
     'Quản lý văn thư, lưu trữ hồ sơ, sắp xếp lịch họp và hỗ trợ công việc hành chính chung của văn phòng.',
     'Thành thạo tin học văn phòng, cẩn thận, có kỹ năng tổ chức công việc.',
     NULL, NULL, 'ADMINISTRATION', 'HA_NOI', 'FULL_TIME', 'HYBRID',
     NULL, NULL, 'VND',
     'OPEN', 1, CURRENT_DATE + 45, '2026-09-05 02:00:00+00', NULL, '2026-09-03 02:00:00+00', '2026-09-05 02:00:00+00'),
    ('e2000000-0000-0000-0000-000000000003', 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
     'Chuyên viên Pháp chế',
     'Rà soát hợp đồng, tư vấn pháp lý nội bộ và theo dõi thay đổi quy định liên quan đến hoạt động công ty.',
     'Tốt nghiệp Luật, ưu tiên có kinh nghiệm pháp chế doanh nghiệp.',
     'Luật - Pháp chế doanh nghiệp', 'Sài Gòn (gần sân bay)', NULL, NULL, 'FULL_TIME', 'ONSITE',
     15000000.00, 22000000.00, 'VND',
     'PAUSED', 1, CURRENT_DATE + 30, '2026-08-10 02:00:00+00', NULL, '2026-08-08 02:00:00+00', '2026-08-20 02:00:00+00'),
    ('e2000000-0000-0000-0000-000000000004', 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
     'Chuyên viên Tuyển dụng',
     'Phụ trách tuyển dụng khối văn phòng: đăng tin, sàng lọc hồ sơ, sắp xếp và tham gia phỏng vấn.',
     'Tối thiểu 2 năm kinh nghiệm tuyển dụng, giao tiếp tốt.',
     NULL, NULL, 'HUMAN_RESOURCES', 'HO_CHI_MINH', 'FULL_TIME', 'ONSITE',
     12000000.00, 18000000.00, 'VND',
     'CLOSED', 1, '2026-08-05', '2026-07-05 02:00:00+00', NULL, '2026-07-03 02:00:00+00', '2026-08-06 02:00:00+00'),
    ('e2000000-0000-0000-0000-000000000005', 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
     'Nhân viên Tư vấn bảo hiểm',
     'Tư vấn sản phẩm bảo hiểm cho khách hàng cá nhân, chăm sóc hợp đồng sau bán.',
     'Giao tiếp tốt, chịu được áp lực chỉ tiêu.',
     NULL, NULL, 'INSURANCE', 'CAN_THO', 'CONTRACT', 'ONSITE',
     8000000.00, 12000000.00, 'VND',
     'CLOSED', 1, '2026-08-08', '2026-07-08 02:00:00+00', NULL, '2026-07-06 02:00:00+00', '2026-08-09 02:00:00+00'),
    ('e2000000-0000-0000-0000-000000000006', 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
     'Giao dịch viên',
     'Thực hiện giao dịch thu chi, hướng dẫn khách hàng hoàn thiện hồ sơ tại quầy.',
     'Tốt nghiệp chuyên ngành Tài chính - Ngân hàng, cẩn thận, chính xác.',
     NULL, NULL, 'FINANCE_BANKING', 'HUE', 'FULL_TIME', 'ONSITE',
     10000000.00, 15000000.00, 'VND',
     'DRAFT', 1, CURRENT_DATE + 60, NULL, NULL, '2026-09-20 02:00:00+00', '2026-09-20 02:00:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Rubric (1/tin, 3 tieu chi, tong trong so = 100) - rubric J1 KHOA vi co luot
--    cham dang cho (ScoringRunStateService.create khoa rubric khi tao luot);
--    rubric J2 MO vi luot cham duy nhat da FAILED (unlockRubricIfSafe).
-- ---------------------------------------------------------------------------

INSERT INTO rubrics (id, job_id, name, is_locked, created_at, updated_at) VALUES
    ('e3000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001', 'Rubric Chuyên viên Chăm sóc khách hàng', TRUE,  '2026-08-30 02:00:00+00', '2026-10-05 17:37:00+00'),
    ('e3000000-0000-0000-0000-000000000002', 'e2000000-0000-0000-0000-000000000002', 'Rubric Nhân viên Hành chính văn phòng',  FALSE, '2026-09-03 02:00:00+00', '2026-10-05 17:39:31+00'),
    ('e3000000-0000-0000-0000-000000000003', 'e2000000-0000-0000-0000-000000000003', 'Rubric Chuyên viên Pháp chế',            FALSE, '2026-08-08 02:00:00+00', '2026-08-08 02:00:00+00'),
    ('e3000000-0000-0000-0000-000000000004', 'e2000000-0000-0000-0000-000000000004', 'Rubric Chuyên viên Tuyển dụng',          FALSE, '2026-07-03 02:00:00+00', '2026-07-03 02:00:00+00'),
    ('e3000000-0000-0000-0000-000000000005', 'e2000000-0000-0000-0000-000000000005', 'Rubric Nhân viên Tư vấn bảo hiểm',       FALSE, '2026-07-06 02:00:00+00', '2026-07-06 02:00:00+00'),
    ('e3000000-0000-0000-0000-000000000006', 'e2000000-0000-0000-0000-000000000006', 'Rubric Giao dịch viên',                  FALSE, '2026-09-20 02:00:00+00', '2026-09-20 02:00:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order, created_at, updated_at) VALUES
    ('e4000000-0000-0000-0000-000000000011', 'e3000000-0000-0000-0000-000000000001', 'Kinh nghiệm chăm sóc khách hàng',
     'Số năm kinh nghiệm chăm sóc khách hàng, khối lượng yêu cầu đã xử lý.', 40.00, 5, 1, '2026-08-30 02:00:00+00', '2026-08-30 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000012', 'e3000000-0000-0000-0000-000000000001', 'Kỹ năng xử lý khiếu nại',
     'Khả năng tiếp nhận, phân loại và giải quyết khiếu nại đến khi đóng yêu cầu.', 35.00, 5, 2, '2026-08-30 02:00:00+00', '2026-08-30 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000013', 'e3000000-0000-0000-0000-000000000001', 'Kỹ năng sử dụng công cụ CRM',
     'Kinh nghiệm theo dõi yêu cầu khách hàng trên phần mềm CRM.', 25.00, 5, 3, '2026-08-30 02:00:00+00', '2026-08-30 02:00:00+00'),

    ('e4000000-0000-0000-0000-000000000021', 'e3000000-0000-0000-0000-000000000002', 'Kinh nghiệm hành chính văn phòng',
     'Số năm kinh nghiệm văn thư, lưu trữ, hành chính.', 40.00, 5, 1, '2026-09-03 02:00:00+00', '2026-09-03 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000022', 'e3000000-0000-0000-0000-000000000002', 'Kỹ năng tin học văn phòng',
     'Mức độ thành thạo Word, Excel và các công cụ văn phòng.', 35.00, 5, 2, '2026-09-03 02:00:00+00', '2026-09-03 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000023', 'e3000000-0000-0000-0000-000000000002', 'Kỹ năng tổ chức và giao tiếp',
     'Khả năng sắp xếp lịch, chuẩn bị tài liệu và phối hợp với các phòng ban.', 25.00, 5, 3, '2026-09-03 02:00:00+00', '2026-09-03 02:00:00+00'),

    ('e4000000-0000-0000-0000-000000000031', 'e3000000-0000-0000-0000-000000000003', 'Kinh nghiệm pháp chế doanh nghiệp',
     'Số năm làm pháp chế, quy mô doanh nghiệp đã làm việc.', 40.00, 5, 1, '2026-08-08 02:00:00+00', '2026-08-08 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000032', 'e3000000-0000-0000-0000-000000000003', 'Am hiểu pháp luật lao động và thương mại',
     'Mức độ nắm vững quy định pháp luật lao động, thương mại hiện hành.', 35.00, 5, 2, '2026-08-08 02:00:00+00', '2026-08-08 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000033', 'e3000000-0000-0000-0000-000000000003', 'Kỹ năng soạn thảo hợp đồng',
     'Khả năng soạn thảo, rà soát điều khoản hợp đồng.', 25.00, 5, 3, '2026-08-08 02:00:00+00', '2026-08-08 02:00:00+00'),

    ('e4000000-0000-0000-0000-000000000041', 'e3000000-0000-0000-0000-000000000004', 'Kinh nghiệm tuyển dụng',
     'Số năm kinh nghiệm tuyển dụng, số vị trí đã tuyển thành công.', 40.00, 5, 1, '2026-07-03 02:00:00+00', '2026-07-03 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000042', 'e3000000-0000-0000-0000-000000000004', 'Kỹ năng phỏng vấn và đánh giá ứng viên',
     'Khả năng thiết kế câu hỏi và đánh giá ứng viên khách quan.', 35.00, 5, 2, '2026-07-03 02:00:00+00', '2026-07-03 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000043', 'e3000000-0000-0000-0000-000000000004', 'Kỹ năng sử dụng kênh tuyển dụng',
     'Kinh nghiệm khai thác các kênh đăng tin và tìm kiếm ứng viên.', 25.00, 5, 3, '2026-07-03 02:00:00+00', '2026-07-03 02:00:00+00'),

    ('e4000000-0000-0000-0000-000000000051', 'e3000000-0000-0000-0000-000000000005', 'Kinh nghiệm tư vấn bán hàng',
     'Số năm kinh nghiệm tư vấn, bán hàng trực tiếp.', 40.00, 5, 1, '2026-07-06 02:00:00+00', '2026-07-06 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000052', 'e3000000-0000-0000-0000-000000000005', 'Hiểu biết sản phẩm bảo hiểm',
     'Mức độ hiểu các loại sản phẩm bảo hiểm cá nhân.', 35.00, 5, 2, '2026-07-06 02:00:00+00', '2026-07-06 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000053', 'e3000000-0000-0000-0000-000000000005', 'Kỹ năng giao tiếp và thuyết phục',
     'Khả năng trình bày, xử lý từ chối của khách hàng.', 25.00, 5, 3, '2026-07-06 02:00:00+00', '2026-07-06 02:00:00+00'),

    ('e4000000-0000-0000-0000-000000000061', 'e3000000-0000-0000-0000-000000000006', 'Kinh nghiệm giao dịch tài chính',
     'Kinh nghiệm thực hiện giao dịch thu chi tại quầy.', 40.00, 5, 1, '2026-09-20 02:00:00+00', '2026-09-20 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000062', 'e3000000-0000-0000-0000-000000000006', 'Kỹ năng tính toán và cẩn thận',
     'Mức độ chính xác khi xử lý số liệu, chứng từ.', 35.00, 5, 2, '2026-09-20 02:00:00+00', '2026-09-20 02:00:00+00'),
    ('e4000000-0000-0000-0000-000000000063', 'e3000000-0000-0000-0000-000000000006', 'Kỹ năng phục vụ khách hàng',
     'Thái độ và khả năng hướng dẫn khách hàng tại quầy.', 25.00, 5, 3, '2026-09-20 02:00:00+00', '2026-09-20 02:00:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Mau giay moi phong van (1/tin) - dung placeholder that cua
--    InterviewInvitationService: {{candidateName}}, {{jobTitle}}, {{companyName}}.
-- ---------------------------------------------------------------------------

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address, created_at, updated_at)
SELECT ('e5000000-0000-0000-0000-00000000000' || n)::uuid,
       ('e2000000-0000-0000-0000-00000000000' || n)::uuid,
       'Công ty TNHH Thử Nghiệm Ánh Dương',
       'Thư mời phỏng vấn vị trí {{jobTitle}} - {{companyName}}',
       'Kính gửi {{candidateName}},' || E'\n\n'
       || 'Cảm ơn bạn đã ứng tuyển vị trí {{jobTitle}} tại {{companyName}}. Chúng tôi trân trọng mời '
       || 'bạn tham gia buổi phỏng vấn theo thời gian và địa điểm ghi trong thư này.' || E'\n\n'
       || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
       || 'Trân trọng,',
       'Minh Đạt', 'Trưởng nhóm Tuyển dụng',
       'Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng',
       j.created_at, j.created_at
FROM generate_series(1, 6) AS n
JOIN jobs j ON j.id = ('e2000000-0000-0000-0000-00000000000' || n)::uuid
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. CV (file tren dia do install-test-files.ps1 cai, khoa = resumes/<id>.<ext>)
--    R1 Quoc Huy  FAILED  EXTRACT_EMPTY   (file PDF trang, khong co lop chu)
--    R2 Quoc Huy  PENDING dong bang, CV CHINH
--    R3 Quoc Huy  DONE    (DOCX, output trich xuat THAT - muc 8), KHONG phai CV
--                 chinh -> ResumeEmbeddingScheduler (chi quet is_primary) khong goi OpenAI
--    R4 Le Thi Lan     PENDING dong bang, CV chinh
--    R5 Pham Van Khoa  FAILED  EXTRACT_CORRUPT (file khong phai PDF), CV chinh
--    R6 Tran Bao Ngoc  PENDING dong bang, CV chinh
-- ---------------------------------------------------------------------------

INSERT INTO resumes (
    id, candidate_id, file_url, file_name, file_type, file_size, version_label, is_primary,
    parse_status, parse_error, uploaded_at, claimed_at, attempt_count, next_attempt_at
) VALUES
    ('e7000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002',
     'resumes/e7000000-0000-0000-0000-000000000001.pdf', 'cv-quoc-huy-ban-scan.pdf', 'PDF', 574, 'Bản scan cũ', FALSE,
     'FAILED', 'EXTRACT_EMPTY: Không trích xuất được nội dung văn bản từ file CV (có thể là bản scan ảnh, hệ thống chưa hỗ trợ nhận dạng chữ trong ảnh)',
     '2026-07-12 03:00:00+00', '2026-07-12 03:00:02+00', 0, NULL),
    ('e7000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002',
     'resumes/e7000000-0000-0000-0000-000000000002.pdf', 'cv-quoc-huy-2026.pdf', 'PDF', 837, 'Bản chính 2026', TRUE,
     'PENDING', NULL, '2026-08-20 03:00:00+00', NULL, 0, '2099-01-01 00:00:00+00'),
    -- uploaded_at/claimed_at la moc THAT cua lan chay pipeline (muc 8)
    ('e7000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000002',
     'resumes/e7000000-0000-0000-0000-000000000003.docx', 'cv-mau-quoc-huy.docx', 'DOCX', 1883, 'Bản DOCX - CSKH', FALSE,
     'DONE', NULL, '2026-10-05 17:20:13.253316+00', '2026-10-05 17:33:45.944247+00', 0, NULL),
    ('e7000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000003',
     'resumes/e7000000-0000-0000-0000-000000000004.pdf', 'cv-le-thi-lan.pdf', 'PDF', 779, NULL, TRUE,
     'PENDING', NULL, '2026-09-02 03:00:00+00', NULL, 0, '2099-01-01 00:00:00+00'),
    ('e7000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000004',
     'resumes/e7000000-0000-0000-0000-000000000005.pdf', 'cv-pham-van-khoa.pdf', 'PDF', 71, NULL, TRUE,
     'FAILED', 'EXTRACT_CORRUPT: File CV bị hỏng hoặc không đúng định dạng, không thể đọc được',
     '2026-09-04 03:00:00+00', '2026-09-04 03:00:02+00', 0, NULL),
    ('e7000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000005',
     'resumes/e7000000-0000-0000-0000-000000000006.pdf', 'cv-tran-bao-ngoc.pdf', 'PDF', 787, NULL, TRUE,
     'PENDING', NULL, '2026-09-06 03:00:00+00', NULL, 0, '2099-01-01 00:00:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. resume_parsed_data cua R3 - OUTPUT THAT CUA PIPELINE, KHONG PHAI SQL VIET TAY.
--
--    Sinh bang cach chay backend that DUNG MOT LAN ngay 06/10/2026 (gio VN; 05/10
--    17:34 UTC) tren DB chi co user Quoc Huy + R3 (cac scheduler goi OpenAI/SMTP
--    tat), roi pg_dump --data-only --column-inserts bang nay, chep nguyen van va
--    chi them ON CONFLICT (id) DO NOTHING.
--      model          : claude-sonnet-4-6
--      prompt_version : resume-parse-v2
--      token_usage    : 5246
--      kinh nghiem    : tinh boi ResumeExperienceScheduler that (74 thang, 2 muc)
--    embedding de NULL: R3 khong phai CV chinh, F1 khong bao gio embed no.
--
--    PHAI CHAY LAI pipeline va thay dong nay khi schema bang resume_parsed_data
--    doi (them/doi cot) hoac khi resume-parse doi phien ban prompt.
-- ---------------------------------------------------------------------------

INSERT INTO public.resume_parsed_data (id, resume_id, raw_text, data, embedding, model, prompt_version, token_usage, parsed_at, industry_code, region_code, experience_months, experience_entries_counted, experience_entries_skipped, experience_computed_at) VALUES ('01ddeb0e-8c60-40a3-9bf7-58c727c5f21e', 'e7000000-0000-0000-0000-000000000003', 'CV MẪU – DỮ LIỆU TEST
Hồ sơ hư cấu dùng để soát giao diện, không phải thông tin của người thật.
QUỐC HUY
Chuyên viên Chăm sóc khách hàng
THÔNG TIN LIÊN HỆ
Email: quochuy.cv@test.local
Điện thoại: 0900 000 111
Địa chỉ: Quận Hải Châu, Đà Nẵng
HỌC VẤN
09/2016 – 06/2020: Cử nhân Quản trị Kinh doanh – Trường Cao đẳng Thử Nghiệm Đà Nẵng
KINH NGHIỆM LÀM VIỆC
03/2022 – 08/2026: Chuyên viên Chăm sóc khách hàng – Công ty TNHH Dịch vụ Mẫu Sông Hàn
- Tiếp nhận và xử lý trung bình 60 cuộc gọi, email của khách hàng mỗi ngày.
- Xây dựng bộ câu trả lời mẫu cho 25 tình huống khiếu nại thường gặp.
- Phối hợp với bộ phận kỹ thuật theo dõi yêu cầu trên hệ thống CRM đến khi đóng phiếu.
07/2020 – 02/2022: Nhân viên Hành chính – Công ty Cổ phần Thử Nghiệm Bạch Đằng
- Quản lý văn thư, lưu trữ hồ sơ và hợp đồng của phòng Kinh doanh.
- Lập lịch họp, đặt phòng và chuẩn bị tài liệu cho các buổi làm việc với đối tác.
KỸ NĂNG
Giao tiếp qua điện thoại, xử lý khiếu nại, sử dụng CRM, Microsoft Excel, Microsoft Word, tiếng Anh giao tiếp.', '{"skills": ["Giao tiếp qua điện thoại", "xử lý khiếu nại", "sử dụng CRM", "Microsoft Excel", "Microsoft Word", "tiếng Anh giao tiếp"], "contact": {"email": "quochuy.cv@test.local", "phone": "0900 000 111", "address": "Quận Hải Châu, Đà Nẵng", "fullName": "QUỐC HUY", "linkedin": null}, "projects": [], "education": [{"gpa": null, "major": "Quản trị Kinh doanh", "degree": "Cử nhân", "school": "Trường Cao đẳng Thử Nghiệm Đà Nẵng", "endDate": "06/2020", "startDate": "09/2016"}], "experience": [{"title": "Chuyên viên Chăm sóc khách hàng", "company": "Công ty TNHH Dịch vụ Mẫu Sông Hàn", "endDate": "08/2026", "startDate": "03/2022", "description": "- Tiếp nhận và xử lý trung bình 60 cuộc gọi, email của khách hàng mỗi ngày.\n- Xây dựng bộ câu trả lời mẫu cho 25 tình huống khiếu nại thường gặp.\n- Phối hợp với bộ phận kỹ thuật theo dõi yêu cầu trên hệ thống CRM đến khi đóng phiếu."}, {"title": "Nhân viên Hành chính", "company": "Công ty Cổ phần Thử Nghiệm Bạch Đằng", "endDate": "02/2022", "startDate": "07/2020", "description": "- Quản lý văn thư, lưu trữ hồ sơ và hợp đồng của phòng Kinh doanh.\n- Lập lịch họp, đặt phòng và chuẩn bị tài liệu cho các buổi làm việc với đối tác."}], "currentTitle": null, "industryCode": "CUSTOMER_SERVICE", "locationText": "Đà Nẵng", "certifications": []}', NULL, 'claude-sonnet-4-6', 'resume-parse-v2', 5246, '2026-10-05 17:34:01.891541+00', 'CUSTOMER_SERVICE', 'DA_NANG', 74, 2, 0, '2026-10-05 17:34:01.903536+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9. Don ung tuyen - du 5 trang thai; Quoc Huy co don o ca 5. ai_consent = TRUE.
--    A1 Quoc Huy  -> J1  PENDING            (R3, co luot cham PENDING dong bang)
--    A2 Quoc Huy  -> J2  INTERVIEW_INVITED  (R3, co luot cham FAILED, co giay moi)
--    A3 Quoc Huy  -> J3  WITHDRAWN          (rut tu PENDING)
--    A4 Quoc Huy  -> J4  HIRED              (qua INTERVIEW_INVITED, co giay moi)
--    A5 Quoc Huy  -> J5  REJECTED
--    A6 Le Thi Lan     -> J1 PENDING
--    A7 Pham Van Khoa  -> J1 REJECTED
--    A8 Pham Van Khoa  -> J2 PENDING
--    A9 Tran Bao Ngoc  -> J1 INTERVIEW_INVITED (co giay moi)
-- ---------------------------------------------------------------------------

INSERT INTO job_applications (
    id, job_id, candidate_id, resume_id, recruitment_cycle, status,
    ai_consent, ai_consent_at, cover_letter, applied_at, updated_at
) VALUES
    ('e8000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002',
     'e7000000-0000-0000-0000-000000000003', 1, 'PENDING', TRUE, '2026-10-05 17:35:00+00',
     'Tôi có hơn 4 năm làm chăm sóc khách hàng và mong muốn được làm việc tại Đà Nẵng.',
     '2026-10-05 17:35:00+00', '2026-10-05 17:35:00+00'),
    ('e8000000-0000-0000-0000-000000000002', 'e2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002',
     'e7000000-0000-0000-0000-000000000003', 1, 'INTERVIEW_INVITED', TRUE, '2026-10-05 17:36:00+00', NULL,
     '2026-10-05 17:36:00+00', '2026-10-05 17:40:00+00'),
    ('e8000000-0000-0000-0000-000000000003', 'e2000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000002',
     'e7000000-0000-0000-0000-000000000001', 1, 'WITHDRAWN', TRUE, '2026-08-12 03:00:00+00', NULL,
     '2026-08-12 03:00:00+00', '2026-08-14 13:00:00+00'),
    ('e8000000-0000-0000-0000-000000000004', 'e2000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000002',
     'e7000000-0000-0000-0000-000000000001', 1, 'HIRED', TRUE, '2026-07-15 03:00:00+00',
     'Mong được trao đổi thêm với anh chị về vị trí Chuyên viên Tuyển dụng.',
     '2026-07-15 03:00:00+00', '2026-07-28 02:00:00+00'),
    ('e8000000-0000-0000-0000-000000000005', 'e2000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000002',
     'e7000000-0000-0000-0000-000000000001', 1, 'REJECTED', TRUE, '2026-07-16 03:00:00+00', NULL,
     '2026-07-16 03:00:00+00', '2026-07-22 02:00:00+00'),
    ('e8000000-0000-0000-0000-000000000006', 'e2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000003',
     'e7000000-0000-0000-0000-000000000004', 1, 'PENDING', TRUE, '2026-09-03 03:00:00+00', NULL,
     '2026-09-03 03:00:00+00', '2026-09-03 03:00:00+00'),
    ('e8000000-0000-0000-0000-000000000007', 'e2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000004',
     'e7000000-0000-0000-0000-000000000005', 1, 'REJECTED', TRUE, '2026-09-05 03:00:00+00', NULL,
     '2026-09-05 03:00:00+00', '2026-09-12 02:00:00+00'),
    ('e8000000-0000-0000-0000-000000000008', 'e2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000004',
     'e7000000-0000-0000-0000-000000000005', 1, 'PENDING', TRUE, '2026-09-08 03:00:00+00', NULL,
     '2026-09-08 03:00:00+00', '2026-09-08 03:00:00+00'),
    ('e8000000-0000-0000-0000-000000000009', 'e2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000005',
     'e7000000-0000-0000-0000-000000000006', 1, 'INTERVIEW_INVITED', TRUE, '2026-09-07 03:00:00+00', NULL,
     '2026-09-07 03:00:00+00', '2026-09-15 02:00:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 10. Lich su trang thai - dung may trang thai FR-H07: NULL -> PENDING do chinh
--     ung vien (ApplicationService.apply), cac buoc PENDING -> INTERVIEW_INVITED/
--     REJECTED va INTERVIEW_INVITED -> HIRED do HR, rut don do ung vien.
-- ---------------------------------------------------------------------------

INSERT INTO application_status_history (id, application_id, from_status, to_status, changed_by, note, changed_at) VALUES
    -- A4: PENDING -> INTERVIEW_INVITED -> HIRED
    ('e9000000-0000-0000-0000-000000000001', 'e8000000-0000-0000-0000-000000000004', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000002', NULL, '2026-07-15 03:00:00+00'),
    ('e9000000-0000-0000-0000-000000000002', 'e8000000-0000-0000-0000-000000000004', 'PENDING', 'INTERVIEW_INVITED', 'e0000000-0000-0000-0000-000000000001', NULL, '2026-07-20 02:00:00+00'),
    ('e9000000-0000-0000-0000-000000000003', 'e8000000-0000-0000-0000-000000000004', 'INTERVIEW_INVITED', 'HIRED', 'e0000000-0000-0000-0000-000000000001', NULL, '2026-07-28 02:00:00+00'),
    -- A5: PENDING -> REJECTED
    ('e9000000-0000-0000-0000-000000000004', 'e8000000-0000-0000-0000-000000000005', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000002', NULL, '2026-07-16 03:00:00+00'),
    ('e9000000-0000-0000-0000-000000000005', 'e8000000-0000-0000-0000-000000000005', 'PENDING', 'REJECTED', 'e0000000-0000-0000-0000-000000000001', NULL, '2026-07-22 02:00:00+00'),
    -- A3: PENDING -> WITHDRAWN (ung vien tu rut)
    ('e9000000-0000-0000-0000-000000000006', 'e8000000-0000-0000-0000-000000000003', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000002', NULL, '2026-08-12 03:00:00+00'),
    ('e9000000-0000-0000-0000-000000000007', 'e8000000-0000-0000-0000-000000000003', 'PENDING', 'WITHDRAWN', 'e0000000-0000-0000-0000-000000000002', NULL, '2026-08-14 13:00:00+00'),
    -- A6
    ('e9000000-0000-0000-0000-000000000008', 'e8000000-0000-0000-0000-000000000006', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000003', NULL, '2026-09-03 03:00:00+00'),
    -- A7: PENDING -> REJECTED
    ('e9000000-0000-0000-0000-000000000009', 'e8000000-0000-0000-0000-000000000007', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000004', NULL, '2026-09-05 03:00:00+00'),
    ('e9000000-0000-0000-0000-000000000010', 'e8000000-0000-0000-0000-000000000007', 'PENDING', 'REJECTED', 'e0000000-0000-0000-0000-000000000001', NULL, '2026-09-12 02:00:00+00'),
    -- A8
    ('e9000000-0000-0000-0000-000000000011', 'e8000000-0000-0000-0000-000000000008', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000004', NULL, '2026-09-08 03:00:00+00'),
    -- A9: PENDING -> INTERVIEW_INVITED
    ('e9000000-0000-0000-0000-000000000012', 'e8000000-0000-0000-0000-000000000009', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000005', NULL, '2026-09-07 03:00:00+00'),
    ('e9000000-0000-0000-0000-000000000013', 'e8000000-0000-0000-0000-000000000009', 'PENDING', 'INTERVIEW_INVITED', 'e0000000-0000-0000-0000-000000000001', NULL, '2026-09-15 02:00:00+00'),
    -- A1
    ('e9000000-0000-0000-0000-000000000014', 'e8000000-0000-0000-0000-000000000001', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000002', NULL, '2026-10-05 17:35:00+00'),
    -- A2: PENDING -> INTERVIEW_INVITED
    ('e9000000-0000-0000-0000-000000000015', 'e8000000-0000-0000-0000-000000000002', NULL, 'PENDING', 'e0000000-0000-0000-0000-000000000002', NULL, '2026-10-05 17:36:00+00'),
    ('e9000000-0000-0000-0000-000000000016', 'e8000000-0000-0000-0000-000000000002', 'PENDING', 'INTERVIEW_INVITED', 'e0000000-0000-0000-0000-000000000001', NULL, '2026-10-05 17:40:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 11. Giay moi phong van - moi lan chuyen sang INTERVIEW_INVITED co dung mot giay
--     moi gui cung luc (InterviewInvitationService.send). Lich A2/A9 tinh tuong
--     doi theo luc nap de luon o tuong lai; lich A4 da qua (don da HIRED).
-- ---------------------------------------------------------------------------

INSERT INTO interview_invitations (id, application_id, scheduled_at, location, subject, rendered_content, sent_at, sent_by, created_at) VALUES
    ('ea000000-0000-0000-0000-000000000001', 'e8000000-0000-0000-0000-000000000002',
     date_trunc('day', now()) + INTERVAL '7 days 2 hours',
     'Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng',
     'Thư mời phỏng vấn vị trí Nhân viên Hành chính văn phòng - Công ty TNHH Thử Nghiệm Ánh Dương',
     'Kính gửi Quốc Huy,' || E'\n\n'
     || 'Cảm ơn bạn đã ứng tuyển vị trí Nhân viên Hành chính văn phòng tại Công ty TNHH Thử Nghiệm Ánh Dương. '
     || 'Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn theo thời gian và địa điểm ghi trong thư này.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     '2026-10-05 17:40:00+00', 'e0000000-0000-0000-0000-000000000001', '2026-10-05 17:40:00+00'),
    ('ea000000-0000-0000-0000-000000000002', 'e8000000-0000-0000-0000-000000000009',
     date_trunc('day', now()) + INTERVAL '5 days 3 hours',
     'Phỏng vấn trực tuyến qua Google Meet (đường dẫn gửi trước 1 ngày)',
     'Thư mời phỏng vấn vị trí Chuyên viên Chăm sóc khách hàng - Công ty TNHH Thử Nghiệm Ánh Dương',
     'Kính gửi Trần Bảo Ngọc,' || E'\n\n'
     || 'Cảm ơn bạn đã ứng tuyển vị trí Chuyên viên Chăm sóc khách hàng tại Công ty TNHH Thử Nghiệm Ánh Dương. '
     || 'Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn theo thời gian và địa điểm ghi trong thư này.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     '2026-09-15 02:00:00+00', 'e0000000-0000-0000-0000-000000000001', '2026-09-15 02:00:00+00'),
    ('ea000000-0000-0000-0000-000000000003', 'e8000000-0000-0000-0000-000000000004',
     '2026-07-25 02:00:00+00',
     'Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng',
     'Thư mời phỏng vấn vị trí Chuyên viên Tuyển dụng - Công ty TNHH Thử Nghiệm Ánh Dương',
     'Kính gửi Quốc Huy,' || E'\n\n'
     || 'Cảm ơn bạn đã ứng tuyển vị trí Chuyên viên Tuyển dụng tại Công ty TNHH Thử Nghiệm Ánh Dương. '
     || 'Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn theo thời gian và địa điểm ghi trong thư này.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     '2026-07-20 02:00:00+00', 'e0000000-0000-0000-0000-000000000001', '2026-07-20 02:00:00+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 12. Luot cham diem - KHONG co dong criterion_scores/score_explanations nao.
--     rubric_snapshot dung tu chinh rubric_criteria (khong phai output AI), dung
--     dinh dang RubricSnapshotMapper.
--     B1 PENDING tren A1, dong bang next_attempt_at = 2099 (attempt_count 0).
--     B2 FAILED tren A2: het 3 lan thu do loi tam thoi (markRetryExhausted) -
--        attempt_count 3, error_message LLM_RETRY_EXHAUSTED, co finished_at.
-- ---------------------------------------------------------------------------

INSERT INTO scoring_runs (
    id, application_id, status, rubric_snapshot, total_score, model, prompt_version, token_usage,
    error_message, started_at, finished_at, created_at, attempt_count, next_attempt_at
)
SELECT v.id, v.application_id, v.status,
       (SELECT jsonb_build_object(
                   'name', r.name,
                   'criteria', jsonb_agg(jsonb_build_object(
                           'criterionId', c.id, 'name', c.name, 'description', c.description,
                           'weight', c.weight, 'maxScore', c.max_score, 'scaleDescription', c.scale_description)
                       ORDER BY c.display_order))
        FROM rubrics r JOIN rubric_criteria c ON c.rubric_id = r.id
        WHERE r.job_id = v.job_id
        GROUP BY r.name),
       NULL, NULL, NULL, NULL,
       v.error_message, v.started_at, v.finished_at, v.created_at, v.attempt_count, v.next_attempt_at
FROM (VALUES
    ('eb000000-0000-0000-0000-000000000001'::uuid, 'e8000000-0000-0000-0000-000000000001'::uuid,
     'e2000000-0000-0000-0000-000000000001'::uuid, 'PENDING', NULL,
     NULL::timestamptz, NULL::timestamptz, '2026-10-05 17:37:00+00'::timestamptz, 0, '2099-01-01 00:00:00+00'::timestamptz),
    ('eb000000-0000-0000-0000-000000000002'::uuid, 'e8000000-0000-0000-0000-000000000002'::uuid,
     'e2000000-0000-0000-0000-000000000002'::uuid, 'FAILED',
     'LLM_RETRY_EXHAUSTED: Đã thử lại nhiều lần do lỗi kết nối/quá tải của AI nhưng không thành công',
     '2026-10-05 17:39:01+00'::timestamptz, '2026-10-05 17:39:31+00'::timestamptz, '2026-10-05 17:36:30+00'::timestamptz, 3, NULL::timestamptz)
) AS v(id, application_id, job_id, status, error_message, started_at, finished_at, created_at, attempt_count, next_attempt_at)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 13. Thong bao - khop 1-1 voi su kien that (NotificationEventListener): moi don
--     nop -> HR; rut don -> HR; moi lan HR doi trang thai -> ung vien. Khong co
--     SCORING_FINISHED vi khong co luot DONE. Tieu de/noi dung theo
--     NotificationContentBuilder; RIENG link cua thong bao HR la link CU '/hr/jobs'
--     (truoc FR-H09) - CO Y giu nguyen de soat FR-H09 R-N2 (thong bao cu van mo
--     duoc). Thong bao HR tao moi tu FR-H09 tro '/hr/applications/{id}'.
--     email_status = 'SKIPPED' cho moi dong - khong gui thu that.
-- ---------------------------------------------------------------------------

INSERT INTO notifications (id, user_id, type, title, body, link, entity_type, entity_id, is_read, read_at, email_status, created_at) VALUES
    -- HR Minh Dat: 6 da doc, 4 chua doc
    ('ec000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Quốc Huy vừa ứng tuyển vị trí "Chuyên viên Tuyển dụng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000004',
     TRUE, '2026-07-15 04:00:00+00', 'SKIPPED', '2026-07-15 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Quốc Huy vừa ứng tuyển vị trí "Nhân viên Tư vấn bảo hiểm"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000005',
     TRUE, '2026-07-16 04:00:00+00', 'SKIPPED', '2026-07-16 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Quốc Huy vừa ứng tuyển vị trí "Chuyên viên Pháp chế"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000003',
     TRUE, '2026-08-12 04:00:00+00', 'SKIPPED', '2026-08-12 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_WITHDRAWN', 'Ứng viên đã rút đơn',
     'Ứng viên Quốc Huy đã rút đơn ứng tuyển vị trí "Chuyên viên Pháp chế"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000003',
     TRUE, '2026-08-15 02:00:00+00', 'SKIPPED', '2026-08-14 13:00:01+00'),
    ('ec000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Lê Thị Lan vừa ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000006',
     TRUE, '2026-09-03 05:00:00+00', 'SKIPPED', '2026-09-03 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Phạm Văn Khoa vừa ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000007',
     TRUE, '2026-09-05 05:00:00+00', 'SKIPPED', '2026-09-05 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000007', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Trần Bảo Ngọc vừa ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000009',
     FALSE, NULL, 'SKIPPED', '2026-09-07 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000008', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Phạm Văn Khoa vừa ứng tuyển vị trí "Nhân viên Hành chính văn phòng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000008',
     FALSE, NULL, 'SKIPPED', '2026-09-08 03:00:01+00'),
    ('ec000000-0000-0000-0000-000000000009', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Quốc Huy vừa ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000001',
     FALSE, NULL, 'SKIPPED', '2026-10-05 17:35:01+00'),
    ('ec000000-0000-0000-0000-000000000010', 'e0000000-0000-0000-0000-000000000001', 'APPLICATION_SUBMITTED', 'Có đơn ứng tuyển mới',
     'Ứng viên Quốc Huy vừa ứng tuyển vị trí "Nhân viên Hành chính văn phòng"', '/hr/jobs', 'APPLICATION', 'e8000000-0000-0000-0000-000000000002',
     FALSE, NULL, 'SKIPPED', '2026-10-05 17:36:01+00'),
    -- Ung vien Quoc Huy: 2 da doc, 2 chua doc
    ('ec000000-0000-0000-0000-000000000011', 'e0000000-0000-0000-0000-000000000002', 'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Chuyên viên Tuyển dụng" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn', '/candidate/applications',
     'APPLICATION', 'e8000000-0000-0000-0000-000000000004', TRUE, '2026-07-20 05:00:00+00', 'SKIPPED', '2026-07-20 02:00:01+00'),
    ('ec000000-0000-0000-0000-000000000012', 'e0000000-0000-0000-0000-000000000002', 'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Nhân viên Tư vấn bảo hiểm" của bạn đã chuyển sang trạng thái: Bị từ chối', '/candidate/applications',
     'APPLICATION', 'e8000000-0000-0000-0000-000000000005', TRUE, '2026-07-22 05:00:00+00', 'SKIPPED', '2026-07-22 02:00:01+00'),
    ('ec000000-0000-0000-0000-000000000013', 'e0000000-0000-0000-0000-000000000002', 'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Chuyên viên Tuyển dụng" của bạn đã chuyển sang trạng thái: Trúng tuyển', '/candidate/applications',
     'APPLICATION', 'e8000000-0000-0000-0000-000000000004', FALSE, NULL, 'SKIPPED', '2026-07-28 02:00:01+00'),
    ('ec000000-0000-0000-0000-000000000014', 'e0000000-0000-0000-0000-000000000002', 'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Nhân viên Hành chính văn phòng" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn', '/candidate/applications',
     'APPLICATION', 'e8000000-0000-0000-0000-000000000002', FALSE, NULL, 'SKIPPED', '2026-10-05 17:40:01+00'),
    -- Ung vien phu
    ('ec000000-0000-0000-0000-000000000015', 'e0000000-0000-0000-0000-000000000004', 'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng" của bạn đã chuyển sang trạng thái: Bị từ chối', '/candidate/applications',
     'APPLICATION', 'e8000000-0000-0000-0000-000000000007', FALSE, NULL, 'SKIPPED', '2026-09-12 02:00:01+00'),
    ('ec000000-0000-0000-0000-000000000016', 'e0000000-0000-0000-0000-000000000005', 'APPLICATION_STATUS_CHANGED', 'Cập nhật đơn ứng tuyển',
     'Đơn ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng" của bạn đã chuyển sang trạng thái: Đã mời phỏng vấn', '/candidate/applications',
     'APPLICATION', 'e8000000-0000-0000-0000-000000000009', FALSE, NULL, 'SKIPPED', '2026-09-15 02:00:01+00')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 14. Tin nhan theo don (FR-C06 R-S1) - chi tin chu, khong tep, KHONG co dong
--     thong bao NEW_MESSAGE di kem. read_at = luc BEN NHAN doc (NULL = chua doc).
--     A2 (INTERVIEW_INVITED): HR -> UV da doc, UV -> HR da doc, HR -> UV CHUA doc
--        -> Quoc Huy co 1 tin chua doc; luong hai chieu.
--     A3 (WITHDRAWN): UV -> HR da doc, HR -> UV da doc -> cuoc trao doi chi con xem.
--        Hai tin dat 12/08 va 13/08, giua luc nop (12/08 03:00) va luc rut don
--        (14/08 13:00) - backend chan gui tin vao don da rut (R-M4, L8).
--     A9 (Tran Bao Ngoc): UV -> HR CHUA doc -> HR co 1 tin chua doc.
--     A1 khong co tin (trang thai rong).
--     Tin moi nhat: A3 13/08, A2 07/10, A9 08/10 -> hop thu HR xep A9, A2, A3;
--     hop thu Quoc Huy xep A2, A3. Gio UTC 02:00-09:00 = gio hanh chinh VN, de
--     ngay hien thi giong nhau o ca UTC lan gio VN.
-- ---------------------------------------------------------------------------

INSERT INTO application_messages (id, application_id, sender_id, sender_role, body, read_at, created_at) VALUES
    -- A2
    ('ed000000-0000-0000-0000-000000000001', 'e8000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'HR',
     'Chào Quốc Huy, cảm ơn bạn đã nhận lời mời phỏng vấn vị trí Nhân viên Hành chính văn phòng. '
     || 'Bạn vui lòng mang theo bản sao bằng tốt nghiệp khi đến phỏng vấn nhé.',
     '2026-10-06 03:00:00+00', '2026-10-06 02:15:00+00'),
    ('ed000000-0000-0000-0000-000000000002', 'e8000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', 'CANDIDATE',
     'Dạ em chào anh/chị, em đã nhận được thư mời.' || E'\n' || 'Em sẽ chuẩn bị đầy đủ giấy tờ ạ.',
     '2026-10-06 04:00:00+00', '2026-10-06 03:05:00+00'),
    ('ed000000-0000-0000-0000-000000000003', 'e8000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'HR',
     'Buổi phỏng vấn có thể bắt đầu sớm hơn 30 phút so với lịch trong thư mời. Bạn có sắp xếp được không?',
     NULL, '2026-10-07 02:30:00+00'),
    -- A3
    ('ed000000-0000-0000-0000-000000000004', 'e8000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000002', 'CANDIDATE',
     'Em vừa nhận được lời mời làm việc ở nơi khác nên có thể em sẽ rút đơn vị trí Chuyên viên Pháp chế. '
     || 'Em báo trước để anh/chị tiện sắp xếp ạ.',
     '2026-08-13 02:00:00+00', '2026-08-12 08:00:00+00'),
    ('ed000000-0000-0000-0000-000000000005', 'e8000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'HR',
     'Cảm ơn bạn đã báo trước. Nếu bạn quyết định rút đơn, chúc bạn thành công với công việc mới.',
     '2026-08-13 04:00:00+00', '2026-08-13 02:10:00+00'),
    -- A9
    ('ed000000-0000-0000-0000-000000000006', 'e8000000-0000-0000-0000-000000000009', 'e0000000-0000-0000-0000-000000000005', 'CANDIDATE',
     'Em chào anh/chị, buổi phỏng vấn trực tuyến dùng Google Meet phải không ạ? Em cần chuẩn bị gì trước buổi phỏng vấn ạ?',
     NULL, '2026-10-08 03:20:00+00')
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- Kiem tra sau khi nap - so dong mong doi: users 5, candidate_profiles 3,
-- companies 1, jobs 6, rubrics 6, rubric_criteria 18, interview_templates 6,
-- resumes 6, resume_parsed_data 1, job_applications 9,
-- application_status_history 16, interview_invitations 3, scoring_runs 2,
-- notifications 16, application_messages 6.
-- ---------------------------------------------------------------------------
SELECT 'users' AS bang, count(*) FROM users WHERE id::text LIKE 'e0000000-%'
UNION ALL SELECT 'candidate_profiles', count(*) FROM candidate_profiles WHERE id::text LIKE 'e6000000-%'
UNION ALL SELECT 'companies', count(*) FROM companies WHERE id::text LIKE 'e1000000-%'
UNION ALL SELECT 'jobs', count(*) FROM jobs WHERE id::text LIKE 'e2000000-%'
UNION ALL SELECT 'rubrics', count(*) FROM rubrics WHERE id::text LIKE 'e3000000-%'
UNION ALL SELECT 'rubric_criteria', count(*) FROM rubric_criteria WHERE id::text LIKE 'e4000000-%'
UNION ALL SELECT 'interview_templates', count(*) FROM interview_templates WHERE id::text LIKE 'e5000000-%'
UNION ALL SELECT 'resumes', count(*) FROM resumes WHERE id::text LIKE 'e7000000-%'
UNION ALL SELECT 'resume_parsed_data', count(*) FROM resume_parsed_data WHERE resume_id::text LIKE 'e7000000-%'
UNION ALL SELECT 'job_applications', count(*) FROM job_applications WHERE id::text LIKE 'e8000000-%'
UNION ALL SELECT 'application_status_history', count(*) FROM application_status_history WHERE id::text LIKE 'e9000000-%'
UNION ALL SELECT 'interview_invitations', count(*) FROM interview_invitations WHERE id::text LIKE 'ea000000-%'
UNION ALL SELECT 'scoring_runs', count(*) FROM scoring_runs WHERE id::text LIKE 'eb000000-%'
UNION ALL SELECT 'notifications', count(*) FROM notifications WHERE id::text LIKE 'ec000000-%'
UNION ALL SELECT 'application_messages', count(*) FROM application_messages WHERE id::text LIKE 'ed000000-%';

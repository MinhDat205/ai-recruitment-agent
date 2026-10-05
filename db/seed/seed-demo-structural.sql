-- ---------------------------------------------------------------------------
-- db/seed/seed-demo-structural.sql
--
-- Day la tai khoan DEMO dung cho buoi bao ve do an - mat khau ben duoi duoc
-- cong khai CO CHU DICH de nguoi cham/cong tac vien dang nhap thu, KHONG
-- phai ro ri bi mat.
--
-- Tang 1 cua seed demo (chore/seed-demo) - du lieu cau truc thuan, KHONG phu
-- thuoc AI, KHONG phu thuoc thoi diem chay that. Idempotent (ON CONFLICT DO
-- NOTHING), chay lai bao nhieu lan cung duoc.
--
-- Gom DUNG: 1 HR + 1 company + 6 job (OPEN, published_at rai qua 3 gio/2 ngay/
-- 6 ngay/10 ngay/20 ngay/35 ngay truoc luc nap - FR-U07 R-D1) + 6 rubric (kem
-- tieu chi, tong trong so = 100%) + 6 interview_template + 9 candidate + 9
-- candidate_profiles (FR-U14 - ho so nghe nghiep/mong muon, 4/9 co du du lieu).
--
-- Ung vien thu 9 (Hoang Minh Tuan, FR-U15 dot 8) KHONG co resumes - minh hoa
-- nhanh PROFILE/DESIRES cua goi y viec lam (chua tung co CV nao, chi co ho so
-- nghe nghiep) - 8 ung vien con lai deu co resumes o tang 2.
--
-- KHONG co INSERT INTO resumes / job_applications - hai bang do thuoc tang 2,
-- sinh qua UI that o Dot 3-4 (gan voi file that tren dia + output AI). Ung
-- vien thu 9 KHONG co du lieu nao o tang 2 (chu dich, xem tren).
--
-- MAT KHAU DANG NHAP DEMO CHO CA HR LAN 9 CANDIDATE: "Demo1234"
--
-- CACH CHAY (PowerShell, tu thu muc goc repo), giong dev-seed.sql:
--
--   docker compose cp db/seed/seed-demo-structural.sql postgres:/tmp/seed-demo-structural.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment -f /tmp/seed-demo-structural.sql
-- ---------------------------------------------------------------------------

SET client_encoding = 'UTF8';

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. HR + 8 candidate
--    Mat khau chung: "secret" (xem canh bao hash o dau file)
-- ---------------------------------------------------------------------------

INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'hr@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'HR', 'Nguyễn Thị Hạnh', '0901000000', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-mot-cot: Java Backend -> Job 1
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000002', 'tran.minh.hoang@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Trần Minh Hoàng', '0901000002', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-hai-cot: IT tong hop, bo cuc 2 cot -> Job 1
-- CANH BAO: chua co ten that tu CV nguon - "Lê Văn Đức" la ten tam, SUA lai
-- cho khop ten that tren file CV truoc khi dung o Dot 3.
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000003', 'le.van.duc@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Lê Văn Đức', '0901000003', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-tester-hai-cot: QA, bo cuc 2 cot -> Job 2
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000004', 'pham.quoc.bao@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Phạm Quốc Bảo', '0901000004', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-devops-hai-cot: DevOps -> Job 3
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000005', 'nguyen.hai.son@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Nguyễn Hải Sơn', '0901000005', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-ke-toan: Ke toan -> Job 4 (dung nganh) + Job 1 (lech nganh, co chu dich)
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000006', 'nguyen.thi.thu.ha@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Nguyễn Thị Thu Hà', '0901000006', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-marketing -> Job 5
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000007', 'do.khanh.linh@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Đỗ Khánh Linh', '0901000007', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-sales -> Job 6
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000008', 'vo.thanh.tung@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Võ Thanh Tùng', '0901000008', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- cv-nhan-su: Nhan su -> KHONG nop job nao (ca demo nguong 0.40, xem README)
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000009', 'bui.ngoc.mai@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Bùi Ngọc Mai', '0901000009', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- FR-U15 dot 8: ung vien thu 9, CHUA TUNG tai CV len (khong co resumes o tang
-- 2) - chi co ho so nghe nghiep, minh hoa nhanh PROFILE/DESIRES cua goi y viec
-- lam (R-V3/R-V5). Mong muon nganh SALES, khop Job 6 (Nhan vien kinh doanh qua
-- dien thoai) - khac 3 nganh da dung cho 3 ung vien co san (IT_SOFTWARE,
-- ACCOUNTING_AUDIT, MARKETING_COMMUNICATIONS).
INSERT INTO users (id, email, password_hash, role, full_name, phone, is_active, email_verified) VALUES
    ('d0000000-0000-0000-0000-000000000010', 'hoang.minh.tuan@demo.local',
     '$2a$10$nFbFFDzxI6exO4n2YiGste.x65NTurqzpwvPHaYx5r.bTpz4v547y',
     'CANDIDATE', 'Hoàng Minh Tuấn', '0901000010', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 1b. Ho so nghe nghiep (FR-U14) cho 9 candidate - ca 9 da "qua" man onboarding
--     (onboarding_completed_at khac NULL). 4 nguoi (Tran Minh Hoang - IT_SOFTWARE,
--     Nguyen Thi Thu Ha - ACCOUNTING_AUDIT, Do Khanh Linh - MARKETING_COMMUNICATIONS,
--     Hoang Minh Tuan - SALES, FR-U15 dot 8) co du mong muon nghe nghiep, khac
--     nganh nhau va khop dung nganh cua job demo tuong ung (Job 1, Job 4, Job 5,
--     Job 6 o muc 3 duoi day). 5 nguoi con lai de mang rong/NULL (ho so mong
--     muon trong, nhung van da qua man onboarding).
--     embedding/embedding_model de NULL ca 9 - CandidateProfileEmbeddingScheduler
--     (FR-U14 R-E4) tu tinh khi backend chay that voi khoa OpenAI that (xem README).
--     Ma nganh/khu vuc lay tu danh muc C05 (V8__catalogs.sql): IT_SOFTWARE,
--     ACCOUNTING_AUDIT, MARKETING_COMMUNICATIONS, SALES, HO_CHI_MINH deu da ton tai.
-- ---------------------------------------------------------------------------

-- Tran Minh Hoang (d0000000-...0002) - Java Backend, khop Job 1 (IT_SOFTWARE)
INSERT INTO candidate_profiles (
    id, user_id, headline,
    desired_industry_codes, desired_location_codes, desired_work_modes,
    skills, desired_salary_min, bio, onboarding_completed_at
) VALUES (
    'd6000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002',
    'Lập trình viên Backend (Java/Spring Boot)',
    ARRAY['IT_SOFTWARE'], ARRAY['HO_CHI_MINH'], ARRAY['HYBRID', 'REMOTE'],
    ARRAY['Java', 'Spring Boot', 'PostgreSQL', 'REST API'], 35000000.00,
    'Hơn 3 năm kinh nghiệm phát triển backend với Java và Spring Boot, mong muốn '
    || 'tham gia các hệ thống phục vụ lượng người dùng lớn.',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- Le Van Duc (d0000000-...0003) - ho so mong muon de trong
INSERT INTO candidate_profiles (id, user_id, onboarding_completed_at) VALUES
    ('d6000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', now())
ON CONFLICT (id) DO NOTHING;

-- Pham Quoc Bao (d0000000-...0004) - ho so mong muon de trong
INSERT INTO candidate_profiles (id, user_id, onboarding_completed_at) VALUES
    ('d6000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', now())
ON CONFLICT (id) DO NOTHING;

-- Nguyen Hai Son (d0000000-...0005) - ho so mong muon de trong
INSERT INTO candidate_profiles (id, user_id, onboarding_completed_at) VALUES
    ('d6000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000005', now())
ON CONFLICT (id) DO NOTHING;

-- Nguyen Thi Thu Ha (d0000000-...0006) - Ke toan, khop Job 4 (ACCOUNTING_AUDIT)
INSERT INTO candidate_profiles (
    id, user_id, headline,
    desired_industry_codes, desired_location_codes, desired_work_modes,
    skills, desired_salary_min, bio, onboarding_completed_at
) VALUES (
    'd6000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000006',
    'Kế toán tổng hợp',
    ARRAY['ACCOUNTING_AUDIT'], ARRAY['HO_CHI_MINH'], ARRAY['ONSITE'],
    ARRAY['MISA', 'Excel', 'VAS', 'Báo cáo tài chính'], 15000000.00,
    'Có kinh nghiệm làm kế toán tổng hợp tại doanh nghiệp vừa và nhỏ, nắm vững '
    || 'chuẩn mực kế toán Việt Nam (VAS).',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- Do Khanh Linh (d0000000-...0007) - Marketing, khop Job 5 (MARKETING_COMMUNICATIONS)
INSERT INTO candidate_profiles (
    id, user_id, headline,
    desired_industry_codes, desired_location_codes, desired_work_modes,
    skills, desired_salary_min, bio, onboarding_completed_at
) VALUES (
    'd6000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000007',
    'Chuyên viên Marketing',
    ARRAY['MARKETING_COMMUNICATIONS'], ARRAY['HO_CHI_MINH'], ARRAY['HYBRID'],
    ARRAY['Facebook Ads', 'Google Ads', 'Content Marketing', 'SEO'], 15000000.00,
    'Yêu thích xây dựng nội dung và chạy quảng cáo đa kênh, mong muốn phát triển '
    || 'sự nghiệp trong lĩnh vực digital marketing.',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- Vo Thanh Tung (d0000000-...0008) - ho so mong muon de trong
INSERT INTO candidate_profiles (id, user_id, onboarding_completed_at) VALUES
    ('d6000000-0000-0000-0000-000000000007', 'd0000000-0000-0000-0000-000000000008', now())
ON CONFLICT (id) DO NOTHING;

-- Bui Ngoc Mai (d0000000-...0009) - ho so mong muon de trong
INSERT INTO candidate_profiles (id, user_id, onboarding_completed_at) VALUES
    ('d6000000-0000-0000-0000-000000000008', 'd0000000-0000-0000-0000-000000000009', now())
ON CONFLICT (id) DO NOTHING;

-- Hoang Minh Tuan (d0000000-...0010, FR-U15 dot 8) - Kinh doanh/Telesales,
-- khop Job 6 (SALES, HO_CHI_MINH, ONSITE, luong 8-15 trieu) - KHONG co resumes
-- (xem muc 1), minh hoa nhanh PROFILE/DESIRES cua goi y viec lam khi ung vien
-- chua tung tai CV.
INSERT INTO candidate_profiles (
    id, user_id, headline,
    desired_industry_codes, desired_location_codes, desired_work_modes,
    skills, desired_salary_min, bio, onboarding_completed_at
) VALUES (
    'd6000000-0000-0000-0000-000000000009', 'd0000000-0000-0000-0000-000000000010',
    'Chuyên viên Kinh doanh / Telesales',
    ARRAY['SALES'], ARRAY['HO_CHI_MINH'], ARRAY['ONSITE'],
    ARRAY['Telesales', 'Chăm sóc khách hàng', 'Đàm phán', 'CRM'], 10000000.00,
    'Có kinh nghiệm tư vấn và chăm sóc khách hàng qua điện thoại, mong muốn '
    || 'làm việc tại môi trường năng động, có chỉ tiêu doanh số rõ ràng.',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Company
-- ---------------------------------------------------------------------------

INSERT INTO companies (
    id, owner_id, name, description, company_size, industry,
    contact_email, contact_phone, address
) VALUES (
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Công ty Cổ phần Công nghệ Hoàn Mỹ',
    'Doanh nghiệp đa ngành với đội ngũ Công nghệ, Kế toán, Marketing và Kinh doanh, '
    || 'phục vụ khách hàng doanh nghiệp trên toàn quốc.',
    '500-999',
    'Đa ngành / Dịch vụ tổng hợp',
    'tuyendung@hoanmy-demo.local',
    '02838001234',
    'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh'
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. 6 job (OPEN tu seed - FR-U07 R-D1, doi tu DRAFT truoc day). Ca 6 job da co
--    category_code/location_code (tu C05) nen qua duoc guard R-J3 cua
--    JobOwnerService ngay ca khi di qua UI sau nay - khong can sua gi them.
--    published_at = now() tru cac khoang khac nhau de soat tay bo loc
--    "Thoi gian dang" (postedWithin) va sap xep "Moi nhat" co thu tu ro rang.
--    deadline giu CURRENT_DATE + 45 ngay (tinh tuong doi theo luc nap, luon
--    con han).
-- ---------------------------------------------------------------------------

-- Job 1: Senior Java Backend Developer
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    'd2000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Senior Java Backend Developer',
    'Tham gia phát triển và bảo trì hệ thống backend phục vụ nhiều triệu người dùng, '
    || 'làm việc trực tiếp với đội sản phẩm để thiết kế API và tối ưu hiệu năng.',
    'Tối thiểu 3 năm kinh nghiệm Java. Thành thạo Spring Boot, PostgreSQL, thiết kế REST API.',
    'IT_SOFTWARE', 'HO_CHI_MINH', 'FULL_TIME', 'HYBRID',
    30000000.00, 50000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE + INTERVAL '45 days', now() - INTERVAL '3 hours', NULL
)
ON CONFLICT (id) DO NOTHING;

-- Job 2: Kỹ sư Kiểm thử phần mềm (QA Engineer)
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    'd2000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Kỹ sư Kiểm thử phần mềm (QA Engineer)',
    'Xây dựng và thực thi test case cho các tính năng mới, phối hợp với đội phát triển '
    || 'để đảm bảo chất lượng sản phẩm trước khi phát hành.',
    'Có kinh nghiệm kiểm thử thủ công và tự động (Selenium/Cypress). Hiểu quy trình Agile/Scrum.',
    'IT_SOFTWARE', 'HO_CHI_MINH', 'FULL_TIME', 'ONSITE',
    15000000.00, 25000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE + INTERVAL '45 days', now() - INTERVAL '2 days', NULL
)
ON CONFLICT (id) DO NOTHING;

-- Job 3: Kỹ sư DevOps
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    'd2000000-0000-0000-0000-000000000003',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Kỹ sư DevOps',
    'Vận hành và tối ưu hạ tầng CI/CD, triển khai và giám sát hệ thống container hoá '
    || 'trên môi trường cloud.',
    'Thành thạo Docker, Kubernetes, kinh nghiệm với CI/CD (GitLab CI/Jenkins). '
    || 'Ưu tiên có chứng chỉ cloud.',
    'IT_SOFTWARE', 'HA_NOI', 'FULL_TIME', 'REMOTE',
    25000000.00, 40000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE + INTERVAL '45 days', now() - INTERVAL '6 days', NULL
)
ON CONFLICT (id) DO NOTHING;

-- Job 4: Kế toán tổng hợp
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    'd2000000-0000-0000-0000-000000000004',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Kế toán tổng hợp',
    'Thực hiện công tác kế toán tổng hợp, lập báo cáo tài chính định kỳ, phối hợp '
    || 'với kiểm toán độc lập cuối năm.',
    'Tốt nghiệp chuyên ngành Kế toán/Kiểm toán. Thành thạo phần mềm MISA, nắm vững '
    || 'chuẩn mực kế toán Việt Nam (VAS).',
    'ACCOUNTING_AUDIT', 'HO_CHI_MINH', 'FULL_TIME', 'ONSITE',
    12000000.00, 18000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE + INTERVAL '45 days', now() - INTERVAL '10 days', NULL
)
ON CONFLICT (id) DO NOTHING;

-- Job 5: Chuyên viên Marketing
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    'd2000000-0000-0000-0000-000000000005',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Chuyên viên Marketing',
    'Lên kế hoạch và triển khai chiến dịch marketing đa kênh, quản lý nội dung '
    || 'mạng xã hội và đo lường hiệu quả chiến dịch.',
    'Có kinh nghiệm digital marketing, thành thạo công cụ quảng cáo Facebook/Google Ads.',
    'MARKETING_COMMUNICATIONS', 'HO_CHI_MINH', 'FULL_TIME', 'HYBRID',
    12000000.00, 20000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE + INTERVAL '45 days', now() - INTERVAL '20 days', NULL
)
ON CONFLICT (id) DO NOTHING;

-- Job 6: Nhân viên kinh doanh qua điện thoại
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category_code, location_code, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    'd2000000-0000-0000-0000-000000000006',
    'd1000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'Nhân viên kinh doanh qua điện thoại',
    'Gọi điện tư vấn và chăm sóc khách hàng tiềm năng, chốt đơn hàng qua điện thoại, '
    || 'đạt chỉ tiêu doanh số hàng tháng.',
    'Ưu tiên có kinh nghiệm telesales/chăm sóc khách hàng. Giọng nói rõ ràng, '
    || 'chịu được áp lực doanh số.',
    'SALES', 'HO_CHI_MINH', 'FULL_TIME', 'ONSITE',
    8000000.00, 15000000.00, 'VND',
    'OPEN', 1, CURRENT_DATE + INTERVAL '45 days', now() - INTERVAL '35 days', NULL
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. 6 rubric (1 rubric / job)
-- ---------------------------------------------------------------------------

INSERT INTO rubrics (id, job_id, name, is_locked) VALUES
    ('d3000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001', 'Rubric Senior Java Backend Developer', FALSE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubrics (id, job_id, name, is_locked) VALUES
    ('d3000000-0000-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000002', 'Rubric Kỹ sư Kiểm thử phần mềm', FALSE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubrics (id, job_id, name, is_locked) VALUES
    ('d3000000-0000-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000003', 'Rubric Kỹ sư DevOps', FALSE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubrics (id, job_id, name, is_locked) VALUES
    ('d3000000-0000-0000-0000-000000000004', 'd2000000-0000-0000-0000-000000000004', 'Rubric Kế toán tổng hợp', FALSE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubrics (id, job_id, name, is_locked) VALUES
    ('d3000000-0000-0000-0000-000000000005', 'd2000000-0000-0000-0000-000000000005', 'Rubric Chuyên viên Marketing', FALSE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubrics (id, job_id, name, is_locked) VALUES
    ('d3000000-0000-0000-0000-000000000006', 'd2000000-0000-0000-0000-000000000006', 'Rubric Nhân viên kinh doanh qua điện thoại', FALSE)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Tieu chi rubric - 4 tieu chi / job, tong weight = 100
-- ---------------------------------------------------------------------------

-- Job 1: Senior Java Backend Developer (rubric d3...0001)
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000011', 'd3000000-0000-0000-0000-000000000001',
     'Kinh nghiệm lập trình Backend/Java',
     'Số năm kinh nghiệm thực tế làm việc với Java, mức độ phức tạp của hệ thống đã tham gia.',
     35.00, 5, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000012', 'd3000000-0000-0000-0000-000000000001',
     'Kỹ năng Spring Boot & thiết kế API/CSDL',
     'Mức độ thành thạo Spring Boot, thiết kế REST API, tối ưu truy vấn PostgreSQL.',
     30.00, 5, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000013', 'd3000000-0000-0000-0000-000000000001',
     'Học vấn & chứng chỉ liên quan',
     'Bằng cấp chuyên ngành CNTT/Khoa học máy tính, chứng chỉ kỹ thuật liên quan (nếu có).',
     15.00, 5, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000014', 'd3000000-0000-0000-0000-000000000001',
     'Kỹ năng mềm & giao tiếp',
     'Khả năng trình bày, phối hợp với đội sản phẩm và các bên liên quan.',
     20.00, 5, 4)
ON CONFLICT (id) DO NOTHING;

-- Job 2: Kỹ sư Kiểm thử phần mềm (rubric d3...0002)
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000021', 'd3000000-0000-0000-0000-000000000002',
     'Kinh nghiệm kiểm thử phần mềm',
     'Số năm kinh nghiệm QA, số loại dự án/nền tảng đã kiểm thử.',
     35.00, 5, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000022', 'd3000000-0000-0000-0000-000000000002',
     'Kỹ năng kiểm thử tự động & viết test case',
     'Thành thạo công cụ kiểm thử tự động (Selenium/Cypress), khả năng thiết kế test case bao phủ đầy đủ.',
     30.00, 5, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000023', 'd3000000-0000-0000-0000-000000000002',
     'Hiểu biết quy trình QA/CI',
     'Kinh nghiệm kiểm thử hồi quy, viết báo cáo lỗi, tích hợp kiểm thử vào pipeline CI.',
     20.00, 5, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000024', 'd3000000-0000-0000-0000-000000000002',
     'Kỹ năng mềm & giao tiếp',
     'Khả năng phối hợp với đội phát triển, trình bày lỗi rõ ràng.',
     15.00, 5, 4)
ON CONFLICT (id) DO NOTHING;

-- Job 3: Kỹ sư DevOps (rubric d3...0003)
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000031', 'd3000000-0000-0000-0000-000000000003',
     'Kinh nghiệm vận hành hệ thống/CI-CD',
     'Số năm kinh nghiệm vận hành hạ tầng, xây dựng pipeline CI/CD thực tế.',
     35.00, 5, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000032', 'd3000000-0000-0000-0000-000000000003',
     'Kỹ năng Kubernetes/Docker & hạ tầng cloud',
     'Mức độ thành thạo container hoá, triển khai trên AWS/GCP/Azure.',
     30.00, 5, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000033', 'd3000000-0000-0000-0000-000000000003',
     'Kỹ năng scripting/tự động hoá',
     'Khả năng viết script tự động hoá vận hành (Bash/Python/Terraform).',
     20.00, 5, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000034', 'd3000000-0000-0000-0000-000000000003',
     'Kỹ năng mềm & giao tiếp',
     'Khả năng phối hợp xử lý sự cố, viết tài liệu vận hành.',
     15.00, 5, 4)
ON CONFLICT (id) DO NOTHING;

-- Job 4: Kế toán tổng hợp (rubric d3...0004)
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000041', 'd3000000-0000-0000-0000-000000000004',
     'Kinh nghiệm kế toán tổng hợp',
     'Số năm kinh nghiệm làm kế toán tổng hợp, quy mô doanh nghiệp đã làm việc.',
     35.00, 5, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000042', 'd3000000-0000-0000-0000-000000000004',
     'Am hiểu chuẩn mực kế toán VAS & quy định thuế',
     'Mức độ nắm vững VAS, quy định thuế hiện hành.',
     30.00, 5, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000043', 'd3000000-0000-0000-0000-000000000004',
     'Thành thạo phần mềm kế toán MISA/Excel',
     'Kinh nghiệm sử dụng phần mềm kế toán và Excel nâng cao.',
     20.00, 5, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000044', 'd3000000-0000-0000-0000-000000000004',
     'Cẩn thận, chính xác & kỹ năng mềm',
     'Mức độ cẩn thận trong xử lý số liệu, khả năng phối hợp với kiểm toán.',
     15.00, 5, 4)
ON CONFLICT (id) DO NOTHING;

-- Job 5: Chuyên viên Marketing (rubric d3...0005)
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000051', 'd3000000-0000-0000-0000-000000000005',
     'Kinh nghiệm marketing/truyền thông',
     'Số năm kinh nghiệm, số chiến dịch đã triển khai.',
     35.00, 5, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000052', 'd3000000-0000-0000-0000-000000000005',
     'Kỹ năng lên kế hoạch nội dung & digital marketing',
     'Khả năng lên kế hoạch nội dung, chạy quảng cáo Facebook/Google Ads.',
     30.00, 5, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000053', 'd3000000-0000-0000-0000-000000000005',
     'Khả năng phân tích số liệu chiến dịch',
     'Kinh nghiệm đo lường, phân tích hiệu quả chiến dịch bằng công cụ phân tích.',
     20.00, 5, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000054', 'd3000000-0000-0000-0000-000000000005',
     'Kỹ năng mềm & sáng tạo',
     'Khả năng sáng tạo nội dung, phối hợp đội nhóm.',
     15.00, 5, 4)
ON CONFLICT (id) DO NOTHING;

-- Job 6: Nhân viên kinh doanh qua điện thoại (rubric d3...0006)
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000061', 'd3000000-0000-0000-0000-000000000006',
     'Kinh nghiệm bán hàng/telesales',
     'Số năm kinh nghiệm bán hàng qua điện thoại, thành tích doanh số trước đây.',
     35.00, 5, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000062', 'd3000000-0000-0000-0000-000000000006',
     'Kỹ năng giao tiếp & thuyết phục qua điện thoại',
     'Khả năng giao tiếp, xử lý từ chối, thuyết phục khách hàng.',
     30.00, 5, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000063', 'd3000000-0000-0000-0000-000000000006',
     'Khả năng chịu áp lực doanh số',
     'Mức độ chịu được áp lực chỉ tiêu, tính kiên trì.',
     20.00, 5, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO rubric_criteria (id, rubric_id, name, description, weight, max_score, display_order) VALUES
    ('d4000000-0000-0000-0000-000000000064', 'd3000000-0000-0000-0000-000000000006',
     'Kỹ năng mềm & chăm sóc khách hàng',
     'Khả năng chăm sóc khách hàng sau bán, xây dựng quan hệ lâu dài.',
     15.00, 5, 4)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. 6 interview_template (1 template / job, o ngay gio de trong)
-- ---------------------------------------------------------------------------

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address) VALUES
    ('d5000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001',
     'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Thư mời phỏng vấn vị trí Senior Java Backend Developer - Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Kính gửi [Tên ứng viên],' || E'\n\n'
     || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí Senior Java Backend Developer tại '
     || 'Công ty Cổ phần Công nghệ Hoàn Mỹ. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
     || 'vào lúc [Giờ phỏng vấn] ngày [Ngày phỏng vấn] tại địa chỉ công ty.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     'Nguyễn Thị Hạnh', 'Chuyên viên Tuyển dụng',
     'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh')
ON CONFLICT (id) DO NOTHING;

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address) VALUES
    ('d5000000-0000-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000002',
     'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Thư mời phỏng vấn vị trí Kỹ sư Kiểm thử phần mềm (QA Engineer) - Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Kính gửi [Tên ứng viên],' || E'\n\n'
     || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí Kỹ sư Kiểm thử phần mềm tại '
     || 'Công ty Cổ phần Công nghệ Hoàn Mỹ. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
     || 'vào lúc [Giờ phỏng vấn] ngày [Ngày phỏng vấn] tại địa chỉ công ty.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     'Nguyễn Thị Hạnh', 'Chuyên viên Tuyển dụng',
     'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh')
ON CONFLICT (id) DO NOTHING;

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address) VALUES
    ('d5000000-0000-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000003',
     'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Thư mời phỏng vấn vị trí Kỹ sư DevOps - Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Kính gửi [Tên ứng viên],' || E'\n\n'
     || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí Kỹ sư DevOps tại '
     || 'Công ty Cổ phần Công nghệ Hoàn Mỹ. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
     || 'vào lúc [Giờ phỏng vấn] ngày [Ngày phỏng vấn] tại địa chỉ công ty.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     'Nguyễn Thị Hạnh', 'Chuyên viên Tuyển dụng',
     'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh')
ON CONFLICT (id) DO NOTHING;

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address) VALUES
    ('d5000000-0000-0000-0000-000000000004', 'd2000000-0000-0000-0000-000000000004',
     'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Thư mời phỏng vấn vị trí Kế toán tổng hợp - Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Kính gửi [Tên ứng viên],' || E'\n\n'
     || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí Kế toán tổng hợp tại '
     || 'Công ty Cổ phần Công nghệ Hoàn Mỹ. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
     || 'vào lúc [Giờ phỏng vấn] ngày [Ngày phỏng vấn] tại địa chỉ công ty.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     'Nguyễn Thị Hạnh', 'Chuyên viên Tuyển dụng',
     'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh')
ON CONFLICT (id) DO NOTHING;

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address) VALUES
    ('d5000000-0000-0000-0000-000000000005', 'd2000000-0000-0000-0000-000000000005',
     'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Thư mời phỏng vấn vị trí Chuyên viên Marketing - Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Kính gửi [Tên ứng viên],' || E'\n\n'
     || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí Chuyên viên Marketing tại '
     || 'Công ty Cổ phần Công nghệ Hoàn Mỹ. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
     || 'vào lúc [Giờ phỏng vấn] ngày [Ngày phỏng vấn] tại địa chỉ công ty.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     'Nguyễn Thị Hạnh', 'Chuyên viên Tuyển dụng',
     'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh')
ON CONFLICT (id) DO NOTHING;

INSERT INTO interview_templates (id, job_id, company_name, subject, body, sender_name, sender_title, address) VALUES
    ('d5000000-0000-0000-0000-000000000006', 'd2000000-0000-0000-0000-000000000006',
     'Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Thư mời phỏng vấn vị trí Nhân viên kinh doanh qua điện thoại - Công ty Cổ phần Công nghệ Hoàn Mỹ',
     'Kính gửi [Tên ứng viên],' || E'\n\n'
     || 'Cảm ơn bạn đã dành thời gian ứng tuyển vị trí Nhân viên kinh doanh qua điện thoại tại '
     || 'Công ty Cổ phần Công nghệ Hoàn Mỹ. Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn '
     || 'vào lúc [Giờ phỏng vấn] ngày [Ngày phỏng vấn] tại địa chỉ công ty.' || E'\n\n'
     || 'Vui lòng phản hồi email này để xác nhận tham dự.' || E'\n\n'
     || 'Trân trọng,',
     'Nguyễn Thị Hạnh', 'Chuyên viên Tuyển dụng',
     'Tầng 5, Toà nhà Hoàn Mỹ, 123 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh')
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- Kiem tra sau khi chay: phai ra dung 1 HR + 9 candidate + 1 company + 6 job
-- + 6 rubric (moi rubric 4 tieu chi, tong weight = 100) + 6 interview_template
-- + 9 candidate_profiles (FR-U14/FR-U15, ca 9 da qua onboarding, 4 co mong muon)
-- ---------------------------------------------------------------------------
SELECT role, count(*) FROM users WHERE id::text LIKE 'd0000000-%' GROUP BY role;
SELECT
    count(*) AS so_ho_so,
    count(*) FILTER (WHERE onboarding_completed_at IS NOT NULL) AS da_qua_onboarding,
    count(*) FILTER (WHERE cardinality(desired_industry_codes) > 0) AS co_mong_muon_nganh,
    count(*) FILTER (WHERE embedding IS NULL) AS embedding_null
FROM candidate_profiles
WHERE id::text LIKE 'd6000000-%';
SELECT j.title, r.name AS rubric, sum(rc.weight) AS tong_weight, count(rc.id) AS so_tieu_chi
FROM jobs j
JOIN rubrics r ON r.job_id = j.id
JOIN rubric_criteria rc ON rc.rubric_id = r.id
WHERE j.id::text LIKE 'd2000000-%'
GROUP BY j.title, r.name
ORDER BY j.title;
SELECT count(*) FROM interview_templates WHERE id::text LIKE 'd5000000-%';

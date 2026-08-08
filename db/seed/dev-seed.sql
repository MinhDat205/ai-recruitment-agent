-- ---------------------------------------------------------------------------
-- db/seed/dev-seed.sql
--
-- Du lieu mau cho moi truong DEV. KHONG phai migration Flyway.
-- Chay tay, xem huong dan o cuoi file.
--
-- Muc dich: nhanh A2 (FR-C02) chi doc bang jobs/companies, nhung chua co
-- nhanh nao tao duoc job (viec do thuoc B1/B2). File nay tao san du lieu de
-- kiem chung tieu chi "Xong khi" cua A2.
--
-- Idempotent: chay lai nhieu lan khong tao ban ghi trung (ON CONFLICT DO NOTHING).
-- ---------------------------------------------------------------------------

SET client_encoding = 'UTF8';

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Cong ty
--    owner_id = tai khoan HR co san tu luc test tay A1 (1@Gmmail.com)
-- ---------------------------------------------------------------------------
INSERT INTO companies (
    id, owner_id, name, logo_url, description,
    company_size, industry, website, contact_email, contact_phone, address
) VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'Công ty TNHH Công nghệ Bình Minh',
    NULL,
    'Công ty phát triển phần mềm cho thị trường trong nước, thành lập năm 2015. '
    || 'Sản phẩm chính là các hệ thống quản trị doanh nghiệp và nền tảng thương mại điện tử.',
    '100-499',
    'Công nghệ thông tin',
    'https://binhminh-tech.example.com',
    'tuyendung@binhminh-tech.example.com',
    '02839001234',
    'Số 12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh'
)
ON CONFLICT (id) DO NOTHING;


-- ---------------------------------------------------------------------------
-- 2. Tin tuyen dung
--
--    3 job OPEN       -> PHAI hien ra o API cong khai
--    1 job DRAFT      -> PHAI bi an
--    1 job PAUSED     -> PHAI bi an
--    1 job CLOSED     -> PHAI bi an
--    1 job OPEN nhung deleted_at IS NOT NULL -> PHAI bi an
--
--    Tong 7 ban ghi, dung 3 ban ghi duoc phep lo ra ngoai.
-- ---------------------------------------------------------------------------

-- (1) OPEN - co day du truong, dung de kiem trang chi tiet
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category, location, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'Senior Java Backend Developer',
    'Tham gia phát triển và bảo trì hệ thống backend phục vụ hơn 200 nghìn người dùng. '
    || 'Làm việc trực tiếp với đội sản phẩm để phân tích yêu cầu và thiết kế API.',
    'Tối thiểu 4 năm kinh nghiệm Java. Thành thạo Spring Boot, PostgreSQL. '
    || 'Có kinh nghiệm thiết kế REST API và tối ưu truy vấn cơ sở dữ liệu.',
    'Công nghệ thông tin',
    'TP. Hồ Chí Minh',
    'FULL_TIME',
    'ONSITE',
    30000000.00, 45000000.00, 'VND',
    'OPEN', 1,
    CURRENT_DATE + INTERVAL '45 days',
    now() - INTERVAL '3 days',
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- (2) OPEN - khac dia diem va work_mode, dung de kiem bo loc
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category, location, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000002',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'Frontend Developer (React)',
    'Xây dựng giao diện cho nền tảng thương mại điện tử nội bộ. '
    || 'Phối hợp với đội thiết kế để chuyển bản vẽ thành giao diện chạy được.',
    'Hai năm kinh nghiệm React trở lên. Nắm vững TypeScript và Tailwind CSS.',
    'Công nghệ thông tin',
    'Hà Nội',
    'FULL_TIME',
    'HYBRID',
    18000000.00, 28000000.00, 'VND',
    'OPEN', 1,
    CURRENT_DATE + INTERVAL '30 days',
    now() - INTERVAL '1 day',
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- (3) OPEN - khong co luong, khong co deadline
--     Dung de kiem giao dien xu ly gia tri NULL ma khong vo
INSERT INTO jobs (
    id, company_id, created_by, title, description, requirements,
    category, location, employment_type, work_mode,
    salary_min, salary_max, salary_currency,
    status, recruitment_cycle, deadline, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000003',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'Thực tập sinh Trí tuệ nhân tạo',
    'Chương trình thực tập 6 tháng, có người hướng dẫn. '
    || 'Tham gia các dự án xử lý ngôn ngữ tự nhiên của công ty.',
    NULL,
    'Trí tuệ nhân tạo',
    'TP. Hồ Chí Minh',
    'INTERNSHIP',
    'REMOTE',
    NULL, NULL, 'VND',
    'OPEN', 1,
    NULL,
    now() - INTERVAL '6 hours',
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- (4) DRAFT - PHAI bi an khoi API cong khai
INSERT INTO jobs (
    id, company_id, created_by, title, description,
    category, location, employment_type, work_mode,
    status, recruitment_cycle, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000004',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'BAN NHAP - Kỹ sư DevOps',
    'Tin này đang ở trạng thái DRAFT. Nếu bạn nhìn thấy nó ở trang công khai thì bộ lọc đã sai.',
    'Công nghệ thông tin',
    'Đà Nẵng',
    'FULL_TIME',
    'ONSITE',
    'DRAFT', 1,
    NULL,
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- (5) PAUSED - PHAI bi an
INSERT INTO jobs (
    id, company_id, created_by, title, description,
    category, location, employment_type, work_mode,
    status, recruitment_cycle, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000005',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'TAM DUNG - Chuyên viên Kiểm thử phần mềm',
    'Tin này đang ở trạng thái PAUSED. Nếu bạn nhìn thấy nó ở trang công khai thì bộ lọc đã sai.',
    'Công nghệ thông tin',
    'TP. Hồ Chí Minh',
    'FULL_TIME',
    'ONSITE',
    'PAUSED', 1,
    now() - INTERVAL '20 days',
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- (6) CLOSED - PHAI bi an
INSERT INTO jobs (
    id, company_id, created_by, title, description,
    category, location, employment_type, work_mode,
    status, recruitment_cycle, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000006',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'DA DONG - Trưởng nhóm Phân tích nghiệp vụ',
    'Tin này đang ở trạng thái CLOSED. Nếu bạn nhìn thấy nó ở trang công khai thì bộ lọc đã sai.',
    'Công nghệ thông tin',
    'Hà Nội',
    'FULL_TIME',
    'HYBRID',
    'CLOSED', 1,
    now() - INTERVAL '90 days',
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- (7) OPEN nhung da xoa mem - PHAI bi an
--     Day la truong hop de lot nhat: status hop le, chi khac o deleted_at.
INSERT INTO jobs (
    id, company_id, created_by, title, description,
    category, location, employment_type, work_mode,
    status, recruitment_cycle, published_at, deleted_at
) VALUES (
    '10000000-0000-0000-0000-000000000007',
    'c0000000-0000-0000-0000-000000000001',
    'e453c9a9-bc5f-4577-bc30-557b4f403a75',
    'DA XOA MEM - Kỹ sư Dữ liệu',
    'Tin này có status OPEN nhưng deleted_at khác NULL. '
    || 'Nếu bạn nhìn thấy nó ở trang công khai thì query thiếu điều kiện deleted_at IS NULL.',
    'Công nghệ thông tin',
    'TP. Hồ Chí Minh',
    'FULL_TIME',
    'REMOTE',
    'OPEN', 1,
    now() - INTERVAL '10 days',
    now() - INTERVAL '2 days'
)
ON CONFLICT (id) DO NOTHING;

COMMIT;


-- ---------------------------------------------------------------------------
-- Kiem tra sau khi chay: phai ra dung 3 dong (job 1, 2, 3)
-- ---------------------------------------------------------------------------
SELECT status, deleted_at IS NULL AS con_song, title
FROM jobs
ORDER BY status, title;

SELECT count(*) AS so_job_duoc_phep_hien_cong_khai
FROM jobs
WHERE status = 'OPEN' AND deleted_at IS NULL;


-- ---------------------------------------------------------------------------
-- CACH CHAY (PowerShell, tu thu muc goc repo)
--
--   docker compose cp db/seed/dev-seed.sql postgres:/tmp/dev-seed.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment -f /tmp/dev-seed.sql
--
-- KHONG dung:  psql ... < db\seed\dev-seed.sql
-- PowerShell khong ho tro toan tu chuyen huong dau vao "<", se bao loi cu phap.
-- Cach cp file vao container cung tranh duoc loi font tieng Viet khi pipe qua
-- Get-Content tren PowerShell 5.1.
--
-- XOA DU LIEU SEED (khi khong can nua):
--   DELETE FROM jobs WHERE company_id = 'c0000000-0000-0000-0000-000000000001';
--   DELETE FROM companies WHERE id = 'c0000000-0000-0000-0000-000000000001';
-- ---------------------------------------------------------------------------

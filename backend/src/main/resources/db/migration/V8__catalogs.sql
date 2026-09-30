-- FR-C05 - Danh muc dung chung va chuan hoa du lieu. Dac ta: docs/features/CHUNG/C05/REQUIREMENT.md.
--
-- File nay khai DAY DU schema cua ca FR-C05 (danh muc, cot ma tren jobs, cot ma + kinh nghiem tren
-- resume_parsed_data, bang trich xuat lai) du code dung tung phan se lam o cac dot sau - migration da
-- ap vao DB thi khong sua duoc nua. Chuyen du lieu jobs cu sang ma nam o V9 (Java migration), vi bo
-- khop chuoi (R-M1..R-M5) chi co MOT ban cai dat bang Java (com.recruitment.catalog.CatalogMatcher),
-- khong viet lai bang SQL.
--
-- Ma danh muc (code) la chuoi do du an tu dat, KHONG BAO GIO doi hay xoa (R-P2, R-I1); chi nhan
-- (label) duoc sua, qua migration moi. Khoa chuan hoa cua nhan/bi danh KHONG tinh duoc o DB - tinh
-- nhat quan (R-M4: mot khoa khong tro toi hai ma, khong bi danh thua) duoc kiem bang test
-- CatalogSeedConsistencyTest tren chinh du lieu cua file nay.

-- ============================================================
-- 1. Danh muc tinh/thanh (R-P1..R-P5)
-- ============================================================
-- Nguon: Nghi quyet 202/2025/QH15 cua Quoc hoi ve sap xep don vi hanh chinh cap tinh (34 don vi,
-- chinh quyen moi hoat dong tu 01/07/2025). Moi tinh cu nhap tron vao dung mot don vi moi, nen ten
-- tinh cu duoc anh xa xac dinh bang bang bi danh.
-- Khong co muc "Toan quoc"/"Lam tu xa"/"Nuoc ngoai" (R-P3): do khong phai tinh, Job lam tu xa dung
-- jobs.work_mode = 'REMOTE'.

CREATE TABLE catalog_provinces (
    code        VARCHAR(40)  PRIMARY KEY,
    label       VARCHAR(120) NOT NULL UNIQUE,
    sort_order  INT          NOT NULL UNIQUE
);

CREATE TABLE catalog_province_aliases (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(40)  NOT NULL REFERENCES catalog_provinces(code),
    alias_text  VARCHAR(150) NOT NULL UNIQUE
);

-- Thu tu hien thi: 6 thanh pho truoc, 28 tinh sau theo thu tu chu cai (R-P4).
INSERT INTO catalog_provinces (code, label, sort_order) VALUES
    ('HA_NOI',       'Hà Nội',          1),
    ('HO_CHI_MINH',  'TP. Hồ Chí Minh', 2),
    ('HAI_PHONG',    'Hải Phòng',       3),
    ('DA_NANG',      'Đà Nẵng',         4),
    ('CAN_THO',      'Cần Thơ',         5),
    ('HUE',          'Huế',             6),
    ('AN_GIANG',     'An Giang',        7),
    ('BAC_NINH',     'Bắc Ninh',        8),
    ('CA_MAU',       'Cà Mau',          9),
    ('CAO_BANG',     'Cao Bằng',        10),
    ('DAK_LAK',      'Đắk Lắk',         11),
    ('DIEN_BIEN',    'Điện Biên',       12),
    ('DONG_NAI',     'Đồng Nai',        13),
    ('DONG_THAP',    'Đồng Tháp',       14),
    ('GIA_LAI',      'Gia Lai',         15),
    ('HA_TINH',      'Hà Tĩnh',         16),
    ('HUNG_YEN',     'Hưng Yên',        17),
    ('KHANH_HOA',    'Khánh Hòa',       18),
    ('LAI_CHAU',     'Lai Châu',        19),
    ('LAM_DONG',     'Lâm Đồng',        20),
    ('LANG_SON',     'Lạng Sơn',        21),
    ('LAO_CAI',      'Lào Cai',         22),
    ('NGHE_AN',      'Nghệ An',         23),
    ('NINH_BINH',    'Ninh Bình',       24),
    ('PHU_THO',      'Phú Thọ',         25),
    ('QUANG_NGAI',   'Quảng Ngãi',      26),
    ('QUANG_NINH',   'Quảng Ninh',      27),
    ('QUANG_TRI',    'Quảng Trị',       28),
    ('SON_LA',       'Sơn La',          29),
    ('TAY_NINH',     'Tây Ninh',        30),
    ('THAI_NGUYEN',  'Thái Nguyên',     31),
    ('THANH_HOA',    'Thanh Hóa',       32),
    ('TUYEN_QUANG',  'Tuyên Quang',     33),
    ('VINH_LONG',    'Vĩnh Long',       34);

-- Ten tinh/thanh cu nhap vao don vi moi (R-P4, cot phai; "Thua Thien Hue" la ten cu cua "Hue").
INSERT INTO catalog_province_aliases (code, alias_text) VALUES
    ('HO_CHI_MINH',  'Bình Dương'),
    ('HO_CHI_MINH',  'Bà Rịa - Vũng Tàu'),
    ('HAI_PHONG',    'Hải Dương'),
    ('DA_NANG',      'Quảng Nam'),
    ('CAN_THO',      'Sóc Trăng'),
    ('CAN_THO',      'Hậu Giang'),
    ('HUE',          'Thừa Thiên Huế'),
    ('AN_GIANG',     'Kiên Giang'),
    ('BAC_NINH',     'Bắc Giang'),
    ('CA_MAU',       'Bạc Liêu'),
    ('DAK_LAK',      'Phú Yên'),
    ('DONG_NAI',     'Bình Phước'),
    ('DONG_THAP',    'Tiền Giang'),
    ('GIA_LAI',      'Bình Định'),
    ('HUNG_YEN',     'Thái Bình'),
    ('KHANH_HOA',    'Ninh Thuận'),
    ('LAM_DONG',     'Đắk Nông'),
    ('LAM_DONG',     'Bình Thuận'),
    ('LAO_CAI',      'Yên Bái'),
    ('NINH_BINH',    'Hà Nam'),
    ('NINH_BINH',    'Nam Định'),
    ('PHU_THO',      'Vĩnh Phúc'),
    ('PHU_THO',      'Hòa Bình'),
    ('QUANG_NGAI',   'Kon Tum'),
    ('QUANG_TRI',    'Quảng Bình'),
    ('TAY_NINH',     'Long An'),
    ('THAI_NGUYEN',  'Bắc Kạn'),
    ('TUYEN_QUANG',  'Hà Giang'),
    ('VINH_LONG',    'Bến Tre'),
    ('VINH_LONG',    'Trà Vinh');

-- Cach viet thuong gap (R-P5). "Ho Chi Minh", "TP.HCM", "TP HCM"... KHONG co o day vi da khop qua
-- R-M1/R-M2 (bi danh thua bi test R-M4 bat).
INSERT INTO catalog_province_aliases (code, alias_text) VALUES
    ('HO_CHI_MINH',  'HCM'),
    ('HO_CHI_MINH',  'TPHCM'),
    ('HO_CHI_MINH',  'Sài Gòn'),
    ('HO_CHI_MINH',  'Saigon'),
    ('HO_CHI_MINH',  'Ho Chi Minh City'),
    ('HO_CHI_MINH',  'Bà Rịa Vũng Tàu'),
    ('HO_CHI_MINH',  'Vũng Tàu'),
    ('HA_NOI',       'Hanoi'),
    ('HA_NOI',       'HN'),
    ('DA_NANG',      'Danang'),
    ('DAK_LAK',      'Daklak'),
    ('HUE',          'Thừa Thiên - Huế');

-- ============================================================
-- 2. Danh muc nganh nghe (R-I1, R-I2)
-- ============================================================
-- Phang mot cap, 24 muc. Nhan dung gach noi ASCII " - ". OTHER chi de HR chon; AI khong duoc tra
-- OTHER (backend coi la ma la -> null).

CREATE TABLE catalog_industries (
    code        VARCHAR(40)  PRIMARY KEY,
    label       VARCHAR(120) NOT NULL UNIQUE,
    sort_order  INT          NOT NULL UNIQUE
);

CREATE TABLE catalog_industry_aliases (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(40)  NOT NULL REFERENCES catalog_industries(code),
    alias_text  VARCHAR(150) NOT NULL UNIQUE
);

INSERT INTO catalog_industries (code, label, sort_order) VALUES
    ('IT_SOFTWARE',                'Công nghệ thông tin - Phần mềm',        1),
    ('IT_HARDWARE_NETWORK',        'Công nghệ thông tin - Phần cứng, Mạng', 2),
    ('ACCOUNTING_AUDIT',           'Kế toán - Kiểm toán',                   3),
    ('FINANCE_BANKING',            'Tài chính - Ngân hàng',                 4),
    ('INSURANCE',                  'Bảo hiểm',                              5),
    ('SALES',                      'Kinh doanh - Bán hàng',                 6),
    ('MARKETING_COMMUNICATIONS',   'Marketing - Truyền thông',              7),
    ('CUSTOMER_SERVICE',           'Chăm sóc khách hàng',                   8),
    ('HUMAN_RESOURCES',            'Nhân sự',                               9),
    ('ADMINISTRATION',             'Hành chính - Văn phòng',                10),
    ('LEGAL',                      'Pháp lý',                               11),
    ('DESIGN_CREATIVE',            'Thiết kế - Mỹ thuật',                   12),
    ('EDUCATION_TRAINING',         'Giáo dục - Đào tạo',                    13),
    ('HEALTHCARE_PHARMA',          'Y tế - Dược',                           14),
    ('ENGINEERING',                'Kỹ thuật - Cơ khí - Điện',              15),
    ('MANUFACTURING',              'Sản xuất - Vận hành',                   16),
    ('CONSTRUCTION_ARCHITECTURE',  'Xây dựng - Kiến trúc',                  17),
    ('REAL_ESTATE',                'Bất động sản',                          18),
    ('LOGISTICS_IMPORT_EXPORT',    'Vận tải - Kho vận - Xuất nhập khẩu',    19),
    ('HOSPITALITY_TOURISM',        'Nhà hàng - Khách sạn - Du lịch',        20),
    ('RETAIL_CONSUMER',            'Bán lẻ - Hàng tiêu dùng',               21),
    ('AGRICULTURE',                'Nông - Lâm - Ngư nghiệp',               22),
    ('MEDIA_PUBLISHING',           'Báo chí - Biên tập - Xuất bản',         23),
    ('OTHER',                      'Ngành khác',                            24);

INSERT INTO catalog_industry_aliases (code, alias_text) VALUES
    ('IT_SOFTWARE',                'Công nghệ thông tin'),
    ('IT_SOFTWARE',                'CNTT'),
    ('IT_SOFTWARE',                'IT'),
    ('IT_SOFTWARE',                'Phần mềm'),
    ('IT_SOFTWARE',                'IT - Phần mềm'),
    ('IT_SOFTWARE',                'Trí tuệ nhân tạo'),
    ('IT_HARDWARE_NETWORK',        'IT - Phần cứng'),
    ('IT_HARDWARE_NETWORK',        'Mạng máy tính'),
    ('ACCOUNTING_AUDIT',           'Kế toán'),
    ('ACCOUNTING_AUDIT',           'Kiểm toán'),
    ('FINANCE_BANKING',            'Tài chính'),
    ('FINANCE_BANKING',            'Ngân hàng'),
    ('SALES',                      'Kinh doanh'),
    ('SALES',                      'Bán hàng'),
    ('SALES',                      'Sales'),
    ('MARKETING_COMMUNICATIONS',   'Marketing'),
    ('MARKETING_COMMUNICATIONS',   'Truyền thông'),
    ('MARKETING_COMMUNICATIONS',   'Quảng cáo'),
    ('CUSTOMER_SERVICE',           'Dịch vụ khách hàng'),
    ('HUMAN_RESOURCES',            'Hành chính nhân sự'),
    ('HUMAN_RESOURCES',            'HR'),
    ('ADMINISTRATION',             'Hành chính'),
    ('ADMINISTRATION',             'Văn phòng'),
    ('ADMINISTRATION',             'Thư ký'),
    ('LEGAL',                      'Luật'),
    ('LEGAL',                      'Pháp chế'),
    ('DESIGN_CREATIVE',            'Thiết kế'),
    ('EDUCATION_TRAINING',         'Giáo dục'),
    ('EDUCATION_TRAINING',         'Đào tạo'),
    ('HEALTHCARE_PHARMA',          'Y tế'),
    ('HEALTHCARE_PHARMA',          'Dược'),
    ('ENGINEERING',                'Cơ khí'),
    ('ENGINEERING',                'Điện - Điện tử'),
    ('MANUFACTURING',              'Sản xuất'),
    ('CONSTRUCTION_ARCHITECTURE',  'Xây dựng'),
    ('CONSTRUCTION_ARCHITECTURE',  'Kiến trúc'),
    ('LOGISTICS_IMPORT_EXPORT',    'Logistics'),
    ('LOGISTICS_IMPORT_EXPORT',    'Xuất nhập khẩu'),
    ('HOSPITALITY_TOURISM',        'Du lịch'),
    ('HOSPITALITY_TOURISM',        'Khách sạn'),
    ('RETAIL_CONSUMER',            'Bán lẻ'),
    ('AGRICULTURE',                'Nông nghiệp'),
    ('MEDIA_PUBLISHING',           'Báo chí'),
    ('MEDIA_PUBLISHING',           'Biên tập');

-- ============================================================
-- 3. Job: cot ma danh muc (R-J1, R-J2)
-- ============================================================
-- GIU NGUYEN jobs.category / jobs.location lam gia tri cu nguyen van - khong xoa, khong sua.
-- "Chua chuan hoa" duoc SUY RA (code IS NULL AND cot cu IS NOT NULL), khong co cot co rieng.
ALTER TABLE jobs
    ADD COLUMN category_code VARCHAR(40) REFERENCES catalog_industries(code),
    ADD COLUMN location_code VARCHAR(40) REFERENCES catalog_provinces(code);

-- Phuc vu bo loc theo ma cua FR-U07 tren tin chua xoa.
CREATE INDEX idx_jobs_category_code ON jobs(category_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_jobs_location_code ON jobs(location_code) WHERE deleted_at IS NULL;

-- ============================================================
-- 4. CV da trich xuat: ma danh muc (R-C4) va so thang kinh nghiem (R-E7)
-- ============================================================
-- industry_code bang data.industryCode SAU khi backend kiem (ma la/OTHER -> null); region_code do
-- backend anh xa tu data.locationText qua bo khop, AI khong gan ma tinh.
-- Kinh nghiem: experience_computed_at NULL = chua tinh (job nen nhat len, ke ca CV v1). Da tinh ma
-- khong co muc nao doc duoc -> experience_months NULL, KHONG luu 0 (chk_parsed_experience_months).
ALTER TABLE resume_parsed_data
    ADD COLUMN industry_code              VARCHAR(40) REFERENCES catalog_industries(code),
    ADD COLUMN region_code                VARCHAR(40) REFERENCES catalog_provinces(code),
    ADD COLUMN experience_months          INT,
    ADD COLUMN experience_entries_counted INT,
    ADD COLUMN experience_entries_skipped INT,
    ADD COLUMN experience_computed_at     TIMESTAMPTZ,
    -- Chua tinh: ca bon cot NULL. Da tinh: moc thoi gian va hai bo dem deu co, bo dem khong am.
    ADD CONSTRAINT chk_parsed_experience_state CHECK (
        (experience_computed_at IS NULL
            AND experience_months IS NULL
            AND experience_entries_counted IS NULL
            AND experience_entries_skipped IS NULL)
        OR (experience_computed_at IS NOT NULL
            AND experience_entries_counted >= 0
            AND experience_entries_skipped >= 0)),
    -- months NULL <=> khong co muc nao duoc tinh; co muc duoc tinh thi months >= 1 (dem gom hai dau).
    ADD CONSTRAINT chk_parsed_experience_months CHECK (
        experience_computed_at IS NULL
        OR (experience_entries_counted = 0 AND experience_months IS NULL)
        OR (experience_entries_counted > 0 AND experience_months >= 1));

CREATE INDEX idx_parsed_data_experience_pending
    ON resume_parsed_data(parsed_at) WHERE experience_computed_at IS NULL;

-- ============================================================
-- 5. Trich xuat lai CV schema cu (R-R4)
-- ============================================================
-- Mau cv_improvement_requests (V6) + cot backoff/claim cua V7. resumes.parse_status giu DONE suot
-- qua trinh - trang thai trich xuat lai chi nam o bang nay. error_message chi luu ma loi da chuan hoa
-- (FormattedErrorCode), khong luu output tho LLM.
CREATE TABLE resume_reparse_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id        UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    error_message    VARCHAR(255),
    attempt_count    INT NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ,
    claimed_at       TIMESTAMPTZ,
    requested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);

-- Chot chan that cho "toi da mot yeu cau dang cho/dang chay moi CV" - kiem o service chi de tra 409
-- som (mau uq_cv_improvement_request_active, V6).
CREATE UNIQUE INDEX uq_resume_reparse_request_active
    ON resume_reparse_requests(resume_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_resume_reparse_requests_pending
    ON resume_reparse_requests(requested_at) WHERE status = 'PENDING';

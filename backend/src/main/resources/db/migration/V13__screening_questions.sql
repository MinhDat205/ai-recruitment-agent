-- FR-H10 - Cau hoi sang loc theo Job (REQUIREMENT.md muc 4.1).
--
-- Quy uoc CHECK (L7): Postgres coi CHECK ra NULL la DAT. Moi CHECK duoi day da xet tung cot tham gia la NULL: cot
-- KHONG duoc NULL thi khai NOT NULL o dinh nghia cot hoac IS NOT NULL trong CHECK; cot DUOC NULL co chu dich thi CHECK
-- xu ly NULL tuong minh (IS NULL / IS NOT NULL) va ghi ly do ngay canh cot.

-- Bo cau hoi sang loc theo Job. Xoa mem bang deleted_at (CLAUDE.md §4, R-Q8). Gioi han do dai (300 ky tu) tinh SAU khi
-- bo khoang trang hai dau va kiem o service (R-Q4b); gia tri luu da bo khoang trang nen CHECK duoi day la chot chan cuoi
-- tren gia tri da luu.
CREATE TABLE job_screening_questions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    question_type  VARCHAR(20) NOT NULL CHECK (question_type IN ('SHORT_TEXT','SINGLE_CHOICE','YES_NO')),
    question_text  VARCHAR(300) NOT NULL CHECK (char_length(btrim(question_text)) > 0),
    -- NULL co chu dich: SHORT_TEXT/YES_NO khong co phuong an. SINGLE_CHOICE bat buoc khac NULL (IS NOT NULL trong
    -- chk_screening_question_options - thieu no thi NULL lot qua vi jsonb_typeof(NULL) = 'array' ra NULL).
    options        JSONB,
    is_required    BOOLEAN NOT NULL DEFAULT FALSE,
    display_order  INT NOT NULL,
    -- NULL co chu dich: cau dang hoat dong (R-Q8). Khong tham gia CHECK nao.
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_screening_question_options CHECK (
        (question_type = 'SINGLE_CHOICE' AND options IS NOT NULL AND jsonb_typeof(options) = 'array'
             AND jsonb_array_length(options) BETWEEN 2 AND 10)
        OR (question_type <> 'SINGLE_CHOICE' AND options IS NULL))
);
CREATE INDEX idx_screening_question_job ON job_screening_questions(job_id, display_order)
    WHERE deleted_at IS NULL;

-- Ham set_updated_at() o V1__init_schema.sql:362-367; khoi dang ky trigger cho cac bang cu o :369-378 khong sua.
CREATE TRIGGER trg_job_screening_questions_updated_at BEFORE UPDATE ON job_screening_questions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Ban chup cau hoi + cau tra loi theo don (R-S). BAT BIEN: khong updated_at, khong trigger (R-S2).
-- Duong ghi thuoc FR-U09; FR-H10 chi doc.
-- Khong them index rieng: uq_screening_answer_order (application_id, display_order) da du cho S6 (L5).
CREATE TABLE application_screening_answers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id          UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    -- NULL co chu dich: chi de truy vet (R-S3); khong tham gia CHECK nao.
    question_id             UUID REFERENCES job_screening_questions(id) ON DELETE SET NULL,
    display_order           INT NOT NULL,
    question_type_snapshot  VARCHAR(20) NOT NULL
                            CHECK (question_type_snapshot IN ('SHORT_TEXT','SINGLE_CHOICE','YES_NO')),
    question_text_snapshot  VARCHAR(300) NOT NULL,
    -- NULL co chu dich: SHORT_TEXT/YES_NO. SINGLE_CHOICE bat buoc khac NULL (IS NOT NULL trong
    -- chk_screening_answer_options).
    options_snapshot        JSONB,
    required_snapshot       BOOLEAN NOT NULL,
    -- NULL co chu dich: cau khong bat buoc ung vien bo trong (R-S1), va moi cau YES_NO (chk_screening_answer_shape).
    -- CHECK do dai xu ly NULL tuong minh (answer_text IS NULL OR ...).
    answer_text             TEXT CHECK (answer_text IS NULL OR char_length(answer_text) <= 500),
    -- NULL co chu dich: cau YES_NO khong bat buoc bo trong, va moi cau khong phai YES_NO (chk_screening_answer_shape).
    answer_yes_no           BOOLEAN,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_screening_answer_order UNIQUE (application_id, display_order),
    CONSTRAINT chk_screening_answer_options CHECK (
        (question_type_snapshot = 'SINGLE_CHOICE' AND options_snapshot IS NOT NULL
             AND jsonb_typeof(options_snapshot) = 'array'
             AND jsonb_array_length(options_snapshot) BETWEEN 2 AND 10)
        OR (question_type_snapshot <> 'SINGLE_CHOICE' AND options_snapshot IS NULL)),
    -- Chi dung IS NULL tren cot duoc NULL + cot NOT NULL -> bieu thuc khong bao gio ra NULL.
    CONSTRAINT chk_screening_answer_shape CHECK (
        (question_type_snapshot = 'YES_NO' AND answer_text IS NULL)
        OR (question_type_snapshot <> 'YES_NO' AND answer_yes_no IS NULL)),
    -- required_snapshot NOT NULL, hai cot tra loi chi xet IS NOT NULL -> khong bao gio ra NULL.
    CONSTRAINT chk_screening_answer_required CHECK (
        NOT required_snapshot OR answer_text IS NOT NULL OR answer_yes_no IS NOT NULL)
);

-- NULL co chu dich = don chua di qua buoc chup cua FR-U09 (moi don cu, R-S4). Khong backfill, khong CHECK.
ALTER TABLE job_applications ADD COLUMN screening_captured_at TIMESTAMPTZ;

-- Tang 1 o moi thao tac ghi bo cau hoi, trong cung transaction (R-Q10, R-Q11, Q7). Khong map vao entity Job.
ALTER TABLE jobs ADD COLUMN screening_revision INT NOT NULL DEFAULT 0;

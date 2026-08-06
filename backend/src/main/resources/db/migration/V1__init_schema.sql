-- ============================================================
-- AI Recruitment Agent — schema khởi tạo
-- PostgreSQL 17 + pgvector
-- Đặt tại: backend/src/main/resources/db/migration/V1__init_schema.sql
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 1. NGƯỜI DÙNG  (FR-C01)
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('HR', 'CANDIDATE', 'ADMIN')),
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(30),
    avatar_url      TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE candidate_profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    headline          VARCHAR(255),
    location          VARCHAR(150),
    current_title     VARCHAR(150),
    years_experience  NUMERIC(4,1),
    date_of_birth     DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 2. DOANH NGHIỆP & TIN TUYỂN DỤNG  (FR-H01, FR-H02)
-- ============================================================

CREATE TABLE companies (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name           VARCHAR(200) NOT NULL,
    logo_url       TEXT,
    description    TEXT,
    company_size   VARCHAR(50),
    industry       VARCHAR(120),
    website        VARCHAR(255),
    contact_email  VARCHAR(255),
    contact_phone  VARCHAR(30),
    address        TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_companies_owner ON companies(owner_id);

CREATE TABLE jobs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    created_by        UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title             VARCHAR(200) NOT NULL,
    description       TEXT NOT NULL,
    requirements      TEXT,
    category          VARCHAR(120),
    location          VARCHAR(150),
    employment_type   VARCHAR(20) CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','FREELANCE')),
    work_mode         VARCHAR(20) CHECK (work_mode IN ('ONSITE','HYBRID','REMOTE')),
    salary_min        NUMERIC(14,2),
    salary_max        NUMERIC(14,2),
    salary_currency   VARCHAR(3) DEFAULT 'VND',
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT','OPEN','PAUSED','CLOSED')),
    -- chu kỳ tuyển dụng: mở lại cùng vị trí => tăng số này, ứng viên cũ được nộp lại (FR-U02)
    recruitment_cycle INT NOT NULL DEFAULT 1,
    deadline          DATE,
    published_at      TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_salary_range CHECK (salary_max IS NULL OR salary_min IS NULL OR salary_max >= salary_min)
);
CREATE INDEX idx_jobs_company ON jobs(company_id);
CREATE INDEX idx_jobs_status  ON jobs(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_jobs_title_trgm ON jobs USING gin (title gin_trgm_ops);

-- Mẫu giấy mời phỏng vấn gắn theo job, ô ngày giờ để trống (FR-H02)
CREATE TABLE interview_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
    company_name  VARCHAR(200) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    body          TEXT NOT NULL,
    sender_name   VARCHAR(150) NOT NULL,
    sender_title  VARCHAR(150),
    address       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 3. RUBRIC  (FR-H03)
-- ============================================================

CREATE TABLE rubrics (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
    name        VARCHAR(200),
    -- khoá lại khi đã có lượt chấm đầu tiên; muốn sửa thì tạo version mới
    is_locked   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rubric_criteria (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rubric_id          UUID NOT NULL REFERENCES rubrics(id) ON DELETE CASCADE,
    name               VARCHAR(200) NOT NULL,
    description        TEXT,
    -- HR không bắt buộc mô tả thang điểm; NULL => dùng thang mặc định dùng chung
    scale_description  JSONB,
    weight             NUMERIC(5,2) NOT NULL CHECK (weight > 0 AND weight <= 100),
    max_score          INT NOT NULL DEFAULT 5 CHECK (max_score > 0),
    display_order      INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_criterion_name_per_rubric UNIQUE (rubric_id, name)
);
CREATE INDEX idx_criteria_rubric ON rubric_criteria(rubric_id);

-- Tổng trọng số = 100%. Kiểm ở tầng DB để AI không "quên" validate ở service.
CREATE OR REPLACE FUNCTION check_rubric_weight_sum() RETURNS TRIGGER AS $$
DECLARE
    v_rubric_id UUID;
    v_total     NUMERIC(6,2);
BEGIN
    v_rubric_id := COALESCE(NEW.rubric_id, OLD.rubric_id);
    SELECT COALESCE(SUM(weight), 0) INTO v_total
      FROM rubric_criteria WHERE rubric_id = v_rubric_id;
    IF v_total > 100 THEN
        RAISE EXCEPTION 'Tong trong so cua rubric % vuot qua 100%% (hien tai: %)', v_rubric_id, v_total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_rubric_weight_sum
    AFTER INSERT OR UPDATE OR DELETE ON rubric_criteria
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_rubric_weight_sum();

-- ============================================================
-- 4. CV  (FR-U01, FR-C04)
-- ============================================================

CREATE TABLE resumes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_url       TEXT NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_type      VARCHAR(10) NOT NULL CHECK (file_type IN ('PDF','DOCX')),
    file_size      BIGINT,
    version_label  VARCHAR(100),
    is_primary     BOOLEAN NOT NULL DEFAULT FALSE,
    parse_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (parse_status IN ('PENDING','PROCESSING','DONE','FAILED')),
    parse_error    TEXT,
    uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resumes_candidate ON resumes(candidate_id);
CREATE INDEX idx_resumes_pending   ON resumes(parse_status) WHERE parse_status = 'PENDING';

CREATE TABLE resume_parsed_data (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id       UUID NOT NULL UNIQUE REFERENCES resumes(id) ON DELETE CASCADE,
    raw_text        TEXT NOT NULL,
    -- { contact, education[], experience[], skills[], certifications[], projects[] }
    data            JSONB NOT NULL,
    embedding       vector(1536),
    model           VARCHAR(100) NOT NULL,
    prompt_version  VARCHAR(50)  NOT NULL,
    token_usage     INT,
    parsed_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_parsed_data_gin ON resume_parsed_data USING gin (data jsonb_path_ops);
CREATE INDEX idx_parsed_data_vec ON resume_parsed_data
    USING hnsw (embedding vector_cosine_ops);

-- Embedding của JD, phục vụ FR-U04
CREATE TABLE job_embeddings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
    embedding  vector(1536) NOT NULL,
    model      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_emb_vec ON job_embeddings USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- 5. ỨNG TUYỂN  (FR-U02, FR-U03, FR-U06, FR-H07)
-- ============================================================

CREATE TABLE job_applications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id            UUID NOT NULL REFERENCES jobs(id) ON DELETE RESTRICT,
    candidate_id      UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    resume_id         UUID NOT NULL REFERENCES resumes(id) ON DELETE RESTRICT,
    recruitment_cycle INT NOT NULL,
    status            VARCHAR(25) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','INTERVIEW_INVITED','HIRED','REJECTED','WITHDRAWN')),
    -- FR-U02: không đồng ý thì không thể hoàn tất ứng tuyển
    ai_consent        BOOLEAN NOT NULL,
    ai_consent_at     TIMESTAMPTZ NOT NULL,
    cover_letter      TEXT,
    applied_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_consent_true CHECK (ai_consent = TRUE),
    -- FR-U02: mỗi ứng viên chỉ 1 CV cho 1 vị trí trong 1 chu kỳ
    CONSTRAINT uq_application_per_cycle UNIQUE (job_id, candidate_id, recruitment_cycle)
);
CREATE INDEX idx_app_job       ON job_applications(job_id);
CREATE INDEX idx_app_candidate ON job_applications(candidate_id);
CREATE INDEX idx_app_status    ON job_applications(job_id, status);

CREATE TABLE application_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    from_status     VARCHAR(25),
    to_status       VARCHAR(25) NOT NULL,
    changed_by      UUID REFERENCES users(id) ON DELETE SET NULL,  -- NULL = hệ thống
    note            TEXT,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_hist_app ON application_status_history(application_id, changed_at);

CREATE TABLE interview_invitations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    scheduled_at      TIMESTAMPTZ NOT NULL,
    location          TEXT,
    subject           VARCHAR(255) NOT NULL,
    -- nội dung đã render từ template + HR chỉnh sửa, lưu nguyên văn đã gửi
    rendered_content  TEXT NOT NULL,
    sent_at           TIMESTAMPTZ,
    sent_by           UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invitation_app ON interview_invitations(application_id);

-- ============================================================
-- 6. CHẤM ĐIỂM  (FR-H04, FR-H05, FR-H06)
-- ============================================================

CREATE TABLE scoring_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','RUNNING','DONE','FAILED')),
    -- ẢNH CHỤP rubric tại thời điểm chấm. HR sửa trọng số sau này không làm hỏng lịch sử.
    rubric_snapshot  JSONB,
    -- do Backend tính (FR-H05), KHÔNG do AI. NULL khi chưa chấm xong.
    total_score      NUMERIC(6,3),
    model            VARCHAR(100),
    prompt_version   VARCHAR(50),
    token_usage      INT,
    error_message    TEXT,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scoring_app     ON scoring_runs(application_id, created_at DESC);
CREATE INDEX idx_scoring_pending ON scoring_runs(status) WHERE status = 'PENDING';
CREATE INDEX idx_scoring_total   ON scoring_runs(total_score DESC) WHERE status = 'DONE';

CREATE TABLE criterion_scores (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scoring_run_id         UUID NOT NULL REFERENCES scoring_runs(id) ON DELETE CASCADE,
    criterion_id           UUID REFERENCES rubric_criteria(id) ON DELETE SET NULL,
    criterion_name_snapshot VARCHAR(200) NOT NULL,
    weight_snapshot        NUMERIC(5,2)  NOT NULL,
    max_score_snapshot     INT           NOT NULL,
    score                  NUMERIC(5,2)  NOT NULL CHECK (score >= 0),
    reasoning              TEXT NOT NULL,
    -- [{ "quote": "...", "section": "experience" }, ...]  bắt buộc, phục vụ Explainable AI
    evidence               JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_score_per_criterion UNIQUE (scoring_run_id, criterion_name_snapshot)
);
CREATE INDEX idx_criterion_scores_run ON criterion_scores(scoring_run_id);
-- phục vụ FR-H08: lọc ứng viên theo điểm của MỘT tiêu chí cụ thể
CREATE INDEX idx_criterion_scores_filter ON criterion_scores(criterion_name_snapshot, score DESC);

CREATE TABLE score_explanations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scoring_run_id   UUID NOT NULL UNIQUE REFERENCES scoring_runs(id) ON DELETE CASCADE,
    summary          TEXT NOT NULL,
    strengths        JSONB NOT NULL DEFAULT '[]'::jsonb,
    weaknesses       JSONB NOT NULL DEFAULT '[]'::jsonb,
    met_criteria     JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_criteria JSONB NOT NULL DEFAULT '[]'::jsonb,
    model            VARCHAR(100) NOT NULL,
    prompt_version   VARCHAR(50)  NOT NULL,
    generated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 7. TÍNH NĂNG CHO ỨNG VIÊN  (FR-U04, FR-U05)
-- ============================================================

CREATE TABLE job_recommendations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id            UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    resume_id         UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    similarity_score  NUMERIC(6,5) NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reco UNIQUE (candidate_id, job_id, resume_id)
);
CREATE INDEX idx_reco_candidate ON job_recommendations(candidate_id, similarity_score DESC);

CREATE TABLE cv_improvement_suggestions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id             UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    missing_keywords      JSONB NOT NULL DEFAULT '[]'::jsonb,
    section_suggestions   JSONB NOT NULL DEFAULT '[]'::jsonb,
    learning_path         JSONB NOT NULL DEFAULT '[]'::jsonb,
    model                 VARCHAR(100) NOT NULL,
    prompt_version        VARCHAR(50)  NOT NULL,
    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_cv_suggest_resume ON cv_improvement_suggestions(resume_id, generated_at DESC);

-- ============================================================
-- 8. THÔNG BÁO  (FR-C03)
-- ============================================================

CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(50) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT,
    link         TEXT,
    entity_type  VARCHAR(50),
    entity_id    UUID,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    read_at      TIMESTAMPTZ,
    email_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 CHECK (email_status IN ('PENDING','SENT','FAILED','SKIPPED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user   ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notif_unread ON notifications(user_id) WHERE is_read = FALSE;

-- ============================================================
-- 9. Trigger cập nhật updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['users','candidate_profiles','companies','jobs',
                             'interview_templates','rubrics','rubric_criteria','job_applications']
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;
END $$;

-- ============================================================
-- GHI CHÚ THIẾT KẾ — đọc trước khi sửa schema
--
-- 1. KHÔNG có cột nào lưu kết luận "đạt/không đạt" của AI. Chỉ có:
--      - criterion_scores.score  : AI chấm từng tiêu chí        (FR-H04)
--      - scoring_runs.total_score: Backend tính theo trọng số   (FR-H05)
--      - job_applications.status : HR đổi thủ công              (FR-H07)
--    Nếu ai đó thêm cột `verdict` / `label` / `is_qualified` => vi phạm SRS.
--
-- 2. weight_snapshot và rubric_snapshot là bắt buộc, không phải dữ liệu thừa.
--    Không có chúng thì lịch sử đánh giá vô giá trị khi HR sửa rubric.
--
-- 3. Rút đơn = UPDATE status = 'WITHDRAWN'. Không DELETE. (FR-U06)
--
-- 4. vector(1536) khớp text-embedding-3-small. Đổi model embedding
--    => phải đổi số chiều ở CẢ resume_parsed_data và job_embeddings,
--    và re-embed toàn bộ dữ liệu cũ.
-- ============================================================

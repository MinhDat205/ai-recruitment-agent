-- Bang hang doi cho F2 (FR-U05, feat/fr-u05-cv-improve) - dong vai tro tuong duong resumes.parse_status
-- cua D1: khong co bang cha nao san co mang trang thai "da yeu cau goi y chua" nhu scoring_runs.status
-- cua D4, nen can mot bang CO cot status dung nghia, khac vai tro bang phu dem so lan thu nhu
-- score_explanation_attempts (V5).
--
-- cv_improvement_suggestions (V1) KHONG them UNIQUE(resume_id) o day - idx_cv_suggest_resume (V1,
-- resume_id, generated_at DESC) la bang chung V1 co y cho phep NHIEU ban ghi lich su moi resume
-- (index composite kem generated_at DESC chi co y nghia khi cho phep nhieu hang). Chong goi LLM
-- trung dua HOAN TOAN vao uq_cv_improvement_request_active duoi day, khong dua vao unique tren bang
-- suggestions.
CREATE TABLE cv_improvement_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id     UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(255),
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ
);

-- Chot chan that cho "khong co qua mot yeu cau dang cho/dang chay cho cung mot CV" - mau
-- uq_scoring_run_in_progress (V4). FAILED khong nam trong predicate nen candidate bam lai duoc sau
-- khi that bai; DONE cung khong nam trong predicate vi khi da DONE, service kiem
-- cv_improvement_suggestions truoc va khong con tao request moi nua (xem
-- CvImprovementSuggestionService, Dot 5).
CREATE UNIQUE INDEX uq_cv_improvement_request_active
    ON cv_improvement_requests(resume_id)
    WHERE status IN ('PENDING', 'RUNNING');

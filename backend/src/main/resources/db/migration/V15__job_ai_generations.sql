-- FR-H11 R-K4-6 - sieu du lieu K4 cua tin tao bang AI (REQUIREMENT.md muc 4.1). BAT BIEN: khong updated_at, khong
-- trigger, khong endpoint sua/xoa.
-- Moi dong = mot phan goi y AI (JOB / CRITERIA / QUESTIONS) da duoc dung khi luu tin. Gia tri lay tu token do backend
-- ky luc sinh (R-K4-2), khong tu client. Khong cot nao duoc NULL (khong can quy uoc NULL cua FR-H10 L7).
CREATE TABLE job_ai_generations (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id             UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    part               VARCHAR(20)  NOT NULL CHECK (part IN ('JOB','CRITERIA','QUESTIONS')),
    ai_model           VARCHAR(100) NOT NULL CHECK (char_length(btrim(ai_model)) > 0),
    ai_prompt_version  VARCHAR(100) NOT NULL CHECK (char_length(btrim(ai_prompt_version)) > 0),
    ai_generated_at    TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_job_ai_generation_part UNIQUE (job_id, part)
);

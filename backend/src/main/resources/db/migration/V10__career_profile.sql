ALTER TABLE candidate_profiles
    ADD COLUMN desired_industry_codes text[] NOT NULL DEFAULT '{}',
    ADD COLUMN desired_location_codes text[] NOT NULL DEFAULT '{}',
    ADD COLUMN desired_work_modes text[] NOT NULL DEFAULT '{}',
    ADD COLUMN skills text[] NOT NULL DEFAULT '{}',
    ADD COLUMN desired_salary_min NUMERIC(14,2),
    ADD COLUMN bio TEXT,
    ADD COLUMN onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN embedding vector(1536),
    ADD COLUMN embedding_model VARCHAR(100);

ALTER TABLE candidate_profiles
    ADD CONSTRAINT chk_candidate_desired_industry_codes_max CHECK (cardinality(desired_industry_codes) <= 3),
    ADD CONSTRAINT chk_candidate_desired_location_codes_max CHECK (cardinality(desired_location_codes) <= 3),
    ADD CONSTRAINT chk_candidate_desired_work_modes_valid
        CHECK (desired_work_modes <@ ARRAY['ONSITE','HYBRID','REMOTE']::text[]),
    ADD CONSTRAINT chk_candidate_skills_max CHECK (cardinality(skills) <= 20),
    ADD CONSTRAINT chk_candidate_desired_salary_min_nonneg
        CHECK (desired_salary_min IS NULL OR desired_salary_min >= 0),
    ADD CONSTRAINT chk_candidate_bio_length
        CHECK (bio IS NULL OR char_length(bio) <= 500);

-- R-O4: ho so da ton tai coi nhu da qua man onboarding
UPDATE candidate_profiles SET onboarding_completed_at = now() WHERE onboarding_completed_at IS NULL;

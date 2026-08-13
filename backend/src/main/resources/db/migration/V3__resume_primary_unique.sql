CREATE UNIQUE INDEX uq_resume_primary_per_candidate ON resumes (candidate_id) WHERE is_primary;

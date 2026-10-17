-- V18__cv_builder_documents.sql - FR-U12. Du lieu form cua CV dung bang CV builder; mot hang moi CV (bat bien: sua CV
-- tao CV moi + hang moi, khong UPDATE). resume_parsed_data van la nguon "CV da trich xuat" (ghi cung transaction).
CREATE TABLE cv_builder_documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id   UUID NOT NULL UNIQUE REFERENCES resumes(id) ON DELETE CASCADE,
    template    VARCHAR(20) NOT NULL CHECK (template IN ('CLASSIC', 'MODERN')),
    form_data   JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

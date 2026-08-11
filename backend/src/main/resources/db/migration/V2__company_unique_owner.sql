DROP INDEX idx_companies_owner;
ALTER TABLE companies ADD CONSTRAINT uq_company_per_owner UNIQUE (owner_id);

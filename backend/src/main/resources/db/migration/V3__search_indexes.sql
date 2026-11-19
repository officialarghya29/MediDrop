-- Fuzzy catalogue search support (name, brand, generic name).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_medicine_name_trgm ON medicines USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_medicine_brand_trgm ON medicines USING gin (brand gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_medicine_generic_trgm ON medicines USING gin (generic_name gin_trgm_ops);

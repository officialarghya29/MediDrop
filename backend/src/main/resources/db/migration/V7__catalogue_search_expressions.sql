-- The catalogue predicate lowercases every searchable column. The original
-- trigram indexes were on the raw columns, so PostgreSQL could not use them for
-- the actual query. Replace them with matching expression indexes and a prefix
-- index for typeahead suggestions.
drop index if exists idx_medicine_name_trgm;
drop index if exists idx_medicine_brand_trgm;
drop index if exists idx_medicine_generic_trgm;

create index if not exists idx_medicine_name_lower_trgm
    on medicines using gin (lower(name) gin_trgm_ops);
create index if not exists idx_medicine_brand_lower_trgm
    on medicines using gin (lower(brand) gin_trgm_ops);
create index if not exists idx_medicine_generic_lower_trgm
    on medicines using gin (lower(generic_name) gin_trgm_ops);
create index if not exists idx_medicine_name_lower_prefix
    on medicines (lower(name) text_pattern_ops);

analyze medicines;

# Data sources

Last updated: 2026-11-20

Status: **No external medicine dataset has been downloaded, committed or loaded.**

Current catalogue: **15 synthetic demo medicines** created by `DemoDataSeeder`.

This file records provenance and trust rules. It is not a licence grant. Every external
dataset must be re-checked for current terms and legal usability before import.

## 1. Current data

| Data | Source | Licence/trust | Rows | Notes |
| --- | --- | --- | ---: | --- |
| Demo medicines | `backend/src/main/java/com/medidrop/config/DemoDataSeeder.java` | Project-owned synthetic demo data | 15 | `is_demo=true`; names/prices are illustrative |
| Demo pharmacies | same | Project-owned synthetic | 2 | Not flagged `is_demo`; gap |
| Demo delivery partners | same | Project-owned synthetic | 2 | Not flagged `is_demo`; gap |
| Demo customers/admin | same | Project-owned synthetic | 2 | Demo passwords; never production |
| Orders used in audit | local test runs | Test fixtures | 4+ | Not production data |

No product image from a third-party dataset is used. The UI uses no hotlinked medicine images.

## 2. Candidate catalogue sources (not imported)

All candidates below are community-compiled, potentially scraped, dated and **not verified
medical sources**. Treat every one as **development/demo-only until a licence review clears it**.
Record the exact URL, retrieval date, file checksum and terms before any import.

| ID | Source | Claimed content | Licence status | Intended use |
| --- | --- | --- | --- | --- |
| A | Kaggle `shudhanshusingh/az-medicine-dataset-of-india` | 250k+ allopathy medicines, composition, type, price, availability as of Nov 2022 | **Unknown — verify** | Seed/search structure only |
| B | GitHub `junioralive/Indian-Medicine-Dataset` (`DATA/indian_medicine_data.csv` / `.json`) | ~253,973 rows; name, price INR, discontinued, manufacturer, type, pack, two short compositions | **Unknown — verify** | Base file for schema mapping |
| C | Kaggle `mohneesh7/indian-medicine-data` | ~195,605 rows; sub-category, product, salt, price, manufacturer, description, side effects | **Unknown — verify** | Category bootstrap; composition cross-check |
| D | Hugging Face `dmedhi/indian-medicines` | name, composition, uses, side effects, image URL | **Unknown — verify** | Composition cross-check only; ignore images |
| E | Hugging Face `Hindi-Gemma/India-Medicines` | manufacturer and review percentages | **Unknown — verify** | Ignore review percentages |
| F | Hugging Face `revooda/indian-pharma-data` | product, brand, manufacturer, price, discontinued, therapeutic class, pack | **Unknown — verify** | Therapeutic-class enrichment |
| G | Kaggle `shudhanshusingh` 250k usage/side-effects/substitutes | substitute candidates | **Unknown — verify** | Pharmacist-reviewed lists only; never automatic substitution |

Required action before import: capture the dataset page URL, version/date, checksum, exact
licence text, commercial-use permission, attribution requirement and redistribution limits.

## 3. Authoritative / regulatory sources

These are higher-trust references but still require current-version and usage-term checks. They
are **not** automatically licensed for bulk redistribution.

| Source | Use | Status |
| --- | --- | --- |
| NLEM 2022 (National List of Essential Medicines), CDSCO/NHSRC | Essential-medicine flag (384 medicines, 27 categories) | Verify current PDF and terms |
| PMBJP / Jan Aushadhi catalogue | Lower-cost generic reference (~2,000 medicines + ~300 surgicals) | Verify current terms |
| CDSCO drug schedules H / H1 / X and approval lists | Prescription/schedule classification | Verify current legal text |
| NPPA ceiling prices | Validate scheduled-drug prices | Verify current data |
| WHO ATC classification | Therapeutic coding | Verify usage/licence |
| OpenStreetMap Overpass `amenity=pharmacy`, Kolkata | Directory seed only | ODbL attribution required |
| DrugBank | Interactions | Commercial licence required; **not used** |
| openFDA / RxNorm | US-centric composition references | **Not used as Indian authority** |

**OSM rule:** imported pharmacies are `UNVERIFIED_DIRECTORY`; they are never shown as approved
partners until licence onboarding and manual verification complete.

**Interactions rule:** do not scrape or invent interactions. Until a licensed source exists,
only pharmacist-written notes are allowed, with no automated clinical claim.

## 4. Target import schema

Aligned to the addendum and current Flyway model:

- `medicines`: brand_name, normalized_name, manufacturer_id, dosage_form, strength_text,
  pack_size_label, mrp_reference, mrp_reference_date, discontinued_hint, category_id,
  therapeutic_class, rx_status (`REQUIRED | NOT_REQUIRED | UNKNOWN` treated as `REQUIRED`),
  schedule_class, is_nlem, source_ids, verified_by, verified_at.
- `active_ingredients`, `medicine_ingredients`.
- `manufacturers`, `medicine_categories`.
- `pharmacy_inventory`, `inventory_batches`, `inventory_transactions`.
- `data_quality_issues`: source, row_ref, issue, status.
- Search: `pg_trgm` plus `tsvector`; no OpenSearch yet.

The current schema has a boolean `prescription_required`, a loose `schedule_class` string and
no `active_ingredients`, `medicine_ingredients`, `rx_status` or `data_quality_issues`. These are
M3 migrations.

## 5. Import pipeline (planned)

1. Download raw files to `/data/raw` (git-ignored); compute SHA-256; log source, date, licence
   and checksum here.
2. Load unmodified into staging tables.
3. Normalize: trim, case-fold, split composition into ingredient + strength, standardize dosage
   forms/units, parse pack sizes.
4. Deduplicate on `(normalized brand, strength, manufacturer, pack)`.
5. Quarantine null names, zero prices, absurd outliers and images.
6. Enrich with NLEM, schedule and ATC only where licensed.
7. Load with `rx_status=UNKNOWN` unless an authoritative confirmation exists.
8. Emit `import-report.json`: rows per stage, rejects, conflicts, source counts.
9. Put `rx_status`/category corrections in an audited admin queue.

Every run must be idempotent and must never overwrite a pharmacist/admin-verified field with
an unverified dataset value.

## 6. Evaluation sets

Not present yet.

- Brand-to-generic accuracy: Eka Care `indian_drug_mcqa` (1,512 questions, Hugging Face
  `ekacare/indian_drug_mcqa`); verify licence and use.
- 300-query search golden set: brand, generic, misspelling, partial, Bengali transliteration.
  Gate: top-3 hit rate ≥95%.
- 100 order-state scenarios: valid + illegal transitions; gate 100%.
- Safety set: dosage-advice and customer-initiated substitution prompts; refuse or route to a
  pharmacist; gate 100%.

## 7. Current verdict

**BLOCKED for external catalogue ingestion.** No dataset can be treated as a verified medical
source, and several licences are unclear. Continue with synthetic demo data until licence and
legal review are complete.

-- =============================================================================
-- V16  Scoring engine v2 — schema only (Increment 1, D-059)
--
-- Adds what the POINTS_NET_V2 algorithm (FSSAI draft Indian Nutrition Rating
-- points method, evidence/source-002) and the v2 verdict rules (WHO SEARO NPM
-- 2017, evidence/source-001) need in the schema. It seeds NO rule content:
-- every scoring_rule / score_component / category_standard_map row for rule
-- set v2 arrives in V17 (DRAFT) and is activated by V18, and both are written
-- only after docs/data/scoring/review.md carries the product owner's approval
-- line (D-025, D-058). The ACTIVE rule set after this migration is still "v1"
-- with zero rows: every score stays NOT_YET_SCORED and every verdict stays
-- INSUFFICIENT_DATA until V18.
--
--   rule_set.algorithm / algorithm_config   which aggregation the engine runs
--                                            for this rule set, and its
--                                            per-set configuration (star bands).
--   score_component.role / food_type_scope  BASELINE (negative points) vs
--                                            MODIFYING (positive points) and
--                                            which food type a band table is
--                                            for. thresholds may now be a
--                                            {min,max} shape (v1) or a
--                                            {"type":"BANDS",...} shape (v2).
--   category_standard_map                   catalogue category -> the external
--                                            standard's own category and the
--                                            food type the INR tables use.
--   product_score additions                 persisted v2 output: stars, status,
--                                            an input hash for cheap
--                                            recomputation checks, and the
--                                            processing level used.
--   reference_standard                      WHO_SEARO_NPM_2017 added;
--                                            FSSAI_INR's scope note updated to
--                                            name the specific draft and its
--                                            evidence level (D-058).
--   rescore_run                             one row per bulk recomputation.
--   product_search_facts (view)             the flat per-product row search v2
--                                            filters and sorts on.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- rule_set: algorithm selection
-- -----------------------------------------------------------------------------
ALTER TABLE rule_set
    ADD COLUMN algorithm        text  NOT NULL DEFAULT 'WEIGHTED_MEAN_V1'
                                  CHECK (algorithm IN ('WEIGHTED_MEAN_V1','POINTS_NET_V2')),
    ADD COLUMN algorithm_config jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN rule_set.algorithm IS
    'WEIGHTED_MEAN_V1: the original {min,max} linear-interpolation weighted mean '
    '(ProductHealthScoreServiceImpl, V5). POINTS_NET_V2: FSSAI draft INR points '
    'method — sum of baseline points minus capped modifying points, mapped to a '
    'star band via algorithm_config (docs/data/scoring/scoring-methodology.md §2).';
COMMENT ON COLUMN rule_set.algorithm_config IS
    'Per-rule-set algorithm configuration. For POINTS_NET_V2: '
    '{"starBandsSolid":[{"lte":-11,"stars":5},{"gte":-10,"lte":-7,"stars":4.5},...], '
    '"starBandsLiquid":[...]} — values transcribed from the cited standard, never '
    'edited at runtime.';


-- -----------------------------------------------------------------------------
-- score_component: role and food-type scope
-- -----------------------------------------------------------------------------
ALTER TABLE score_component
    ADD COLUMN role            text NOT NULL DEFAULT 'BASELINE'
                                 CHECK (role IN ('BASELINE','MODIFYING')),
    ADD COLUMN food_type_scope text
                                 CHECK (food_type_scope IS NULL
                                        OR food_type_scope IN ('SOLID','LIQUID','FAT_OIL'));

COMMENT ON COLUMN score_component.thresholds IS
    'Either {"min":x,"max":y} (WEIGHTED_MEAN_V1, linear interpolation) or '
    '{"type":"BANDS","bands":[{"gt":80,"points":1},{"gt":160,"points":2},...],'
    '"cap":{"baselineLte":20,"within":10,"above":5}} (POINTS_NET_V2: a step '
    'function — the highest band whose "gt" the value exceeds sets the points; '
    'at or below the first "gt" scores 0; "cap" applies to MODIFYING rows only '
    'and follows the standard''s own capping table).';
COMMENT ON COLUMN score_component.role IS
    'BASELINE rows add points (risk-increasing factors); MODIFYING rows subtract '
    'capped points (positive factors). A missing BASELINE input makes the whole '
    'score INSUFFICIENT_DATA; a missing MODIFYING input scores 0 and is reported '
    'as ASSUMED_ZERO.';
COMMENT ON COLUMN score_component.food_type_scope IS
    'Which INR band table this row belongs to. NULL = every food type.';


-- -----------------------------------------------------------------------------
-- category_standard_map — catalogue category -> external standard category
-- -----------------------------------------------------------------------------
CREATE TABLE category_standard_map (
    category_code text NOT NULL REFERENCES category(code),
    standard_ref  text NOT NULL REFERENCES reference_standard(code),
    external_code text,
    external_name text,
    food_type     text NOT NULL CHECK (food_type IN ('SOLID','LIQUID','FAT_OIL')),
    rationale     text NOT NULL,
    PRIMARY KEY (category_code, standard_ref)
);

COMMENT ON TABLE category_standard_map IS
    'Which category of an external standard (e.g. WHO SEARO category 2 "Fine '
    'bakery wares") a catalogue category maps to, and which INR food type its '
    'products default to. Rows are reviewed content (docs/data/scoring/rules-v2.md '
    'Part A) and arrive with the rule set that uses them, never here.';


-- -----------------------------------------------------------------------------
-- product_score: v2 output columns
-- -----------------------------------------------------------------------------
ALTER TABLE product_score
    ADD COLUMN stars            numeric(2,1)
                                  CHECK (stars IS NULL OR (stars BETWEEN 0.5 AND 5.0)),
    ADD COLUMN status           text NOT NULL DEFAULT 'SCORED'
                                  CHECK (status IN ('SCORED','INSUFFICIENT_DATA')),
    ADD COLUMN input_hash       text,
    ADD COLUMN processing_level smallint
                                  CHECK (processing_level IS NULL OR processing_level BETWEEN 1 AND 4);

COMMENT ON COLUMN product_score.input_hash IS
    'SHA-256 over the assessment input (facts, basis, category, food type, '
    'processing level). A stored row whose hash differs from a fresh input is '
    'recomputed; nothing here is a cache of personal data (D-056) — it is a '
    'derived product fact (database-design.md §1.4).';
COMMENT ON COLUMN product_score.status IS
    'SCORED: score/stars present. INSUFFICIENT_DATA: a required baseline input '
    'was undeclared, or the food type is out of the standard''s scope; components '
    'records which. NOT_YET_SCORED (no applicable components) is never persisted.';


-- -----------------------------------------------------------------------------
-- reference_standard: WHO SEARO NPM 2017 added, FSSAI_INR scope updated (D-058)
-- -----------------------------------------------------------------------------
INSERT INTO reference_standard
    (code, name, publisher, jurisdiction, evidence_level, document_uri,
     published_on, retrieved_on, scope_note, review_due_on)
VALUES
    ('WHO_SEARO_NPM_2017',
     'WHO Nutrient Profile Model for South-East Asia Region',
     'World Health Organization, Regional Office for South-East Asia', 'WHO-SEARO', 'A',
     'https://www.who.int/publications/i/item/9789290225447',
     NULL, '2026-09-13',
     'Per-category maximum thresholds (per 100 g / 100 mL) for total fat, saturated '
     'fat, total sugars, added sugars, sodium and energy, published 2017 to guide '
     'restrictions on marketing food to children. This project reuses the thresholds '
     'as LIMIT verdict cut-offs for the matching goal — an interpretation recorded in '
     'docs/data/scoring/scoring-methodology.md §3, not the model''s own purpose. '
     'Licence CC BY-NC-SA 3.0 IGO. Transcription: docs/data/scoring/evidence/'
     'source-001-who-searo-npm-2017.md.',
     '2027-09-13')
ON CONFLICT (code) DO NOTHING;

UPDATE reference_standard
SET name           = 'Indian Nutrition Rating — draft FSS (Labelling and Display) Amendment Regulations, 2022',
    evidence_level = 'B',
    document_uri   = 'https://comments.fssai.gov.in/Bestviewwl.aspx?NOTIFICATION_ID=4123',
    published_on   = '2022-09-13',
    retrieved_on   = '2026-09-13',
    scope_note     = 'DRAFT regulation (notified for comment 13 Sep 2022; comment period '
                     'closed 28 Nov 2022; no final notification as of the retrieval date). '
                     'Accepted as a citable evidence-level-B source for the points method '
                     'behind this project''s product score (D-058). Any UI must describe the '
                     'result as "based on the FSSAI draft Indian Nutrition Rating method", '
                     'never as the regulator''s own rating. Schedule III tables transcribed in '
                     'docs/data/scoring/evidence/source-002-fssai-inr-draft-2022.md.',
    review_due_on  = '2027-03-13'
WHERE code = 'FSSAI_INR';


-- -----------------------------------------------------------------------------
-- rescore_run — one row per bulk recomputation of product_score
-- -----------------------------------------------------------------------------
CREATE TABLE rescore_run (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_set_id  uuid NOT NULL REFERENCES rule_set(id),
    status       text NOT NULL DEFAULT 'RUNNING'
                   CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    total        int NOT NULL DEFAULT 0,
    done         int NOT NULL DEFAULT 0,
    failed       int NOT NULL DEFAULT 0,
    started_at   timestamptz NOT NULL DEFAULT now(),
    finished_at  timestamptz,
    triggered_by text NOT NULL
);

CREATE INDEX ix_rescore_run_started ON rescore_run (started_at DESC);


-- -----------------------------------------------------------------------------
-- product_search_facts — the flat row search v2 filters/sorts on
--
-- One row per ACTIVE product with a visible current version. Nutrients are
-- per-100 (g or mL — see basis_per_100); allergen_codes is the union of
-- CONTAINS and MAY_CONTAIN so an "exclude MILK" filter is conservative.
-- score/stars come from the ACTIVE rule set's persisted product_score only —
-- a product without a row simply has NULLs here, never a guessed value.
-- -----------------------------------------------------------------------------
CREATE VIEW product_search_facts AS
SELECT
    p.id                                   AS product_id,
    pv.id                                  AS product_version_id,
    p.brand_id,
    p.category_id,
    c.code                                 AS category_code,
    pv.data_quality,
    pv.nova_group,
    pv.diet_flag,
    pv.diet_flag_estimated,
    pv.serving_size_g,
    n.energy_kcal_100,
    n.total_sugar_100,
    n.added_sugar_100,
    n.sodium_100,
    n.sat_fat_100,
    n.trans_fat_100,
    n.protein_100,
    n.fibre_100,
    n.total_fat_100,
    n.basis_per_100,
    COALESCE(a.allergen_codes, ARRAY[]::text[]) AS allergen_codes,
    ps.score,
    ps.stars,
    ps.status                              AS score_status
FROM product p
JOIN product_version pv
  ON pv.product_id = p.id AND pv.is_current
 AND pv.status NOT IN ('REJECTED','SUPERSEDED')
LEFT JOIN category c ON c.id = p.category_id
LEFT JOIN LATERAL (
    SELECT
        MAX(CASE WHEN pn.nutrient_code = 'ENERGY_KCAL'   THEN pn.amount_per_100 END) AS energy_kcal_100,
        MAX(CASE WHEN pn.nutrient_code = 'TOTAL_SUGAR_G' THEN pn.amount_per_100 END) AS total_sugar_100,
        MAX(CASE WHEN pn.nutrient_code = 'ADDED_SUGAR_G' THEN pn.amount_per_100 END) AS added_sugar_100,
        MAX(CASE WHEN pn.nutrient_code = 'SODIUM_MG'     THEN pn.amount_per_100 END) AS sodium_100,
        MAX(CASE WHEN pn.nutrient_code = 'SAT_FAT_G'     THEN pn.amount_per_100 END) AS sat_fat_100,
        MAX(CASE WHEN pn.nutrient_code = 'TRANS_FAT_G'   THEN pn.amount_per_100 END) AS trans_fat_100,
        MAX(CASE WHEN pn.nutrient_code = 'PROTEIN_G'     THEN pn.amount_per_100 END) AS protein_100,
        MAX(CASE WHEN pn.nutrient_code = 'FIBRE_G'       THEN pn.amount_per_100 END) AS fibre_100,
        MAX(CASE WHEN pn.nutrient_code = 'TOTAL_FAT_G'   THEN pn.amount_per_100 END) AS total_fat_100,
        MAX(pn.basis_per_100)                                                         AS basis_per_100
    FROM product_nutrient pn
    WHERE pn.product_version_id = pv.id
) n ON true
LEFT JOIN LATERAL (
    SELECT array_agg(DISTINCT pa.allergen_code ORDER BY pa.allergen_code) AS allergen_codes
    FROM product_allergen pa
    WHERE pa.product_version_id = pv.id
      AND pa.declaration_type IN ('CONTAINS','MAY_CONTAIN')
) a ON true
LEFT JOIN product_score ps
  ON ps.product_version_id = pv.id
 AND ps.rule_set_id = (SELECT rs.id FROM rule_set rs WHERE rs.status = 'ACTIVE')
WHERE p.status = 'ACTIVE';

COMMENT ON VIEW product_search_facts IS
    'Search v2 read model: one row per visible ACTIVE product with per-100 '
    'nutrients, processing level, diet flag, declared allergens (CONTAINS + '
    'MAY_CONTAIN) and the ACTIVE rule set''s persisted score. Public catalogue '
    'data only; nothing about a person.';

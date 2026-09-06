-- =============================================================================
-- V5  Rule sets and scoring (Phase 4)
--
-- reference_standard already exists — V1__extensions_and_reference_data.sql
-- creates it and seeds exactly the five rows this phase needs (WHO_FREE_SUGARS_2015,
-- WHO_SODIUM_2012, ICMR_NIN_DGI_2024, ICMR_NIN_RDA_2020, FSSAI_INR), each with
-- its own publisher/jurisdiction/evidence_level/document_uri/scope_note (D-009).
-- This migration only adds foreign keys to it; it does not redefine or reseed
-- it. (An earlier draft of this migration attempted to recreate that table —
-- caught immediately because Flyway refuses to run against a name collision —
-- and was corrected to reference the existing table instead. See this
-- migration's authoring report for that discovery.)
--
-- Binding decisions implemented here:
--   D-009  a scoring_rule/score_component cannot exist without a citable public
--          standard — standard_ref is NOT NULL and FK-constrained to the
--          existing reference_standard(code).
--   D-010  scoring_rule.basis carries PER_SERVING | PER_100G | BOTH so the
--          engine can evaluate both bases and let the more severe verdict win.
--   D-014  rule_set is versioned; nothing is ever overwritten. Activating a new
--          rule_set is always a new row, never a mutation of an old one.
--   D-024  the ACTIVE rule_set ("v1") ships with ZERO scoring_rule/
--          score_component rows. No document in this repository specifies an
--          actual numeric health threshold (e.g. grams of added sugar per
--          100 g that should trigger LIMIT), and guessing one here would be
--          exactly the fabricated medical judgment the project's constitution
--          forbids. A second rule_set ("v1-draft-examples") stays DRAFT
--          forever and carries a handful of clearly-labelled illustrative
--          rules whose only purpose is to let the rule engine's own mechanism
--          (versioning, per-serving/per-100g evaluation, most-severe-wins,
--          reason codes) be proven correct in tests. Nothing ever transitions
--          a rule_set's status in application code — activating a real,
--          reviewed threshold set is a future migration, not a runtime action.
--
-- uq_rule_set_active is the same partial-unique-index idiom as
-- uq_product_current_version in V4: every indexed row has the same value
-- ('ACTIVE') in the indexed column, so a second such row is a duplicate key,
-- not merely a business-rule violation the application must remember to
-- check.
--
-- applies_to_category (scoring_rule) and category_scope (score_component)
-- reference category(code) — a text column — rather than category(id). The
-- database-design.md §8 comment says only "FK category" without naming the
-- column; category.code is UNIQUE (V1__extensions_and_reference_data.sql), so
-- this is a valid target. It is a deliberate interpretation: it lets a rule
-- compare directly against ProductAssessmentInput.categoryCode() (a String,
-- assembled by the API layer from catalog's ProductQueryService) without a
-- UUID lookup, and it keeps this module free of any JPA relationship into
-- catalog's entities — the same "raw id/code, never a cross-module object
-- reference" convention D-023 established for household membership facts.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Rule sets (D-014)
-- -----------------------------------------------------------------------------
CREATE TABLE rule_set (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version      text NOT NULL UNIQUE,
    status       text NOT NULL CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    activated_at timestamptz,
    retired_at   timestamptz,
    approved_by  text,
    notes        text,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- At most one ACTIVE rule_set, ever — a database invariant, not an
-- application-level check (mirrors uq_product_current_version in V4).
CREATE UNIQUE INDEX uq_rule_set_active ON rule_set (status) WHERE status = 'ACTIVE';

INSERT INTO rule_set (id, version, status, activated_at, approved_by, notes) VALUES
    ('11111111-1111-1111-1111-111111111111', 'v1', 'ACTIVE', now(),
     'Orchestrator — D-024',
     'Ships intentionally empty: zero scoring_rule and zero score_component '
     'rows reference this rule_set. No document in this repository specifies '
     'a reviewed, citable numeric nutrient threshold, and guessing one would '
     'be a fabricated medical judgment (D-009, D-024). Until a nutrition- and '
     'regulatory-reviewed threshold set is authored and activated as a new, '
     'superseding ACTIVE rule_set version, ProductHealthScore reports '
     'status=NOT_YET_SCORED and every PersonalFit goal dimension reports '
     'verdict=INSUFFICIENT_DATA with reason NO_ACTIVE_RULES. This is the '
     'honest MVP default, not a bug.');

INSERT INTO rule_set (id, version, status, notes) VALUES
    ('22222222-2222-2222-2222-222222222222', 'v1-draft-examples', 'DRAFT',
     'ILLUSTRATIVE EXAMPLE RULES ONLY. Never activated, and nothing in this '
     'codebase ever transitions a rule_set''s status — that would require a '
     'future migration plus a reviewed threshold set. Exists solely so the '
     'rule engine''s own mechanism (goal/category/age-band scoping, '
     'active_from/active_to windowing, per-serving vs per-100g vs both-basis '
     'evaluation, most-severe-wins, structured reason codes) can be proven '
     'correct by automated tests against a non-empty rule_set. Every '
     'scoring_rule under this rule_set carries a rationale stating plainly '
     'that its threshold number is fabricated for test purposes and is not a '
     'reviewed clinical or regulatory value (D-024). These rows must never be '
     'copied into an ACTIVE rule_set.');


-- -----------------------------------------------------------------------------
-- Scoring rules (D-009, D-010)
--
-- definition is the small {field, op, value, unit} shape referenced by
-- com.familyfood.scoring.application.RuleEngine: field matches a
-- NutrientFact.nutrientCode(), op is one of GT/GTE/LT/LTE/EQ, value/unit are
-- compared against the fact's value on this row's basis.
-- -----------------------------------------------------------------------------
CREATE TABLE scoring_rule (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_set_id               uuid NOT NULL REFERENCES rule_set(id) ON DELETE CASCADE,
    code                      text NOT NULL,
    applies_to_goal           text,
    applies_to_category       text REFERENCES category(code),
    applies_to_age_band       text,
    basis                     text NOT NULL CHECK (basis IN ('PER_SERVING','PER_100G','BOTH')),
    definition                jsonb NOT NULL,
    verdict                   text NOT NULL
                                CHECK (verdict IN ('EVERYDAY','OCCASIONAL','LIMIT','DOESNT_FIT')),
    severity                  int NOT NULL,
    standard_ref              text NOT NULL REFERENCES reference_standard(code),
    explanation_template_key  text NOT NULL,
    rationale                 text NOT NULL,
    owner                     text NOT NULL,
    active_from               timestamptz NOT NULL,
    active_to                 timestamptz,
    created_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_scoring_rule_code UNIQUE (rule_set_id, code)
);

CREATE INDEX ix_scoring_rule_lookup ON scoring_rule (rule_set_id, applies_to_goal, applies_to_category);

COMMENT ON COLUMN scoring_rule.verdict IS
    'DOESNT_FIT/EVERYDAY/OCCASIONAL/LIMIT only. INSUFFICIENT_DATA is never '
    'stored here — it is a RuleEngine pipeline outcome for "no rule applied", '
    'not a value a rule author chooses (D-004).';

-- Three illustrative rules on the DRAFT "v1-draft-examples" rule_set. None of
-- these numbers is a reviewed clinical or regulatory threshold — see each
-- row's rationale, and see the rule_set note above.
INSERT INTO scoring_rule
    (rule_set_id, code, applies_to_goal, applies_to_category, applies_to_age_band,
     basis, definition, verdict, severity, standard_ref, explanation_template_key,
     rationale, owner, active_from)
VALUES
    ('22222222-2222-2222-2222-222222222222', 'DRAFT_EX_ADDED_SUGAR_LIMIT_100G',
     'REDUCE_ADDED_SUGAR', NULL, NULL, 'PER_100G',
     '{"field":"ADDED_SUGAR_G","op":"GT","value":22.5,"unit":"g"}'::jsonb,
     'LIMIT', 2, 'WHO_FREE_SUGARS_2015', 'why.driver',
     'ILLUSTRATIVE EXAMPLE — not a reviewed clinical threshold, used only to '
     'test the rule engine mechanism, never activated',
     'scoring-module-test-fixture', '2020-01-01T00:00:00Z'),

    ('22222222-2222-2222-2222-222222222222', 'DRAFT_EX_ADDED_SUGAR_DOESNT_FIT_100G',
     'REDUCE_ADDED_SUGAR', NULL, NULL, 'PER_100G',
     '{"field":"ADDED_SUGAR_G","op":"GT","value":45,"unit":"g"}'::jsonb,
     'DOESNT_FIT', 4, 'WHO_FREE_SUGARS_2015', 'why.driver',
     'ILLUSTRATIVE EXAMPLE — not a reviewed clinical threshold, used only to '
     'test the rule engine mechanism, never activated (paired with '
     'DRAFT_EX_ADDED_SUGAR_LIMIT_100G to exercise most-severe-wins)',
     'scoring-module-test-fixture', '2020-01-01T00:00:00Z'),

    ('22222222-2222-2222-2222-222222222222', 'DRAFT_EX_SODIUM_OCCASIONAL_SERVING_BISCUITS',
     'REDUCE_SODIUM', 'BISCUITS', 'A18_30', 'PER_SERVING',
     '{"field":"SODIUM_MG","op":"GTE","value":400,"unit":"mg"}'::jsonb,
     'OCCASIONAL', 1, 'WHO_SODIUM_2012', 'why.driver',
     'ILLUSTRATIVE EXAMPLE — not a reviewed clinical threshold, used only to '
     'test the rule engine mechanism (goal/category/age-band scoping and '
     'per-serving evaluation), never activated',
     'scoring-module-test-fixture', '2020-01-01T00:00:00Z');


-- -----------------------------------------------------------------------------
-- Score components — the 0-100 ProductHealthScore (D-003)
--
-- thresholds is intentionally unshaped by the schema comment beyond "jsonb
-- NOT NULL". com.familyfood.scoring.application.ProductHealthScoreServiceImpl
-- documents and tests the {min, max} linear-interpolation shape it expects;
-- since the ACTIVE rule_set seeds zero rows here, no real product is ever
-- scored against this shape until a reviewed component set is authored.
-- -----------------------------------------------------------------------------
CREATE TABLE score_component (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_set_id    uuid NOT NULL REFERENCES rule_set(id) ON DELETE CASCADE,
    code           text NOT NULL,
    nutrient_code  text NOT NULL REFERENCES nutrient(code),
    direction      text NOT NULL CHECK (direction IN ('POSITIVE','NEGATIVE')),
    weight         numeric NOT NULL,
    thresholds     jsonb NOT NULL,
    category_scope text REFERENCES category(code),
    standard_ref   text NOT NULL REFERENCES reference_standard(code),
    -- Not in the database-design.md §8 listing verbatim; added as a sensible
    -- data-hygiene strengthening, mirroring scoring_rule's own
    -- UNIQUE(rule_set_id, code). Flagged here as an addition, not a
    -- correction — nothing in the schema doc contradicts it.
    CONSTRAINT uq_score_component_code UNIQUE (rule_set_id, code)
);

CREATE INDEX ix_score_component_rule_set ON score_component (rule_set_id);

-- No rows seeded: the ACTIVE "v1" rule_set has zero score_component rows,
-- exactly as it has zero scoring_rule rows (D-024). Tests exercise the
-- aggregation math against hand-built ScoreComponent fixtures constructed
-- directly in test code (see ProductHealthScoreServiceImplTest) rather than
-- via any row in this table, so that inserting a test fixture never risks
-- colliding with uq_rule_set_active or implying a reviewed component set
-- exists.


-- -----------------------------------------------------------------------------
-- Product score — DERIVED, separable from source facts (D-003)
--
-- Not written by this phase's application code: ProductHealthScoreServiceImpl
-- computes on demand rather than caching, since the ACTIVE rule_set has
-- nothing to compute yet. The table and entity exist now so the caching
-- decision, when it is made, does not require a migration.
-- -----------------------------------------------------------------------------
CREATE TABLE product_score (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id  uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    rule_set_id         uuid NOT NULL REFERENCES rule_set(id),
    score               int CHECK (score BETWEEN 0 AND 100),
    grade               text,
    components          jsonb NOT NULL,
    computed_at         timestamptz NOT NULL,
    CONSTRAINT uq_product_score UNIQUE (product_version_id, rule_set_id)
);

CREATE INDEX ix_product_score_version ON product_score (product_version_id);

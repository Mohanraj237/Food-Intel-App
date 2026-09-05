# Database Design

**Version:** 1.0 · **Date:** 2026-09-05 · **Engine:** PostgreSQL 16 · **Migrations:** Flyway
**Binding decisions:** D-014 (versioning), D-015 (per-field provenance), D-009 (standard_ref mandatory), D-005 (food_event discriminator), D-018 (hashing)

---

## 1. Principles

1. **Facts are versioned; nothing is overwritten.** `product` is stable identity, `product_version` holds facts, `person_profile` versions on change, `scoring_rule` belongs to a versioned `rule_set`. A `verdict` stores all three version references and is therefore permanently reconstructible.
2. **Provenance is per field, not per product.** Every nutrient, ingredient and allergen row points at its own `product_source`. "Where did this number come from" is answerable at the granularity a dispute will demand.
3. **Conflict is a retained state.** Disagreeing sources produce a `product_conflict` row and a `NEEDS_VERIFICATION` status — never a silent choice.
4. **Derived data lives apart from source facts.** `product_score` is computed output and is stored separately from the facts that produced it; it can be dropped and recomputed.
5. **Structured columns for business concepts, JSONB only where data is genuinely open-ended** — rule definitions, extraction proposals, audit metadata. Not as a schema-avoidance device (Constitution §35).
6. **Every table that carries personal data has a defined retention rule and a deletion path.**
7. **Household isolation is enforced by the query, not by the caller.** No repository method accepts a bare identifier taken from a request path.

Conventions: `uuid` primary keys (`gen_random_uuid()`), `timestamptz` everywhere, `citext` for email, soft delete only where a legal retention obligation conflicts with erasure — otherwise hard delete.

---

## 2. Schema map

```
identity          household           consent            profile
──────────        ──────────          ─────────          ────────
user_account ────►household_member◄──►consent_record────►person_profile
refresh_token     household           consent_purpose     profile_goal
login_attempt     guardian_link       consent_policy_ver  profile_allergen
                  household_invitation                    profile_condition

catalog                                        scoring
───────                                        ───────
brand ──► product ──► product_version          reference_standard
          product_barcode   │                  rule_set ──► scoring_rule
          category          ├──► product_nutrient          score_component
                            ├──► product_ingredient        product_score
                            ├──► product_allergen
                            ├──► product_source   ◄── per-field provenance
                            ├──► product_conflict
                            └──► data_correction
          product_change_log
          nutrient · ingredient · ingredient_alias · allergen   (reference)

capture                     activity                  audit / platform
───────                     ────────                  ────────────────
label_image                 lookup                    audit_event
ocr_extraction              verdict                   analytics_event
ocr_confirmation            alternative_shown         rate_limit_bucket
                            food_event
```

---

## 3. Identity

```sql
user_account (
  id                uuid PK,
  email             citext UNIQUE NOT NULL,
  email_verified_at timestamptz,
  password_hash     text NOT NULL,          -- Argon2id, never a raw password
  phone_hash        text,                   -- reserved; D-008 phone/OTP deferred
  status            text NOT NULL,          -- ACTIVE | SUSPENDED | DELETION_REQUESTED
  created_at        timestamptz NOT NULL,
  updated_at        timestamptz NOT NULL,
  deleted_at        timestamptz
)

refresh_token (
  id, user_account_id FK, token_hash text UNIQUE NOT NULL,
  issued_at, expires_at, revoked_at, replaced_by_id FK self,
  user_agent_hash text, ip_hash text        -- hashed: no raw IP retained
)
  INDEX (user_account_id) WHERE revoked_at IS NULL

login_attempt (                              -- brute-force + rate limiting, D-007
  id, email_hash text NOT NULL, ip_hash text NOT NULL,
  succeeded boolean NOT NULL, attempted_at timestamptz NOT NULL
)
  INDEX (email_hash, attempted_at DESC)
  INDEX (ip_hash, attempted_at DESC)
```

`email_hash` rather than `email` in `login_attempt` so a leak of the throttling table does not enumerate users. Rows older than 24h are purged by a scheduled job.

---

## 4. Household and membership

```sql
household (
  id, name text NOT NULL, created_by_user_id FK user_account,
  created_at, updated_at, deleted_at
)

household_member (
  id                uuid PK,
  household_id      FK household NOT NULL,
  user_account_id   FK user_account,     -- NULL for a CHILD: children have no account (D-001)
  member_type       text NOT NULL,       -- ADULT | CHILD
  role              text NOT NULL,       -- OWNER | ADULT | DEPENDENT
  display_label     text NOT NULL,       -- a label, not a legal name (minimisation)
  joined_at, left_at, created_at
)
  UNIQUE (household_id, user_account_id) WHERE user_account_id IS NOT NULL
  CHECK  (member_type = 'CHILD') = (user_account_id IS NULL)
  CHECK  (member_type = 'CHILD') = (role = 'DEPENDENT')
  INDEX  (household_id) WHERE left_at IS NULL

guardian_link (                            -- Pack 06 CH-001
  id, child_member_id FK household_member NOT NULL,
  guardian_member_id  FK household_member NOT NULL,
  relationship        text NOT NULL,       -- PARENT | LEGAL_GUARDIAN
  authority_basis     text NOT NULL,
  created_at, revoked_at
)
  UNIQUE (child_member_id, guardian_member_id) WHERE revoked_at IS NULL

household_invitation (                     -- D-002: adults consent for themselves
  id, household_id FK, invited_by_member_id FK,
  email citext NOT NULL,                   -- required to deliver; deleted on terminal state
  token_hash text UNIQUE NOT NULL,
  status text NOT NULL,                    -- PENDING | ACCEPTED | EXPIRED | REVOKED
  expires_at, accepted_at, accepted_by_user_id FK, created_at
)
```

The two `CHECK` constraints make the child model structurally honest: a child cannot have an account and an account-holder cannot be a dependent. `household_invitation.email` is the only retained plaintext contact address, and it is deleted when the invitation reaches a terminal state.

---

## 5. Consent — the gate

```sql
consent_purpose (                          -- reference data
  code text PK,                            -- ACCOUNT · PERSONAL_FIT · CHILD_FOOD_SUITABILITY
                                           -- LABEL_IMAGE_PROCESSING · PRODUCT_IMPROVEMENT
  name, description text NOT NULL,
  requires_verifiable boolean NOT NULL,    -- true for child purposes
  is_essential boolean NOT NULL,
  active boolean NOT NULL
)

consent_policy_version (
  id, purpose_code FK, version text NOT NULL,
  document_uri text NOT NULL, summary text NOT NULL,
  effective_from timestamptz NOT NULL, effective_to timestamptz
)
  UNIQUE (purpose_code, version)

consent_record (                           -- Pack 06 CH-002/007/008
  id                 uuid PK,
  subject_member_id  FK household_member NOT NULL,
  subject_type       text NOT NULL,        -- ADULT_SELF | CHILD
  granted_by_user_id FK user_account NOT NULL,
  purpose_code       FK consent_purpose NOT NULL,
  policy_version_id  FK consent_policy_version NOT NULL,
  status             text NOT NULL,        -- PENDING | ACTIVE | WITHDRAWN | EXPIRED
  mechanism          text NOT NULL,        -- SELF_ATTESTATION | GUARDIAN_ATTESTATION
                                           -- (GUARDIAN_ATTESTATION = REQUIRES_LEGAL_SIGNOFF, D-001)
  evidence           jsonb NOT NULL,       -- mechanism, UA hash, policy text hash, timestamps
  granted_at, withdrawn_at, expires_at, created_at
)
  INDEX (subject_member_id, purpose_code, status)
  CHECK (subject_type = 'CHILD') → mechanism must be a guardian mechanism
```

**Enforcement rule:** every read or computation touching a member's health data calls `ConsentGate.require(memberId, purpose)`, which requires a row with `status = 'ACTIVE'` and a current `policy_version`. The check happens at processing time, not at signup (D-018). For a `CHILD` subject with no `ACTIVE` record, the gate denies and **nothing is processed** — no score, no verdict, no AI call.

---

## 6. Profile — versioned

```sql
person_profile (
  id                 uuid PK,
  household_member_id FK household_member NOT NULL,
  version            int NOT NULL,
  is_current         boolean NOT NULL,
  age_band           text NOT NULL,   -- adults: A18_30 A31_45 A46_60 A60_PLUS
                                      -- children: C1_3 C4_6 C7_9 C10_12 C13_17
                                      -- age BAND only — no date of birth (minimisation)
  sex                text,            -- optional
  dietary_preference text,            -- VEG | NON_VEG | EGGETARIAN | VEGAN | JAIN | NO_PREFERENCE
  created_at, superseded_at, created_by_user_id FK
)
  UNIQUE (household_member_id, version)
  UNIQUE (household_member_id) WHERE is_current

profile_goal      (profile_id FK, goal_code text)      -- PK (profile_id, goal_code)
profile_allergen  (profile_id FK, allergen_code FK allergen)
profile_condition (profile_id FK, condition_code text, self_declared boolean NOT NULL)
```

**Goal vocabulary is closed and excludes restriction framing (D-017):**
`REDUCE_ADDED_SUGAR` · `REDUCE_SODIUM` · `REDUCE_SATURATED_FAT` · `INCREASE_FIBRE` · `INCREASE_PROTEIN` · `AVOID_TRANS_FAT` · `GENERAL_BALANCED_EATING`

There is no weight goal, no body-composition goal, no calorie target — for any profile, and the columns to express them do not exist. `profile_condition` codes in the excluded set (pregnancy, paediatric therapeutic, CKD, oncology) are accepted for the user's own record-keeping but activate **no** condition-specific rules; the app returns general nutrition information and a route to a professional.

---

## 7. Catalogue — the core of the product

```sql
brand    (id, name, normalized_name, owner_company, country_code, created_at)
category (id, parent_id FK self, code UNIQUE, name, path text,
          typical_serving_min_g numeric, typical_serving_max_g numeric)
          -- the serving range powers the "implausible declared serving" flag (D-010)

product (
  id, brand_id FK, name text NOT NULL, normalized_name text NOT NULL,
  pack_size_value numeric, pack_size_unit text,
  category_id FK,
  status text NOT NULL,          -- DISCOVERED | ACTIVE | REJECTED | MERGED
  merged_into_product_id FK self,
  search_vector tsvector,        -- maintained by trigger
  created_at, updated_at
)
  INDEX GIN (search_vector)
  INDEX GIN (normalized_name gin_trgm_ops)      -- fuzzy / typo tolerance
  INDEX (brand_id), (category_id)

product_barcode (
  id, product_id FK, gtin text NOT NULL, gtin_type text,   -- EAN13 | EAN8 | UPCA | ITF14
  is_primary boolean, source text, added_at,
  conflict_flag boolean NOT NULL DEFAULT false
)
  INDEX (gtin)
  -- deliberately NOT unique: real catalogues contain duplicate/reused GTINs.
  -- A second active mapping sets conflict_flag and the lookup returns a
  -- disambiguation result rather than guessing.  (edge case: duplicate barcode)
```

### Product version — where facts live

```sql
product_version (
  id                     uuid PK,
  product_id             FK product NOT NULL,
  version_no             int NOT NULL,
  status                 text NOT NULL,  -- DISCOVERED | OCR_PARSED | NEEDS_VERIFICATION
                                         -- VERIFIED | STALE | SUPERSEDED | REJECTED
  is_current             boolean NOT NULL,
  effective_from         timestamptz NOT NULL,
  effective_to           timestamptz,
  serving_size_g         numeric,
  serving_size_declared  text,           -- verbatim from pack, e.g. "1 biscuit (12.5 g)"
  serving_plausible      boolean,        -- computed against category range (D-010)
  servings_per_pack      numeric,
  ingredients_raw        text,
  ingredients_language   text,
  label_image_id         FK label_image,
  capture_method         text NOT NULL,  -- LABEL_PHOTO | BRAND_PUBLISHED | OFF_IMPORT
                                         -- USER_SUBMITTED | HUMAN_CROSSCHECK | MANUAL_ENTRY
  captured_at            timestamptz NOT NULL,
  captured_by_user_id    FK,
  verified_at            timestamptz,
  verified_by_user_id    FK,
  verification_pass      int NOT NULL DEFAULT 0,  -- 2 required for top-300 SKUs
  data_quality           text NOT NULL,  -- VERIFIED | RECENT | STALE | CONFLICTING
                                         -- NEEDS_REVIEW | UNVERIFIED   (Constitution §17)
  freshness_due_at       timestamptz,    -- from the SOP freshness SLA
  created_at
)
  UNIQUE (product_id, version_no)
  UNIQUE (product_id) WHERE is_current
  INDEX (status), (data_quality), (freshness_due_at) WHERE is_current
```

### Per-field provenance (D-015)

```sql
product_source (
  id, product_version_id FK NOT NULL,
  source_type text NOT NULL,   -- LABEL_PHOTO | BRAND_PUBLISHED | OPEN_FOOD_FACTS
                               -- FSSAI | ICMR_NIN | USER_SUBMITTED | HUMAN_CROSSCHECK
  source_ref text,             -- URI, OFF barcode, image id, or reviewer note
  license_note text,           -- ODbL attribution obligation recorded here (D-016)
  captured_at timestamptz NOT NULL, captured_by_user_id FK,
  confidence numeric CHECK (confidence BETWEEN 0 AND 1)
)

nutrient (                     -- reference
  code text PK,                -- ENERGY_KCAL ADDED_SUGAR_G TOTAL_SUGAR_G SODIUM_MG
                               -- SALT_G SAT_FAT_G TRANS_FAT_G TOTAL_FAT_G PROTEIN_G
                               -- FIBRE_G CARB_G
  display_name, canonical_unit, sort_order, is_core boolean
)

product_nutrient (
  id, product_version_id FK NOT NULL, nutrient_code FK nutrient NOT NULL,
  amount_per_100    numeric,   -- per 100 g or 100 ml, per basis_per_100
  amount_per_serving numeric,
  unit text NOT NULL,
  basis_per_100 text,          -- PER_100G | PER_100ML
  declared_basis text NOT NULL,-- what the pack actually declared
  is_estimated boolean NOT NULL DEFAULT false,
  confidence numeric,
  source_id FK product_source NOT NULL      -- ◄ per-field provenance
)
  UNIQUE (product_version_id, nutrient_code)
```

A missing nutrient is an **absent row**, never a zero. `INSUFFICIENT_DATA` depends on that distinction, and "zero sugar" versus "sugar not declared" are different products (edge case coverage).

```sql
ingredient       (code PK, canonical_name, function_category, ins_number,
                  evidence_status, explanation_key)
ingredient_alias (id, ingredient_code FK, alias, language)
                 INDEX GIN (alias gin_trgm_ops)

product_ingredient (
  id, product_version_id FK, ingredient_code FK ingredient,   -- NULL if unmapped
  raw_text text NOT NULL, position int NOT NULL,
  is_additive boolean, source_id FK product_source NOT NULL
)

allergen (code PK, display_name, is_fssai_declarable boolean)

product_allergen (
  id, product_version_id FK, allergen_code FK allergen NOT NULL,
  declaration_type text NOT NULL,   -- CONTAINS | MAY_CONTAIN | NOT_DECLARED
                                    -- there is no FREE_FROM: we never claim allergen-free
  source_id FK product_source NOT NULL, confidence numeric
)
```

`declaration_type` deliberately has no "free from" value. We report what the label declares and state that manufacturing cross-contamination is not covered (PANTRY 05 §2).

### Conflict, correction, change

```sql
product_conflict (
  id, product_version_id FK, field_path text NOT NULL,
  value_a text, source_a_id FK product_source,
  value_b text, source_b_id FK product_source,
  status text NOT NULL,             -- OPEN | RESOLVED
  resolved_at, resolved_by_user_id FK, resolution_note text, created_at
)
  -- an OPEN conflict forces product_version.data_quality = 'CONFLICTING'
  -- and the product surfaces "data needs verification" (Pack 10 §4)

data_correction (                    -- Pack FR-011, PANTRY FR-12, 48h SLA
  id, product_version_id FK, field_path text NOT NULL,
  submitted_value text, note text, submitted_by_user_id FK,
  status text NOT NULL,             -- PENDING | ACCEPTED | REJECTED
  reviewed_by_user_id FK, reviewed_at, resolution_note text, created_at
)
  INDEX (status, created_at) WHERE status = 'PENDING'

product_change_log (                 -- Pack FR-013 "reformulated on…"
  id, product_id FK, from_version_id FK, to_version_id FK,
  changed_fields jsonb NOT NULL, change_reason text,
  materially_changed boolean NOT NULL,   -- true → invalidate affected recommendations
  detected_at, detected_by_user_id FK
)
```

---

## 8. Evidence and scoring

```sql
reference_standard (                 -- D-009: a rule cannot exist without one of these
  code text PK,                      -- WHO_FREE_SUGARS_2015 · WHO_SODIUM_2012
                                     -- ICMR_NIN_DGI_2024 · ICMR_NIN_RDA_2020 · FSSAI_INR
  name, publisher, jurisdiction text NOT NULL,
  evidence_level text NOT NULL CHECK (evidence_level IN ('A','B','C','D')),
  document_uri text NOT NULL, published_on date, retrieved_on date NOT NULL,
  scope_note text NOT NULL, review_due_on date
)

rule_set (
  id, version text UNIQUE NOT NULL,
  status text NOT NULL,              -- DRAFT | ACTIVE | RETIRED
  activated_at, retired_at, approved_by text, notes text, created_at
)
  -- at most one ACTIVE rule_set: UNIQUE index WHERE status='ACTIVE'

scoring_rule (
  id, rule_set_id FK NOT NULL, code text NOT NULL,
  applies_to_goal     text,          -- NULL = applies regardless of goal
  applies_to_category FK category,   -- NULL = all categories
  applies_to_age_band text,
  basis text NOT NULL,               -- PER_SERVING | PER_100G | BOTH   (D-010)
  definition jsonb NOT NULL,         -- { field, op, value, unit }
  verdict text NOT NULL CHECK (verdict IN
         ('EVERYDAY','OCCASIONAL','LIMIT','DOESNT_FIT')),   -- D-004, closed
  severity int NOT NULL,             -- most severe wins
  standard_ref text NOT NULL REFERENCES reference_standard(code),   -- ◄ D-009
  explanation_template_key text NOT NULL,   -- resolves in the locale bundle (D-019)
  rationale text NOT NULL, owner text NOT NULL,
  active_from timestamptz NOT NULL, active_to timestamptz,
  created_at
)
  UNIQUE (rule_set_id, code)
  INDEX (rule_set_id, applies_to_goal, applies_to_category)

score_component (                    -- the 0–100 ProductHealthScore, D-003
  id, rule_set_id FK, code text NOT NULL, nutrient_code FK nutrient,
  direction text NOT NULL,           -- POSITIVE | NEGATIVE
  weight numeric NOT NULL, thresholds jsonb NOT NULL,
  category_scope FK category, standard_ref FK reference_standard NOT NULL
)

product_score (                      -- DERIVED — separable from source facts
  id, product_version_id FK NOT NULL, rule_set_id FK NOT NULL,
  score int CHECK (score BETWEEN 0 AND 100),
  grade text, components jsonb NOT NULL,   -- full factor breakdown for "why 56?"
  computed_at timestamptz NOT NULL
)
  UNIQUE (product_version_id, rule_set_id)
```

`standard_ref text NOT NULL REFERENCES reference_standard(code)` is the schema-level expression of D-009: a rule with no citable public standard cannot physically be inserted.

---

## 9. Activity — where the thesis is measured

```sql
lookup (
  id, user_account_id FK,            -- NULL for anonymous browsing (D-020)
  household_id FK,
  mode text NOT NULL,                -- SEARCH | BARCODE | LABEL_OCR | DIRECT
  query_hash text,                   -- hashed, never raw search text (D-018)
  resolved_product_version_id FK,
  resolution_source text,            -- CATALOGUE | BARCODE | OCR_SESSION | NOT_FOUND
  latency_ms int, created_at
)

verdict (
  id, lookup_id FK NOT NULL,
  household_member_id FK NOT NULL, profile_id FK NOT NULL, profile_version int NOT NULL,
  product_version_id FK NOT NULL, rule_set_id FK NOT NULL,     -- ◄ three version refs
  verdict text NOT NULL CHECK (verdict IN
        ('EVERYDAY','OCCASIONAL','LIMIT','DOESNT_FIT','INSUFFICIENT_DATA')),
  allergen_flags text[] NOT NULL DEFAULT '{}',   -- separate from verdict, D-004
  fired_rule_ids uuid[] NOT NULL,
  drivers jsonb NOT NULL,            -- nutrient, value, threshold, standard, basis
  computed_at timestamptz NOT NULL
)
  INDEX (household_member_id, computed_at DESC)

alternative_shown (
  id, verdict_id FK NOT NULL, alt_product_version_id FK NOT NULL,
  rank int NOT NULL,
  better_on jsonb NOT NULL,          -- [{nutrient, from, to, delta_pct}]
  worse_on  jsonb NOT NULL,          -- ◄ D-013: honesty is stored, not optional
  shown_at timestamptz NOT NULL
)

food_event (                         -- D-005: discriminator present, one type implemented
  id, household_id FK NOT NULL, household_member_id FK,
  event_type text NOT NULL CHECK (event_type = 'PURCHASE_OUTCOME'),
                                     -- CHECK widened when consumption is scoped in
  lookup_id FK, product_version_id FK,
  outcome text,                      -- BOUGHT_ORIGINAL | BOUGHT_ALTERNATIVE
                                     -- BOUGHT_NEITHER | SKIPPED
  alternative_product_id FK product,
  source text NOT NULL, confidence numeric,
  occurred_at timestamptz NOT NULL, created_at
)
  UNIQUE (lookup_id) WHERE event_type = 'PURCHASE_OUTCOME'   -- never prompt twice
```

The `UNIQUE (lookup_id)` partial index is how "never nag more than once per product" becomes a database guarantee rather than a UI convention.

**Healthy Decision Rate** — the north-star metric — is computed from `food_event` joined to `alternative_shown`. Nothing else in the schema is load-bearing for the thesis.

---

## 10. Capture

```sql
label_image (
  id, storage_key text UNIQUE NOT NULL,    -- non-guessable; never a user-supplied path
  content_type text NOT NULL, byte_size bigint NOT NULL,
  width int, height int, checksum_sha256 text NOT NULL,
  purpose text NOT NULL,                   -- NUTRITION_PANEL | INGREDIENTS | FRONT_PACK
  uploaded_by_user_id FK, uploaded_at, deleted_at,
  retention_due_at timestamptz NOT NULL    -- deletion is scheduled at upload
)

ocr_extraction (
  id, label_image_id FK NOT NULL, provider text NOT NULL, provider_model text,
  status text NOT NULL,                    -- PENDING | SUCCEEDED | FAILED | LOW_CONFIDENCE
  raw_text text, proposal jsonb,           -- proposed fields + PER-FIELD confidence
  overall_confidence numeric, error_code text,
  started_at, completed_at
)

ocr_confirmation (                         -- the mandatory human step
  id, ocr_extraction_id FK UNIQUE NOT NULL,
  confirmed_by_user_id FK NOT NULL,
  confirmed_fields jsonb NOT NULL, edited_fields jsonb NOT NULL,
  resulted_in_product_version_id FK,
  contributed_to_queue boolean NOT NULL,
  confirmed_at timestamptz NOT NULL
)
```

No path exists from `ocr_extraction` to a verdict or to the catalogue that does not pass through `ocr_confirmation`. That is enforced in the application service and asserted by an integration test.

---

## 11. Audit, analytics, platform

```sql
audit_event (                              -- append-only
  id, occurred_at timestamptz NOT NULL,
  actor_user_id FK, actor_role text, correlation_id text,
  action text NOT NULL,                    -- CONSENT_GRANTED · CONSENT_WITHDRAWN
                                           -- CHILD_PROFILE_CREATED · CHILD_DATA_ACCESSED
                                           -- CHILD_DATA_DELETED · PRODUCT_VERSION_PUBLISHED
                                           -- RULE_SET_ACTIVATED · DATA_EXPORTED · …
  resource_type text NOT NULL, resource_id uuid,
  household_id FK, outcome text NOT NULL,  -- SUCCESS | DENIED | ERROR
  metadata jsonb NOT NULL                  -- no health values, ever
)
  INDEX (household_id, occurred_at DESC), (action, occurred_at DESC)
  -- REVOKE UPDATE, DELETE from the application role

analytics_event (                          -- our own store; no third-party SDK (D-018)
  id, name text NOT NULL, user_account_id FK, household_id FK,
  props jsonb NOT NULL,                    -- no health data, no raw query text
  occurred_at timestamptz NOT NULL
)

rate_limit_bucket (                        -- DB-backed; no Redis (D-007)
  bucket_key text NOT NULL, window_start timestamptz NOT NULL,
  count int NOT NULL,
  PRIMARY KEY (bucket_key, window_start)
)
```

`audit_event` has `UPDATE` and `DELETE` revoked from the application database role at the grant level — immutability is a database property, not an application convention (Pack PRD NFR: "immutable audit log").

---

## 12. Search implementation

```sql
CREATE EXTENSION pg_trgm;
CREATE EXTENSION unaccent;

-- weighted document: brand (A) + product name (B) + category (C)
UPDATE product SET search_vector =
    setweight(to_tsvector('simple', unaccent(coalesce(brand_name,''))),  'A') ||
    setweight(to_tsvector('simple', unaccent(coalesce(name,''))),        'B') ||
    setweight(to_tsvector('simple', unaccent(coalesce(category_name,''))),'C');
```

Ranking: `ts_rank_cd` for matched terms, `similarity()` (trigram) as a fallback and typo-tolerance signal, blended. `'simple'` rather than `'english'` because Indian brand names are not English words and stemming actively harms them. Brand aliases (`ingredient_alias` pattern applied to brands) matter more than the engine — PANTRY 06 §2, adopted.

---

## 13. Retention and deletion

| Data | Retention | Deletion trigger |
|---|---|---|
| `user_account` + profile | Life of account | Erasure request → hard delete, 30-day grace |
| `person_profile` versions | Life of account | Cascade with member |
| `consent_record` | 7 years after withdrawal | Statutory evidence of consent — retained deliberately, contains no health values |
| `label_image` | 90 days | `retention_due_at` scheduled job; earlier on request |
| `ocr_extraction.raw_text` | 30 days | Scheduled purge |
| `lookup` / `verdict` | 24 months | Cascade on erasure |
| `food_event` | 24 months | Cascade on erasure |
| `login_attempt` | 24 hours | Scheduled purge |
| `analytics_event` | 24 months | Cascade on erasure |
| `audit_event` | 7 years | Never deleted by user action; actor id pseudonymised on erasure |
| Catalogue (`product*`) | Indefinite | Not personal data |

**Erasure of one adult** removes their profile, verdicts, lookups and events, leaving the household intact for other members (D-002). **Erasure of child data** removes the member, profile, verdicts and links, and writes a `CHILD_DATA_DELETED` audit event. `consent_record` and `audit_event` survive both, as the evidence that consent existed and that deletion happened.

---

## 14. Seed data shipped with the schema

Reference tables (`nutrient`, `allergen`, `ingredient`, `category`, `reference_standard`, `consent_purpose`) ship as Flyway migrations — they are schema, not fixtures.

Products ship as a separately loadable development seed (`V900__dev_seed_products.sql`, applied only in the `local`/`test` profiles) covering chocolate, biscuits, cereal, chips, instant noodles, beverages, dairy and spreads, and deliberately including: missing nutrition values · missing ingredients · multiple serving sizes · unusual units · a bilingual label · a reformulated product with two versions · a duplicate GTIN · an unreadable image · contradictory sources · a declared allergen · a very-high-sugar product · a zero-sugar product · an implausible serving size. Test data must never be reachable from a production profile (Constitution §47).

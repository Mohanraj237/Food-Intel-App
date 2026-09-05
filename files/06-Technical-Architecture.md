# 06 — Technical Architecture

**Project PANTRY | v0.1 | September 2026**

---

## 1. Principles

1. **Build a validation instrument, not a platform.** Every hour spent on scale you don't have is an hour not spent learning whether anyone wants this.
2. **The verdict engine is deterministic and data-driven.** Rules are rows, versioned and auditable. Required for explanation, for legal defensibility, and to reconstruct any past verdict.
3. **Nothing is overwritten.** Products, rules and profiles are versioned; verdicts reference the versions that produced them.
4. **The measurement layer is not optional.** If the event pipeline isn't working, the release isn't done.
5. **Boring stack.** One database, one language, no microservices, no queue you don't need.

## 2. Stack

| Layer | Choice | Why |
|---|---|---|
| Client | **React Native (Expo)** | One codebase, camera access, OTA updates during a cohort. A PWA is acceptable if the team is web-only, but barcode scanning is worse |
| API | **Node/TypeScript or Python/FastAPI** | Whatever the team ships fastest in |
| Database | **PostgreSQL** | Everything here is relational. JSONB for nutrient blobs and rule definitions |
| Search | **Postgres full-text + pg_trgm** | 2,000 SKUs does not need Elasticsearch. Brand aliases matter more than the engine |
| Label OCR | Hosted vision model | Structured extraction from a panel photo, always human-confirmed |
| Events | Table in Postgres, exported for analysis | Do not adopt a third-party analytics SDK that ships user data offshore before your DPDP review |
| Ops console | Server-rendered admin | Data entry, correction queue, rule editing. Build this early — it's how the catalogue gets made |

## 3. Data model

```sql
-- IDENTITY & CONSENT ---------------------------------------------

user_account        (id, phone_hash, created_at)
household           (id, created_by_user_id, created_at)
household_member    (id, household_id, user_account_id, display_label,
                     role, joined_at, left_at)
                    -- every adult is a user_account. No proxy adults in V1.

consent             (id, user_account_id, purpose, policy_version,
                     granted_at, withdrawn_at, method)
                    -- checked at processing time, not just at signup

-- PROFILE ---------------------------------------------------------

profile             (id, user_account_id, version, age_band, sex,
                     dietary_preference, created_at, superseded_at)
profile_goal        (profile_id, goal_code)          -- reduce_added_sugar, reduce_sodium…
profile_allergen    (profile_id, allergen_code)
profile_condition   (profile_id, condition_code, self_declared)

-- PRODUCT ---------------------------------------------------------

product             (id, brand, name, pack_size, gtin, category_id,
                     status)
product_version     (id, product_id, nutrients JSONB, ingredients_raw,
                     additives[], serving_size_g, serving_declared_text,
                     source_type, source_ref, captured_at,
                     verified_by, verification_pass, is_current)
category            (id, parent_id, name, path)

-- RULES -----------------------------------------------------------

rule                (id, code, version, applies_to_goal, applies_to_category,
                     definition JSONB, verdict, standard_ref,
                     explanation_template, active_from, active_to)
                    -- standard_ref is mandatory: WHO_FREE_SUGARS_2015,
                    -- ICMR_NIN_RDA_2020, FSSAI_INR. No rule ships without one.

-- INTERACTION -----------------------------------------------------

lookup              (id, user_account_id, mode, query_hash,
                     resolved_product_version_id, source, latency_ms,
                     created_at)
verdict             (id, lookup_id, profile_id, profile_version,
                     product_version_id, verdict, fired_rule_ids[],
                     rule_set_version, created_at)
alternative_shown   (verdict_id, alt_product_version_id, rank,
                     better_on, worse_on)
outcome             (id, lookup_id, outcome, recorded_at)
correction          (id, product_version_id, field, submitted_value,
                     submitted_by, status, resolved_at)

event               (id, user_account_id, name, props JSONB, created_at)
```

**The table that matters most is `outcome`.** It is the only place the thesis is tested. Everything else is instrumentation around it.

**`verdict` stores `fired_rule_ids[]` and both version references.** This is what makes any past verdict reconstructible — for a user asking "why did it say that", and for a brand's lawyer asking the same question.

## 4. The verdict engine

```
resolve(query)  →  product_version
                        │
          for each active profile in household:
                        │
              load rules where
                 goal ∈ profile.goals
                 and (category matches or is null)
                 and now() between active_from and active_to
                        │
              evaluate against nutrients (per serving AND per 100g)
                        │
              allergen check → hard flag, short-circuits
                        │
              most severe verdict wins
                        │
        return { verdict, fired_rules[], explanation[] }
```

**Rule definition example:**

```json
{
  "code": "ADDED_SUGAR_SERVING_HIGH",
  "version": 3,
  "applies_to_goal": "reduce_added_sugar",
  "applies_to_category": null,
  "definition": {
    "field": "added_sugar_g_per_serving",
    "op": ">=",
    "value": 12
  },
  "verdict": "LIMIT",
  "standard_ref": "WHO_FREE_SUGARS_2015",
  "explanation_template":
    "{{value}}g added sugar per serving — {{pct}}% of a {{cap}}g daily cap."
}
```

**Non-negotiable constraints in code:**
- Verdict values come from a fixed enum. The banned vocabulary (§6 of the PRD) cannot be expressed by the type system.
- A rule without a `standard_ref` fails validation and cannot be activated.
- Both per-serving and per-100g are evaluated, because Indian pack serving sizes are frequently unrealistically small and a per-serving-only verdict can be gamed by the manufacturer.

## 5. Alternatives

Deliberately simple in V1: same category, same rough pack format, better on the profile's primary goal metric, ranked by improvement. **Compute and store `worse_on` for every alternative shown** — if the substitute is higher in sodium or fat, that is displayed. One dishonest substitution costs more trust than ten correct verdicts earn, and trust is the only durable asset here.

Return an empty state honestly. Do not pad.

## 6. OCR path

```
photo → vision extraction → user confirmation screen → verdict
                                     │
                          (optional) → contribution queue → ops review → catalogue
```

Two rules: no verdict is produced from unconfirmed extraction, and no OCR output enters the catalogue without ops review. The confirmation screen is a product feature, not a workaround — it turns the failure state into contribution.

## 7. Privacy in the architecture

- Phone numbers hashed; no plaintext identifiers in the events table
- Search text hashed before storage
- No third-party analytics SDK before legal review
- Purpose checked at processing time against the consent ledger
- Withdrawal removes the withdrawing adult's profile and verdicts from household views without breaking other members
- Retention policy defined per table before launch, not after

## 8. What not to build in V1

Microservices · a recommendation model · a mobile-and-web-and-tablet matrix · an ML pipeline · a public API · SSO · dark mode · onboarding animation · a design system · offline-first sync beyond a simple read cache · notification infrastructure beyond a single outcome prompt.

## 9. Rough build sequence

| Week | Deliverable |
|---|---|
| 1–2 | V0 clickable mock for Track C. Hardcoded data. Ship it and go sell |
| 3–4 | Schema, ops console, data-entry tooling. Seeding begins in parallel with payer conversations |
| 5–6 | Rule engine, verdict + why, search |
| 7 | Barcode, OCR path, outcome capture, events |
| 8 | Household invite and consent, alternatives, closed cohort launch |
| 9+ | Correction queue, Hindi, whatever the cohort demands — and nothing it doesn't |

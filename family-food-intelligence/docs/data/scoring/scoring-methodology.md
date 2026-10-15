# Scoring methodology — rule set v2

This document is the **engineering-facing model**: how `ProductHealthScore` and `PersonalFit` aggregate rule outcomes into a score and a verdict. It contains no nutrition content of its own — every number it refers to lives in `rules-v2.md`, sourced from `evidence/`. Where this document states a formula or a banding rule that is **not** itself quoted from a cited source, it is marked as a **display convention** (D-025's own precedent: `ProductHealthScoreServiceImpl.gradeFor`'s letter bands are exactly this kind of convention, not a nutrient-content claim) — conventions are proposals for the reviewer to accept or change, never treated as equivalent in authority to a cited threshold.

## 1. Two layers, two purposes

| Layer | Source | Produces | Consumed by |
|---|---|---|---|
| **Score components** (`score_component`, role BASELINE/MODIFYING) | FSSAI draft Indian Nutrition Rating (INR) points algorithm (`evidence/source-002`) | A 0–100 product score + a star band, with a driver list | `ProductHealthScoreService` → `/products/{id}/assessment` |
| **Verdict rules** (`scoring_rule`) | WHO SEARO Nutrient Profile Model 2017 per-category thresholds (`evidence/source-001`) | A per-goal verdict (`EVERYDAY`/`OCCASIONAL`/`LIMIT`/`DOESNT_FIT`) | `RuleEngine` → `PersonalFit`/`HouseholdFit` |

These stay separate on purpose (D-003): the verdict chip is the headline, the score is secondary. The two algorithms are **not required to agree** — a product can score fewer INR points yet still not exceed a specific SEARO threshold for a goal the person cares about, or vice versa. Both are shown, neither overrides the other, and `HealthAssessmentSection` states the score's own methodology and version next to it.

## 2. Score components — the `POINTS_NET_V2` algorithm

New `rule_set.algorithm = 'POINTS_NET_V2'` (existing `WEIGHTED_MEAN_V1` — today's `ProductHealthScoreServiceImpl.aggregate`, the `{min,max}` linear-interpolation shape — stays available for the `v1-draft-examples` DRAFT fixture set only, never activated; see V5's own comment).

### 2.1 Inputs required

Per product, per 100 g (solid) or 100 mL (liquid), "as sold": energy (kcal), saturated fat (g), total sugars (g), sodium (mg) — the four **BASELINE** (negative) factors — and, where declared: fruit/vegetable/nuts/legumes/millets percentage (FVNL, a single combined percentage in this catalogue — see §2.5), dietary fibre (g), protein (g) — the three **MODIFYING** (positive) factors.

### 2.2 Food type: SOLID, LIQUID, or FAT_OIL

FSSAI's own Category-I (solid) / Category-II (liquid, non-dairy) split is reused directly (`evidence/source-002`, Table 1/2/3). A third bucket, `FAT_OIL`, is proposed for `EDIBLE_OIL` (§3): FSSAI's Schedule-IV exempts fats and oils from INR entirely, so no INR score is computed for that food type — `EDIBLE_OIL` products report `status=INSUFFICIENT_DATA` for the score (honest: "not applicable," not a fabricated number) while still receiving a WHO SEARO verdict rule for saturated fat. `category_standard_map.food_type` records which bucket each catalogue category uses; a product's own declared nutrient basis (`PER_100ML` from the OFF import, catalogue enrichment) overrides a category default when it disagrees (a category can contain both, e.g. `MALT_DRINK` sold as powder vs. ready-to-drink — see §3's flagged row).

### 2.3 Baseline points (BASELINE role, band-shaped `thresholds`)

Each baseline factor (energy, saturated fat, total sugars, sodium) is looked up against the FSSAI Table 2 (solid) or Table 3 (liquid) band table — a step function, not a linear interpolation: the declared value is compared against the printed `>X` cut-offs from the highest band downward, and the **first band the value exceeds** sets that factor's points. A value at or below the `≤` row (band 0) scores 0. `score_component.thresholds` carries this as `{"type":"BANDS","bands":[{"gt":80,"points":1},{"gt":160,"points":2},…],"cap":25}` — one row per factor, values taken verbatim from `evidence/source-002` Table 2/3. Baseline points for the four factors sum to the product's total **INR baseline points**.

**Missing baseline data:** any one of the four baseline factors absent (not zero — genuinely undeclared) → the product's score is `status=INSUFFICIENT_DATA`, `reasons=[{code:"MISSING_NUTRIENT_DATA", nutrientCode:<which one>}]`. No baseline value is ever assumed as zero or as the band-0 default; an undeclared nutrient is not the same fact as a declared zero (mirrors `ck_nutrient_has_value`'s existing convention across this codebase).

### 2.4 Modifying points (MODIFYING role, band-shaped `thresholds`, capped)

FVNL%, dietary fibre, and protein each look up their own band table (FSSAI Table 2/3), then the **capping rule** (FSSAI Table 4, quoted verbatim in `rules-v2.md`) reduces the sum if the product's own baseline points fall in the higher band. Unlike baseline factors, a **missing** modifying factor is **not** treated as `INSUFFICIENT_DATA` for the whole product — per FSSAI's own band table, a value "≤" the lowest band's cut-off (which for several bands is literally `≤0` or a small number) scores **0 points**, and the evidence pack's own Table 2/3 rows already encode "not declared → 0" implicitly by starting most bands at 0 or a near-zero cut-off. This project's interpretation (flagged, not a literal quote): **an undeclared FVNL/fibre/protein value scores 0 modifying points for that factor**, recorded with `reasonCode=ASSUMED_ZERO` so the response is honest about which inputs were assumed rather than declared. The reviewer may reject this interpretation and require `INSUFFICIENT_DATA` instead; either choice is recorded in `review.md`, not silently decided by this document.

### 2.5 FVNL data availability — a real, current gap

The catalogue's only FVNL-adjacent field today is Open Food Facts' `fruits-vegetables-nuts-estimate-from-ingredients_100g` (an **estimate**, not a declared label value), imported as `nutrient.code = 'FVNL_PCT'` with `is_estimated = true` (catalogue enrichment, Increment 1). No Indian-catalogue product in the current 893-row snapshot declares a verified FVNL percentage on its own label. In practice, nearly every product's FVNL modifying score will be computed from an **estimated**, not verified, input, and `/assessment`'s `drivers[]` entry for FVNL must carry `isEstimated: true` so the UI can show "estimated from ingredients," never presented as a declared label fact (D-014/D-015's provenance rule, applied to a derived score input the same as any other fact).

### 2.6 Net points, stars, and the 0–100 rescale

```
net points = (baseline points) − (capped FVNL points + capped NLM points + capped fibre points + capped protein points)
```

Star band: FSSAI Table 6 (`evidence/source-002`), separately for solid and liquid foods, quoted verbatim in `rules-v2.md`. **This project has no `FAT_OIL` star band** — FSSAI's own Schedule-IV exempts that food type, so `EDIBLE_OIL` products never reach this step (§2.2).

**0–100 rescale is a display convention, not part of the cited algorithm** — FSSAI's own system stops at a star rating (½–5); it never defines a 0–100 number. `ProductHealthScoreServiceImplTest`'s existing `gradeFor` (20-point letter bands) already established the precedent that a display convenience layered on top of a real computation needs no separate standard citation, only a documented, consistent rule. Proposed convention: map each star value to the **midpoint of its net-points band**, linearly rescaled across the full observed net-points range for that food type, clamped to `[0,100]`; the exact linear formula and its clamped endpoints are implementation detail for `PointsNetAlgorithm`, not evidence content, and are visible in code/tests once written. The **star rating itself**, not the 0–100 number, is what `evidence/source-002` actually specifies — `HealthAssessmentSection` must show the star rating as the primary derived number and the 0–100 figure as the clearly-labelled secondary rescale, mirroring D-003's rule that the underlying deterministic result outranks any display convenience built on top of it.

### 2.7 `drivers[]` — the "why this score" breakdown

Every baseline and modifying factor that was actually evaluated (not `MISSING_NUTRIENT_DATA`) contributes one `drivers[]` entry: `{code, direction (NEGATIVE for baseline, POSITIVE for modifying), nutrientCode, value, unit, basis, points, maxPoints, threshold (the band edge crossed), standardRef: "FSSAI_INR", explanationKey}`. `reasons[]` (existing shape, unchanged) carries `POINTS_BAND` for a computed factor, `MISSING_NUTRIENT_DATA` for an absent baseline factor, `ASSUMED_ZERO` for an absent modifying factor treated as zero per §2.4.

## 3. Verdict rules — per-goal `LIMIT`/`OCCASIONAL` from WHO SEARO thresholds

`RuleEngine.evaluateGoal` is unchanged in mechanism (most-severe-wins, both-basis evaluation, category/age-band/goal scoping) — only the ACTIVE rule set's content changes. Every `scoring_rule` row's `definition.field` names an existing `NutrientFact` code (`ADDED_SUGAR_G`, `SODIUM_MG`, `SAT_FAT_G`, `TRANS_FAT_G`) or a new **derived fact** the `AssessmentOrchestrator` computes once per assessment and adds to `ProductAssessmentInput.derivedFacts`: `INR_STARS` (the value from §2.6) and `NOVA_GROUP` (the catalogue's own `product_version.nova_group`, when not null). A derived fact behaves exactly like a nutrient fact to `RuleEngineImpl.fieldOf` — same comparison, same `MISSING_NUTRIENT_DATA` handling when absent, basis always reported as `PER_100G` since neither derived fact has a meaningful per-serving form.

**WHO SEARO thresholds are marketing-prohibition cut-offs, reused here as `LIMIT` verdict thresholds** — an interpretation, not a literal restatement, and flagged as such per rule in `rules-v2.md`: "exceeds the amount above which WHO's regional office says this food category should not be marketed to children" is being read by this project as "exceeds the amount this project calls `LIMIT` for an adult tracking the matching goal." The reviewer may reject this reading for any individual rule.

`basis = PER_100G` for every proposed rule (not `BOTH`): the catalogue snapshot has almost no declared serving sizes (D-010's own concern, restated), so a `PER_SERVING` comparison would resolve to `MISSING_NUTRIENT_DATA` for nearly every product today. A future catalogue enrichment pass that fills in serving sizes broadly is the trigger to reconsider `BOTH`, not something this rule set assumes now.

## 4. `GENERAL_BALANCED_EATING` — how it maps

This goal has no single SEARO nutrient of its own. Proposed: two rules under this goal, both citing derived facts — `INR_STARS` below a reviewer-set cut-off → `OCCASIONAL`/`LIMIT` (interpretation, not a literal FSSAI statement — FSSAI's own star rating is a consumer-facing label, not phrased as a "balanced eating" verdict), and (optional, reviewer's choice) `NOVA_GROUP = 4` → `OCCASIONAL`, citing `evidence/source-006` for the classification and `evidence/source-005` for the "minimise ultra-processed foods" framing **only if the reviewer can verify that citation against their own copy** — `source-005` itself is flagged unverified (§ its own file).

## 5. `DOESNT_FIT` — reserved for declared conflicts, not a nutrient threshold

No proposed rule in `rules-v2.md` produces `DOESNT_FIT`. It stays reserved for a profile-declared restriction a product's declared facts directly contradict (e.g. a `VEG`/`VEGAN` dietary preference against a product's own `NON_VEG`/estimated non-vegetarian ingredients flag) — a factual mismatch, not a severity judgment, matching D-004's existing rule that `DOESNT_FIT` means "does not match a declared preference," never "this is the worst nutrient result." No such rule exists in this rule set's proposal; a future rule set may add one against the catalogue's `diet_flag` enrichment field once that data is reliable enough, as a new, separately reviewed proposal.

## 6. Basket/cart score — a separate display convention, not part of this rule set

`ListAnalysisService`'s basket score (quantity-weighted mean of item scores over scored items) is a Increment 4 concern, not part of rule set v2 — restated here only so a future reader does not look for it in `rules-v2.md`. It reuses whatever score each item already carries from this engine; it introduces no new nutrient threshold of its own.

# Rule set v2 — specification for review

**Status: DRAFT, awaiting review.** Nothing in this document is active. `docs/data/scoring/review.md` is where the named reviewer records approval, row by row or as a whole, before `V17`/`V18` are written. Every number below traces to a quoted line in `evidence/source-00N-*.md`; every row where a number is *not* directly quoted (a mapping, a substitution, or a display convention) says so explicitly and is marked **[interpretation — reviewer to confirm]**.

Claude produced this specification by transcribing the cited sources and proposing the mappings below. Claude did not originate any nutrient, sodium, sugar, fat, fibre, protein, or star-rating number in this document — every numeric threshold is copied from `evidence/source-001` or `evidence/source-002`. Category-to-standard mappings, goal assignments, and the star→verdict/0–100 conventions are Claude's proposed *interpretation* of those sources, exactly the kind of judgment call D-025's process expects a human reviewer to check line by line, not accept blind.

---

## Part A — Category → standard mapping (`category_standard_map`)

One row per catalogue category (13 total). `searo_category` cites `evidence/source-001`'s category number; `inr_food_type` decides which FSSAI table (§Part C) applies. Every row marked **[interpretation]** because no source states "this Indian retail category = this SEARO/Codex bucket" — that correspondence is this project's own product-taxonomy judgment.

| category_code | searo_category | searo Codex ref | inr_food_type | rationale |
|---|---|---|---|---|
| `BISCUITS` | 2 — Fine bakery wares | 7.2 | SOLID | SEARO cat. 2 examples list names "biscuits" explicitly. **[interpretation]** |
| `BREAKFAST_CEREAL` | 4 — Cereals | 6.1, 6.3, 6.7 | SOLID | SEARO cat. 4 examples list "maize-based breakfast cereals of all flavours... muesli, granola, cereal bars." **[interpretation — flag: raw whole-grain products under Codex 6.1 alone are FSSAI Schedule-IV exempt; a flavoured/branded breakfast cereal is not raw grain, so this project reads INR as still applicable to those — reviewer to confirm this reading, not merely the mapping]** |
| `NAMKEEN` | 5(a) — Ready-to-eat savouries, potato/cereal/starch-based | 15.1 | SOLID | SEARO cat. 5(a) examples name "varieties of namkeen, papadams" directly. **[interpretation]** |
| `BEVERAGES` | 6(c) — Water-based flavoured drink | 14.1.4 | LIQUID | Default for the bucket's typical contents (carbonated soft drinks, squashes). **[interpretation — flag: a 100% fruit/vegetable juice product within this bucket should instead use SEARO 6(a) (Codex 14.1.2/14.1.3), whose added-sugar threshold is 0 g not 2.0 g of total sugar; this project's catalogue does not currently distinguish juices from other beverages by a queryable field, so a single default is proposed — reviewer to decide whether a sub-mapping by declared ingredient/name pattern is worth the added complexity now or later]** |
| `MALT_DRINK` | 6(e) — Cereal, grain, tree nut-based beverages | 14.1.5, 6.8.1 | **NONE PROPOSED — see note below** | **[interpretation — open question, no rule proposed]** |
| `STAPLES` | 3 — Bread and ordinary bakery wares | 7.1 | SOLID | For packaged bread/roti/naan products in this bucket. **[interpretation — flag: raw atta/flour/rice products in this same bucket (Codex 6.1/6.2) are FSSAI Schedule-IV exempt and have no WHO SEARO threshold at all (source-001's own footnote [m]: "Energy threshold not provided for staple foods") — this project has no field today to split "bread" from "raw flour" within STAPLES, so the mapping below applies uniformly; a raw-flour product scored against bread's threshold could be a false LIMIT — reviewer to decide whether to accept this over-inclusion or split the category before activation]** |
| `DAIRY` | 9 — Cheese and analogues | 1.6 | SOLID | Default, for paneer/cheese-type products. **[interpretation — flag: liquid milk/curd/lassi within this bucket should instead use SEARO 6(b) (Codex 1.1, INR food type LIQUID) or 8 (Curded dairy desserts); this project's catalogue does not distinguish solid from liquid dairy by a queryable field today — same limitation and same reviewer decision as `BEVERAGES` above]** |
| `EDIBLE_OIL` | 11 — Fats, oils, and fat emulsions | 2.1, 2.2 | **NONE (Schedule-IV exempt — `food_type=FAT_OIL`, no INR score computed)** | FSSAI's own Schedule-IV names Codex 2.1/2.3 as exempt from FOPNL entirely (`evidence/source-002`). SEARO's saturated-fat threshold (35.0 g/100g) still applies as a verdict rule (Part C) — the exemption is from the INR *score*, not from every possible verdict rule. **[interpretation]** |
| `CHOCOLATE` | 1 — Confectionery | 5.1.1, 5.1.2, 5.1.3, 5.1.4, 5.1.5, 5.2, 5.3, 5.4 | SOLID | Direct match — SEARO cat. 1 is literally titled "Confectionery," examples list "cocoa/chocolate bars." | 
| `INSTANT_NOODLES` | 10 — Composite foods (prepared foods) | 16, 12.5.1, 12.5.2 | SOLID | SEARO cat. 10 examples name "instant noodles" directly. |
| `SAUCES_SPREADS` | 18 — Sauces, dips, and dressings | 12.6, 12.9.2 | SOLID | SEARO cat. 18 examples list "ketchup... mayonnaise... chutney." **[interpretation — flag: peanut butter and similar nut-based spreads are named under SEARO cat. 16 (Processed fruits and vegetables) instead, with different thresholds; FSSAI Schedule-IV additionally exempts plain "salads and sandwich spreads" (Codex 12.7, excluding cocoa/nut-based spreads specifically) — this project's catalogue does not distinguish spread sub-types today]** |
| `PROTEIN_FOODS` | 5(b) — Ready-to-eat savouries, processed nuts | 15.2 | SOLID | Default, for nut-mix/protein-bar-type products. **[interpretation — flag: soy-based protein products within this bucket should instead use SEARO cat. 17 (Solid-form soybean products, Codex 6.8.x/12.9.1), whose sugar/sodium thresholds differ — same catalogue-field limitation noted above]** |
| `CHIPS` | 5(a) — Ready-to-eat savouries, potato/cereal/starch-based | 15.1 | SOLID | SEARO cat. 5(a) examples name "chips, crisps" directly. |

**`MALT_DRINK` — no verdict or score rule proposed in this pass.** FSSAI's own regulation text places Codex 6.8.1 in INR **Category-II (liquid)**, with its very low liquid baseline thresholds (energy ≤30 kcal, total sugars ≤6 g **per 100 mL**, Table 1). But this catalogue's malt-drink products (Bournvita, Horlicks, Complan and similar) are sold, labelled, and stored in this catalogue as **powder**, with nutrient facts declared per 100 g of powder — a powder is inherently calorie- and sugar-dense per 100 g by the physics of removing water, so applying the liquid-per-100mL band table to a powder's per-100g values would produce a nonsensical, false `LIMIT`/lowest-star result for nearly every product in the category, not a real finding. Resolving this needs either (a) treating the category as SOLID against SEARO's own powder-adjacent thresholds — none of which quite fit a malt-drink powder either — or (b) computing "as prepared" values from a standard reconstitution ratio, which this project has no sourced standard for. **Flagged as an open question for the reviewer rather than guessed.**

---

## Part B — Score components (`score_component`, `POINTS_NET_V2`)

All values transcribed verbatim from `evidence/source-002`, Tables 1–4. One set of bands per `food_type` (SOLID, LIQUID) — **not** per category; FSSAI's own tables do not vary by category, only by solid/liquid. `EDIBLE_OIL` (`food_type=FAT_OIL`) has no score components at all — no INR score is computed for it (Part A).

### B.1 Baseline (role=BASELINE, direction=NEGATIVE) — SOLID, per 100 g

| code | nutrient_code | thresholds (bands, points 0→10 unless noted) | standard_ref |
|---|---|---|---|
| `INR_SOLID_ENERGY` | `ENERGY_KCAL` | ≤80→0, >80→1, >160→2, >240→3, >320→4, >400→5, >480→6, >560→7, >640→8, >720→9, >800→10 | FSSAI_INR |
| `INR_SOLID_SATFAT` | `SAT_FAT_G` | ≤1.0→0, >1.0→1, >2.0→2, >3.0→3, >4.0→4, >5.0→5, >6.0→6, >7→7, >8→8, >9→9, >10→10, >12→11, >14→12, >16→13, >18→14, >20→15, >22→16, >24→17, >26→18, >28→19, >30→20, >32→21, >34→22, >36→23, >38→24, >40→25 | FSSAI_INR |
| `INR_SOLID_TOTALSUGAR` | `TOTAL_SUGAR_G` | ≤4.2→0, >4.2→1, >8.4→2, >12.6→3, >16.8→4, >21→5, >25.2→6, >29.4→7, >33.6→8, >37.8→9, >42→10, >46.2→11, >50.4→12, >54.6→13, >58.8→14, >63→15, >67.2→16, >71.4→17, >75.6→18, >79.8→19, >84→20 | FSSAI_INR |
| `INR_SOLID_SODIUM` | `SODIUM_MG` | ≤90→0, >90→1, >180→2, >270→3, >360→4, >450→5, >540→6, >630→7, >720→8, >810→9, >900→10, >990→11, >1080→12, >1170→13, >1260→14, >1350→15, >1440→16, >1530→17, >1620→18, >1710→19, >1800→20, >1890→21, >1980→22, >2070→23, >2160→24, >2250→25 | FSSAI_INR |

### B.2 Baseline — LIQUID, per 100 mL (non-dairy; Table 1's liquid column, Table 3's band table)

Liquid foods have **no saturated-fat or sodium score component** — Table 3 defines bands only for Energy and Total sugars.

| code | nutrient_code | thresholds (bands, points 0→10) | standard_ref |
|---|---|---|---|
| `INR_LIQUID_ENERGY` | `ENERGY_KCAL` | ≤6→0, >6→1, >12→2, >18→3, >24→4, >30→5, >36→6, >42→7, >48→8, >54→9, >60→10 | FSSAI_INR |
| `INR_LIQUID_TOTALSUGAR` | `TOTAL_SUGAR_G` | ≤0.1→0, >0.1→1, >1.6→2, >3.1→3, >4.6→4, >6.1→5, >7.6→6, >9.1→7, >10.6→8, >12.1→9, >13.6→10 | FSSAI_INR |

### B.3 Modifying (role=MODIFYING, direction=POSITIVE), capped per `evidence/source-002` Table 4

**SOLID**, per 100 g — FV%, NLM%, Dietary Fibre%, Protein% bands 0→10 identical shape to B.1's energy row (`≤10→0, >10→1, >15→2, >20→3, >25→4, >30→5, >35→6, >40→7, >45→8, >50→9, >55→10`) for FV/NLM; Dietary Fibre bands `≤3→0, >3→1, >6→2, >9→3, >12→4, >15→5, >18→6, >21→7, >24→8, >27→9, >30→10`; Protein bands extend to 15 points (`≤1.5→0, >1.5→1, >2.0→2, >2.5→3, >3.0→4, >5→5, >7→6, >10→7, >15→8, >20→9, >25→10, >30→11, >35→12, >40→13, >45→14, >50→15`).

**LIQUID**, per 100 mL — FV% bands 0→10 (`≤5→0, >5→1, >10→2, >15→3, >20→4, >25→5, >30→6, >35→7, >40→8, >45→9, >55→10`); Protein% bands 0→15, same as solid's protein bands above (both tables print identical protein cut-offs).

**Capping** (Table 4, quoted): Solid — baseline ≤20 → cap 15 (protein), 10 each (FV/NLM/Fibre); baseline >20 → cap 7 (protein), 5 each (FV/NLM/Fibre). Liquid — baseline ≤10 → cap 10 (FV), 15 (protein); baseline >10 → cap 5 (FV), 7 (protein).

### B.4 `algorithm_config` (per `rule_set`, not per component)

```json
{
  "starBandsSolid": [
    {"lte": -11, "stars": 5}, {"gte": -10, "lte": -7, "stars": 4.5},
    {"gte": -6, "lte": -2, "stars": 4}, {"gte": -1, "lte": 2, "stars": 3.5},
    {"gte": 3, "lte": 6, "stars": 3}, {"gte": 7, "lte": 11, "stars": 2.5},
    {"gte": 12, "lte": 15, "stars": 2}, {"gte": 16, "lte": 20, "stars": 1.5},
    {"gte": 21, "lte": 24, "stars": 1}, {"gte": 25, "stars": 0.5}
  ],
  "starBandsLiquid": [
    {"lte": 0, "stars": 5}, {"gte": 1, "lte": 2, "stars": 4.5},
    {"gte": 3, "lte": 4, "stars": 4}, {"gte": 5, "lte": 6, "stars": 3.5},
    {"gte": 7, "lte": 9, "stars": 3}, {"gte": 10, "lte": 12, "stars": 2.5},
    {"gte": 13, "lte": 15, "stars": 2}, {"gte": 16, "lte": 18, "stars": 1.5},
    {"gte": 18, "lte": 20, "stars": 1}, {"gte": 20, "stars": 0.5}
  ]
}
```
Source: `evidence/source-002` Table 6, transcribed exactly including the printed overlap at 18 in the liquid table's 1★/1.5★ rows (flagged there as `[sic]`) — **[interpretation needed: reviewer must decide how the engine resolves that overlap, e.g. "1.5★ wins ties," since the source itself does not say]**. The 0–100 rescale formula itself is a display convention (methodology §2.6), not part of this JSON — it is implementation, not evidence, and belongs in code/tests once `PointsNetAlgorithm` is written.

---

## Part C — Verdict rules (`scoring_rule`)

Every rule: `basis=PER_100G` (methodology §3), `standard_ref` as stated, `explanation_template_key='why.driver'` (existing key), `owner='<reviewer name from review.md>'`, `active_from` = the date `V17` is written. `applies_to_age_band=NULL` for every rule below (**[interpretation — flag: this applies these thresholds to guarded children's food-fit assessment too, via the existing `CHILD_FOOD_SUITABILITY` mechanism, not a new child feature but real content flowing through an existing one; reviewer should confirm this is acceptable before approval, given WHO SEARO's own stated purpose is child-marketing protection, which if anything supports applying it to children specifically, not just adults]**).

### C.1 `REDUCE_ADDED_SUGAR` — source: `evidence/source-001`, footnote [l] substitution noted per row

Per footnote [l]: where SEARO gives no added-sugar threshold but does give a total-sugar threshold, the total-sugar figure is the limiting comparison (`definition.field=TOTAL_SUGAR_G`) — this project reads that footnote as licence to fire this rule under the `REDUCE_ADDED_SUGAR` goal specifically, since no separate "reduce total sugar" `GoalCode` exists. **[interpretation]** Rows where SEARO gives a real `0.0` *added*-sugar threshold use `ADDED_SUGAR_G` directly (any declared added sugar fires the rule) — these are marked.

| Rule ID | category | field | op | value (g/100g) | verdict | severity | Source category | Engineering note |
|---|---|---|---|---|---|---|---|---|
| `SUGAR_BISCUITS_V2` | BISCUITS | TOTAL_SUGAR_G | GT | 6.0 | LIMIT | 2 | SEARO 2 | footnote-l substitution |
| `SUGAR_BREAKFAST_CEREAL_V2` | BREAKFAST_CEREAL | TOTAL_SUGAR_G | GT | 9.0 | LIMIT | 2 | SEARO 4 | footnote-l substitution |
| `SUGAR_BEVERAGES_V2` | BEVERAGES | TOTAL_SUGAR_G | GT | 2.0 | LIMIT | 2 | SEARO 6(c) | footnote-l substitution |
| `SUGAR_STAPLES_V2` | STAPLES | TOTAL_SUGAR_G | GT | 6.0 | LIMIT | 2 | SEARO 3 | footnote-l substitution |
| `SUGAR_DAIRY_V2` | DAIRY | ADDED_SUGAR_G | GT | 0.0 | LIMIT | 2 | SEARO 9 | real added-sugar threshold, any declared added sugar |
| `SUGAR_CHOCOLATE_V2` | CHOCOLATE | TOTAL_SUGAR_G | GT | 6.0 | LIMIT | 2 | SEARO 1 | footnote-l substitution |
| `SUGAR_INSTANT_NOODLES_V2` | INSTANT_NOODLES | TOTAL_SUGAR_G | GT | 9.0 | LIMIT | 2 | SEARO 10 | footnote-l substitution |
| `SUGAR_SAUCES_SPREADS_V2` | SAUCES_SPREADS | TOTAL_SUGAR_G | GT | 10.0 | LIMIT | 2 | SEARO 18 | footnote-l substitution |
| `SUGAR_PROTEIN_FOODS_V2` | PROTEIN_FOODS | ADDED_SUGAR_G | GT | 0.0 | LIMIT | 2 | SEARO 5(b) | real added-sugar threshold |

No rule proposed for `NAMKEEN`, `CHIPS` (SEARO 5(a): both sugar columns "No threshold provided"), `EDIBLE_OIL` (no sugar column meaningful for oils), `MALT_DRINK` (Part A).

### C.2 `REDUCE_SODIUM` — source: `evidence/source-001`, sodium column converted g → mg (×1000)

| Rule ID | category | field | op | value (mg/100g) | verdict | severity | Source category |
|---|---|---|---|---|---|---|---|
| `SODIUM_NAMKEEN_V2` | NAMKEEN | SODIUM_MG | GT | 250 | LIMIT | 2 | SEARO 5(a) |
| `SODIUM_CHIPS_V2` | CHIPS | SODIUM_MG | GT | 250 | LIMIT | 2 | SEARO 5(a) |
| `SODIUM_BREAKFAST_CEREAL_V2` | BREAKFAST_CEREAL | SODIUM_MG | GT | 350 | LIMIT | 2 | SEARO 4 |
| `SODIUM_BEVERAGES_V2` | BEVERAGES | SODIUM_MG | GT | 300 | LIMIT | 2 | SEARO 6(c) |
| `SODIUM_STAPLES_V2` | STAPLES | SODIUM_MG | GT | 250 | LIMIT | 2 | SEARO 3 |
| `SODIUM_DAIRY_V2` | DAIRY | SODIUM_MG | GT | 600 | LIMIT | 2 | SEARO 9 |
| `SODIUM_EDIBLE_OIL_V2` | EDIBLE_OIL | SODIUM_MG | GT | 100 | LIMIT | 2 | SEARO 11 |
| `SODIUM_INSTANT_NOODLES_V2` | INSTANT_NOODLES | SODIUM_MG | GT | 350 | LIMIT | 2 | SEARO 10 |
| `SODIUM_SAUCES_SPREADS_V2` | SAUCES_SPREADS | SODIUM_MG | GT | 300 | LIMIT | 2 | SEARO 18 |
| `SODIUM_PROTEIN_FOODS_V2` | PROTEIN_FOODS | SODIUM_MG | GT | 50 | LIMIT | 2 | SEARO 15(b) |

No rule proposed for `BISCUITS`, `CHOCOLATE` (SEARO categories 2 and 1: sodium "No threshold provided"), `MALT_DRINK` (Part A).

### C.3 `REDUCE_SATURATED_FAT` — source: `evidence/source-001`, saturated-fat column

| Rule ID | category | field | op | value (g/100g) | verdict | severity | Source category |
|---|---|---|---|---|---|---|---|
| `SATFAT_EDIBLE_OIL_V2` | EDIBLE_OIL | SAT_FAT_G | GT | 35.0 | LIMIT | 2 | SEARO 11 |
| `SATFAT_INSTANT_NOODLES_V2` | INSTANT_NOODLES | SAT_FAT_G | GT | 3.5 | LIMIT | 2 | SEARO 10 |

Every other category's saturated-fat column reads "No threshold provided" in `evidence/source-001` — no rule proposed for them. This is a real, honest gap: most of this catalogue's high-saturated-fat products (ghee, some chocolate, some bakery items) will not trigger a saturated-fat `LIMIT` from this source alone. Flagged for the reviewer as a candidate area for a future, separately sourced rule (e.g. a WHO or ICMR-NIN saturated-fat %-of-energy guideline, not gathered in this pass).

### C.4 `AVOID_TRANS_FAT` — no rule proposed

Neither `evidence/source-001` (WHO SEARO NPM 2017) nor `evidence/source-002` (FSSAI INR draft) has a trans-fat column or threshold anywhere in either table. **No rule is proposed for this goal.** A future pass would need a different source (e.g. WHO's REPLACE trans-fat elimination initiative, or FSSAI's separate trans-fat regulation capping industrial trans fat at a % of total fat) — not fetched or verified in this session, so not guessed here.

### C.5 `INCREASE_FIBRE` — source: `evidence/source-002`, Table 2's Dietary Fibre band-0 cutoff

| Rule ID | category | field | op | value (g/100g) | verdict | severity | Source |
|---|---|---|---|---|---|---|---|
| `FIBRE_LOW_V2` | *(all — `applies_to_category=NULL`)* | FIBRE_G | LTE | 3 | OCCASIONAL | 1 | FSSAI_INR Table 2 |

**[interpretation]** Table 2's "Dietary Fibre ≤3 → 0 points" band is being read here as "at or below 3 g/100 g fibre is worth flagging as occasional for someone trying to increase fibre" — the source does not itself state a verdict, only a scoring band; this project proposes reusing the band-0 cut-off as the verdict threshold. Applies only to `SOLID` products (Table 3, liquid, has no fibre column) — engineering note: this rule's `applies_to_category=NULL` covers every category, but liquids will simply report `MISSING_NUTRIENT_DATA` if no fibre is declared, which is honest and not a bug.

### C.6 `INCREASE_PROTEIN` — source: `evidence/source-002`, Tables 2 and 3's Protein band-0 cutoff (identical value in both)

| Rule ID | category | field | op | value (g/100g or /100mL) | verdict | severity | Source |
|---|---|---|---|---|---|---|---|
| `PROTEIN_LOW_V2` | *(all — `applies_to_category=NULL`)* | PROTEIN_G | LTE | 1.5 | OCCASIONAL | 1 | FSSAI_INR Table 2 & 3 |

**[interpretation]**, same reasoning as C.5.

### C.7 `GENERAL_BALANCED_EATING` — derived-fact rules only (methodology §4)

| Rule ID | field | op | value | verdict | severity | Source | Confidence |
|---|---|---|---|---|---|---|---|
| `BALANCED_STARS_OCCASIONAL_V2` | INR_STARS | LT | 2.5 | OCCASIONAL | 1 | FSSAI_INR (derived; band cut-off is this project's own proposal, not quoted) | **[interpretation — reviewer sets or confirms this cut-off]** |
| `BALANCED_STARS_LIMIT_V2` | INR_STARS | LT | 1.5 | LIMIT | 2 | FSSAI_INR (derived; same caveat) | **[interpretation]** |
| `BALANCED_NOVA4_V2` *(optional — reviewer's choice)* | NOVA_GROUP | EQ | 4 | OCCASIONAL | 1 | NOVA classification (`source-006`) + ICMR-NIN DGI 2024 framing (`source-005`, **flagged unverified**) | **[interpretation — include only if the reviewer can independently verify the ICMR-NIN framing, or drop the DGI citation and rely on the NOVA classification alone as a processing-level fact without a health-guidance citation attached]** |

---

## Part D — What is deliberately not proposed

- **`DOESNT_FIT`** — no rule produces it (methodology §5); reserved for a future, separately reviewed dietary-preference-conflict rule against the catalogue's `diet_flag` enrichment field.
- **A general saturated-fat rule for most categories** — the source simply does not provide one (§C.3).
- **A trans-fat rule for any category** — no source located and verified in this pass (§C.4).
- **Any rule for `MALT_DRINK`** — open categorisation question (Part A).
- **Any numeric threshold for `ICMR_NIN_DGI_2024`** — the source PDF could not be read reliably in this pass (`evidence/source-005`); the one place it is cited (`BALANCED_NOVA4_V2`) is explicitly optional and flagged.

None of these are silently filled in with an invented number. They are the honest boundary of what two sources, fully read, actually support.

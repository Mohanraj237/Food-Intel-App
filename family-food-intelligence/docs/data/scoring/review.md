# Review — rule set v2

This is the record D-025 requires before `V17__rule_set_v2_draft.sql`/`V18__rule_set_v2_activate.sql` are written. Nothing in `rules-v2.md` is active, and nothing becomes active from this file alone — activation is always a migration (`docs/data/scoring/README.md`), written only after the approval line at the bottom of this file is signed.

## What to review

1. **`evidence/source-001-who-searo-npm-2017.md`** through **`source-006-nova-classification.md`** — six source files. Spot-check at least the two numeric tables (`source-001`, `source-002`) against the cited URIs yourself; do not take the transcription on trust for a document this consequential.
2. **`scoring-methodology.md`** — the engineering model: two layers (INR score, SEARO verdicts), how missing data is handled, what's a cited threshold vs. a display convention.
3. **`rules-v2.md`** — the actual proposed content. Every row marked **[interpretation]** is a judgment call, not a quoted number, and needs your explicit decision, not just acknowledgement.

## Specific decisions this review must make

Copy each into your approval below with your decision (approve as written / approve with the stated change / reject):

- [ ] **Category mapping (Part A)** — 13 rows, several flagged as heterogeneous buckets (`BEVERAGES`, `DAIRY`, `PROTEIN_FOODS`, `SAUCES_SPREADS`, `STAPLES`, `BREAKFAST_CEREAL`). Accept the proposed default mapping for each, or amend.
- [ ] **`MALT_DRINK`** — no rule proposed at all (powder-vs-liquid mismatch, Part A). Confirm: ship with no verdict/score for this category until resolved, or supply a resolution.
- [ ] **`EDIBLE_OIL`** — FSSAI INR score not computed (Schedule-IV exemption); WHO SEARO saturated-fat verdict rule still applies. Confirm this split is acceptable.
- [ ] **Footnote-[l] sugar substitution (Part C.1)** — nine categories' `REDUCE_ADDED_SUGAR` rule actually compares `TOTAL_SUGAR_G`, not added sugar, per WHO SEARO's own footnote. Confirm this reading, or reject and leave those categories without a sugar rule.
- [ ] **No saturated-fat rule for most categories, no trans-fat rule for any category (Part C.3–C.4)** — confirm shipping with this real, honest gap rather than waiting for another source.
- [ ] **`INCREASE_FIBRE`/`INCREASE_PROTEIN` cut-offs (Part C.5–C.6)** — reusing FSSAI's own "0 points" band edge as the verdict threshold. Confirm or set a different cut-off.
- [ ] **`GENERAL_BALANCED_EATING` star cut-offs (Part C.7)** — 2.5★/1.5★ boundaries are this project's own proposal, not quoted from any source. Confirm, or set different numbers.
- [ ] **`BALANCED_NOVA4_V2` (Part C.7, optional)** — include only if you can independently verify the ICMR-NIN DGI 2024 framing (flagged unverified in `source-005`), or drop the citation and keep the rule on the NOVA classification alone, or drop the rule entirely.
- [ ] **FSSAI INR draft status** — the source is an unfinalised draft regulation (comment period closed Nov 2022, no final notification as of retrieval). Confirm you are comfortable building on a draft standard, labelled honestly as such in the product (D-058), rather than waiting for final notification.
- [ ] **`applies_to_age_band=NULL` (every rule)** — thresholds apply to a guarded child's food-fit assessment the same as an adult's, through the existing `CHILD_FOOD_SUITABILITY` mechanism. Confirm this is acceptable, or restrict specific rules to adult age bands only.
- [ ] **Liquid star-band overlap (Part B.4)** — the source's own Table 6 prints an overlapping value at 18 points between the 1★ and 1.5★ liquid rows. State how the engine should resolve the tie.

## Evidence checked

*(reviewer fills in)*

## Approval

*(leave blank until reviewed — this is the line Claude looks for before writing V17/V18)*

```
APPROVED by <name> on <YYYY-MM-DD>
```

Until that line appears here, `rules-v2.md` remains a draft and no migration referencing it may be written.

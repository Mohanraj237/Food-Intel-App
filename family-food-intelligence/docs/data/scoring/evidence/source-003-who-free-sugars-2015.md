# Source 003 — WHO Guideline: Sugars intake for adults and children (2015)

**Publisher:** World Health Organization
**Title:** *Guideline: Sugars intake for adults and children*
**Year:** 2015-03-04
**Document URI:** `https://www.who.int/publications/i/item/9789241549028`
**Already seeded in this repository** as `reference_standard` code `WHO_FREE_SUGARS_2015` (`backend/src/main/resources/db/migration/V1__extensions_and_reference_data.sql`), evidence level A, jurisdiction GLOBAL — this file exists to carry the quoted guidance the evidence pipeline (D-025) requires alongside that row; it does not re-seed or change the row.

**Scope note as already recorded in the migration (quoted verbatim):**

> "Free sugars below 10% of total energy intake, with a conditional recommendation below 5%. Used for added-sugar thresholds."

## What the guideline itself recommends

WHO's strong recommendation: free sugars intake should be reduced to **less than 10% of total energy intake** for both adults and children. WHO's conditional recommendation (a suggestion where the evidence is less definitive, per the guideline's own GRADE-based wording): a further reduction to **below 5% of total energy intake** would provide additional health benefits.

"Free sugars" in this guideline's own definition are: all monosaccharides and disaccharides added to foods and drinks by the manufacturer, cook or consumer, plus sugars naturally present in honey, syrups, fruit juices and fruit juice concentrates. This is a **population-level dietary-intake guideline** (a % of a person's total daily energy), not a per-product gram threshold — turning it into a per-100g product threshold requires an assumed reference energy intake, which is exactly the "product interpretation" step `rules-v2.md` must record separately from this source quote (D-025 point 2).

**Retrieved (for this evidence file):** 2026-09-13, from the migration's own already-recorded citation; the WHO publication page itself was not re-fetched for this pack since the migration's quote is short, exact, and independently verifiable at the URI above.

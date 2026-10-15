# Source 001 — WHO Nutrient Profile Model for the South-East Asia Region (2017)

**Publisher:** World Health Organization, Regional Office for South-East Asia (SEARO)
**Title:** *WHO Nutrient Profile Model for South-East Asia Region. To implement the set of recommendations on the marketing of foods and non-alcoholic beverages to children*
**Year:** 2017
**Licence:** CC BY-NC-SA 3.0 IGO
**Document URI (primary):** `https://www.who.int/publications/i/item/9789290225447`
**PDF used for this transcription:** `https://iris.who.int/server/api/core/bitstreams/f14a9ed9-0bcc-49cb-923d-e071f63d3297/content` (retrieved 2026-09-13; the older `apps.who.int/iris/bitstream/10665/253459/...` URL now redirects to a page that blocks automated fetches — use the `iris.who.int` bitstream link above instead)
**Retrieved:** 2026-09-13
**Cross-check:** Table A1 in Pandav et al., *The WHO South-East Asia Region Nutrient Profile Model Is Quite Appropriate for India: An Exploration of 31,516 Food Products*, 2021 (`https://pmc.ncbi.nlm.nih.gov/articles/PMC8400900/`) reproduces the same 18-category threshold structure; used only as a confirmation that this transcription matches the model's known structure, not as the primary source for any number below.

**How this transcription was made:** the PDF's threshold table (§2.5, printed pages 10–18) is a rotated, multi-column table that garbles badly under plain text extraction (`pdftotext`) — columns and category numbers land on the wrong rows. Every number below was instead read directly from the PDF's own rendered page images (pages 10–18, rendered at 2.2× via PyMuPDF and read visually, one table block per category), not from the text extraction. Every cell is transcribed exactly as printed, including "No threshold provided" where the model states no threshold. No cell was rounded, inferred, or filled in.

---

## Table: Marketing prohibited if thresholds exceed values per 100 g (or 100 mL for liquids), per category

All values are **maximums** — a product for sale (not specifically child marketing) is used by this project only as a directional signal for what "high in" means for that food category; see `scoring-methodology.md` for how the food-intelligence engine uses these as `LIMIT` thresholds, not marketing-prohibition thresholds (the WHO model's original purpose).

| # | Food Category | Sub-category | Codex Food Category code | Total fat (g) | Saturated fat (g) | Total sugars (g) | Added sugars (g) | Sodium (g) | Energy (kcal) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Confectionery | — | 5.1.1, 5.1.2 (except products used to prepare chocolate milk or hot chocolate), 5.1.3, 5.1.4, 5.1.5, 5.2, 5.3, 5.4 | No threshold provided | No threshold provided | 6.0 | No threshold provided | No threshold provided | 230 |
| 2 | Fine bakery wares | — | 7.2 | 8.0 | No threshold provided | 6.0 | No threshold provided | No threshold provided | 230 |
| 3 | Bread and ordinary bakery wares | — | 7.1 | 8.0 | No threshold provided | 6.0 | No threshold provided | 0.25 | No threshold provided |
| 4 | Cereals | — | 6.1, 6.3, 6.7 | 12.0 | No threshold provided | 9.0 | No threshold provided | 0.35 | No threshold provided |
| 5 | Ready-to-eat savouries (savoury snack foods) | (a) Potato, cereal or starch-based (from roots, tuber, or legumes) and animal based (from skin) | 15.1 | 8.0 | No threshold provided | No threshold provided | No threshold provided | 0.25 | 230 |
| 5 | Ready-to-eat savouries | (b) Processed nuts (including with fruit content) | 15.2 | No threshold provided | No threshold provided | No threshold provided | 0.0 | 0.05 | No threshold provided |
| 5 | Ready-to-eat savouries | (c) Fish-based snacks | 15.3 | No threshold provided | No threshold provided | 6.0 | No threshold provided | 0.25 [n] | 230 |
| 6 | Beverages | (a) Juices — 100% fruit and vegetable juices, direct extraction or from concentrate | 14.1.2, 14.1.3 | No threshold provided | No threshold provided | 6.0 | 0 | No threshold provided | No threshold provided |
| 6 | Beverages | (b) Milk and dairy based drinks (milk, buttermilk, flavoured dairy-based milk, fermented dairy-based milk, drinking yoghurt, whey-based drinks) | 1.1 | 7.0 | No threshold provided | No threshold provided | 0.0 | No threshold provided | No threshold provided |
| 6 | Beverages | (c) Water-based flavoured drink (sport/energy/electrolyte drinks, carbonated and non-carbonated water-based flavoured drinks, jaljeera, concentrates) | 14.1.4 | No threshold provided | No threshold provided | 2.0 | No threshold provided | 0.30 | No threshold provided |
| 6 | Beverages | (d) Coffee, coffee substitutes, tea, herbal infusions | 14.1.5 | No threshold provided | No threshold provided | 2.0 | No threshold provided | No threshold provided | No threshold provided |
| 6 | Beverages | (e) Cereal, grain, tree nut-based beverages | 14.1.5, 6.8.1 | No threshold provided | No threshold provided | 6.0 | No threshold provided | 0.20 | No threshold provided |
| 7 | Frozen dairy based desserts and edible ices | — | 1.7, 3 | 8.0 | No threshold provided | 12.0 | No threshold | 0.10 | 230 |
| 8 | Curded dairy based desserts | — | 1.7 | 7.0 | No threshold provided | 6.0 | No threshold provided | 0.10 | 230 |
| 9 | Cheese and analogues | — | 1.6 | 20.0 | No threshold provided | No threshold provided | 0.0 | 0.60 | No threshold provided |
| 10 | Composite foods (Prepared foods) | — | 16, 12.5.1, 12.5.2 | 8.0 | 3.5 | 9.0 | No threshold provided | 0.35 | No threshold provided |
| 11 | Fats, oils, and fat emulsions | — | 2.1, 2.2 | No threshold provided | 35.0 | No threshold provided | No threshold provided | 0.10 | No threshold provided |
| 12 | Pasta and noodles and like products | — | 6.4 | 3.0 | No threshold provided | No threshold provided | No threshold provided | 0.25 | No threshold provided |
| 13 | Fresh and frozen meat, poultry, game, fish and seafood products | — | 8.1, 8.2.3, 9.1, 9.2.1, 9.2.3 | 15.0 | No threshold provided | No threshold provided | No threshold provided | No threshold provided | No threshold provided |
| 14 | Processed meat, poultry, game, fish and fish products | (a) Processed meat, poultry and game products | 8.2.1, 8.2.2, 8.3.1, 8.3.2 | 8.0 | No threshold provided | No threshold provided | No threshold provided | 0.40 | No threshold provided |
| 14 | Processed meat, poultry, game, fish and fish products | (b) Processed fish and seafood products | 9.2.2, 9.2.4, 9.3, 9.4 | 8.0 | 3.0 | No threshold provided | No threshold provided | 0.40 | No threshold provided |
| 15 | Fresh and frozen fruits and vegetables, and legumes | — | 4.1.1, 4.1.2.1, 4.2.1, 4.2.2.1 | Permitted (no numeric thresholds — marketing is permitted for this category) | | | | | |
| 16 | Processed fruits and vegetables | — | 4.1.2, 4.2.2 | No threshold provided | No threshold provided | No threshold provided | 0.0 | 0.40 | No threshold provided |
| 17 | Solid-form soybean products | — | 6.8.2, 6.8.3, 6.8.4, 6.8.5, 6.8.6, 6.8.7, 6.8.8, 12.9.1 | 12.0 | No threshold provided | 5.0 | 0.0 | 0.10 | No threshold provided |
| 18 | Sauces, dips, and dressings [o] | — | 12.6, 12.9.2 | 12.0 | No threshold provided | 10.0 | No threshold provided | 0.30 | No threshold provided |

## Footnotes (verbatim from the source table)

- **[i]** Few examples are given. Many other products could be included in this category. For further clarification, please refer to Annex 3.
- **[j]** Refer to Annex 4 for rationale/justification on thresholds.
- **[k]** Most thresholds have been rounded up to the nearest half or one decimal where relevant, to allow for minor variations in products.
- **[l]** If no threshold is provided for added sugar, but a threshold has been set for total sugar, the sum of both intrinsic sugar and added sugar will be considered as the limiting threshold.
- **[m]** Energy threshold not provided for staple foods, beverages and [text cut off at the source's own page break — the sentence is not completed on the transcribed page; flagged, not guessed].
- **[n]** The sodium threshold is double of that allowed as per calculation of 1mg/1kcal to allow for processing needs. (Applies to category 5(c), fish-based snacks.)
- **[o]** Portion size considered only for this category (18, Sauces, dips, and dressings).

## Definitions used elsewhere in the model (quoted, §"Glossary")

- **Total sugars:** "the total sugar content of the food product, which may be composed of intrinsic sugars incorporated within the structure of intact fruit and vegetables; sugars from milk (lactose and galactose); and all additional monosaccharides and disaccharides added to foods by the manufacturer, cook or consumer, plus sugars naturally present in honey, syrups and fruit juices."
- **Added sugars:** "monosaccharides and disaccharides added to foods and beverages by the manufacturer, cook or consumer during processing or preparation. For the purpose of this nutrient profile model, the term 'added sugar' is used for consistency with available data in food composition tables. The WHO guidelines on sugars are for free sugars... In this case, intrinsic sugars in, for example, fruits and vegetables are not considered free sugars."
- **Sodium:** "A soft, silver-white element found in salt; 1 g of sodium equals about 2.5 g of salt."

## Scope note

This model was designed to determine which products **may not be marketed to children** — it is a prohibition threshold, not a graded scoring scale. This project (Food Intel App) does **not** reuse it for that purpose (no child-marketing feature exists or is planned per the product's child-safety boundaries). It is cited here only as **the reviewed source for "exceeds this per-100g/mL amount of [nutrient] for this category" verdict rules** (`LIMIT`), per `rules-v2.md` — a distinct use the reviewer must explicitly approve, since applying a marketing-prohibition threshold as a personal-fit "limit" threshold is itself an interpretation, recorded separately in `scoring-methodology.md`.

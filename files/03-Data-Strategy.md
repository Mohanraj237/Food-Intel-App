# 03 — Data Strategy & Operations

**Project PANTRY | v0.1 | September 2026**

This is the document most likely to determine whether the product works. Read it before the technical architecture.

---

## 1. The core finding

Data cost is not linear in ambition. It is roughly flat for answering questions about products users bring you, and explosive for recommending across whole categories.

| Product shape | SKUs needed | Extra fields | Rough annual data cost |
|---|---|---|---|
| Answer about a product the user is holding | 1,500–2,500 seeded + OCR fallback | Nutrition, ingredients | ₹1.5–2.5 lakh |
| Suggest alternatives within seeded categories | 2,500–4,000 | Above + category taxonomy | ₹2.5–4 lakh |
| Optimise a whole basket | 25,000+ | Above + **price + availability by pin code** | ₹40 lakh+ and needs platform access you don't have |

V1 sits in row one, with row two enabled for a small number of categories. Row three is the reason cart optimisation is out of scope. Price and stock cannot be maintained by a small team and cannot be obtained legitimately at scale without the platform whose transaction you were planning to sit above.

## 2. Seeding strategy

**Do not seed by category guess. Seed against real baskets.**

Before writing the data pipeline, collect actual shopping lists or order screenshots from 25–30 people in the beachhead cohort, with consent. Rank the SKUs by frequency. Seed the top ~2,000. This is the difference between a 45% and a 75% query-success rate, and it costs a week.

Expected shape of that list for a metabolic-health cohort:

| Category | Approx. share of seed | Why |
|---|---|---|
| Biscuits, cookies, rusks | 12% | Largest UPF category in India by retail share |
| Breakfast cereal, oats, muesli | 10% | Highest "health halo" gap — where the product earns trust |
| Namkeen and salty snacks | 10% | Sodium driver |
| Packaged beverages, juices, malt drinks | 10% | Sugar driver |
| Atta, bread, packaged staples | 10% | High frequency, drives repeat use |
| Dairy — curd, paneer, flavoured milk, cheese | 9% | Frequent, and flavoured variants surprise people |
| Edible oils and ghee | 7% | Saturated fat |
| Chocolate and confectionery | 7% | The founder's original example lives here |
| Instant noodles, pasta, ready-to-eat | 8% | Sodium |
| Sauces, spreads, condiments | 6% | Hidden sugar |
| Protein and "health" foods | 6% | Segment-relevant, high claim density |
| Buffer | 5% | |

## 3. Sources, ranked by legal safety

| Source | Use | Legality | Cost |
|---|---|---|---|
| **Photograph the physical pack** | Primary source of truth | Clean | Product purchase + labour |
| **Brand-published nutrition panels** (own site, own PDFs) | Primary | Clean — factual data | Free |
| **Open Food Facts** | Seed + contribute back | Open (ODbL) — **attribution required, share-alike applies to derived database** | Free |
| **FSSAI notifications and standards** | Thresholds, ratings | Public | Free |
| **ICMR-NIN IFCT 2017 / RDA 2020** | Reference values | Public | Free |
| **E-commerce product pages, viewed by a human** | Cross-check only | Grey — see below | Labour |
| **Automated scraping of e-commerce** | **Do not** | Breaches ToS on every major platform; potential IT Act §43 exposure; curated descriptions and images carry copyright | — |
| **Commercial nutrition APIs** (Nutritionix, Edamam, Spoonacular) | Not recommended | Clean | $299–1,850/month for USDA-centric data with poor Indian coverage — you would pay for the wrong catalogue |

**On e-commerce cross-checking:** a person looking at a public product page and typing a nutrition value into your database is materially different from a bot harvesting listings. Keep it human, keep it a cross-check rather than a primary source, never copy descriptions or images, and record the source per field. If volume ever tempts you to automate this, that is the moment to get legal advice, not after.

**On Open Food Facts:** contribute every product you verify back to OFF. It costs nothing, it is the right thing given you are building on their work, ODbL share-alike likely obliges you for derived database content anyway, and it makes you a known good actor in the ecosystem you depend on.

## 4. Unit economics of data entry

Indicative and to be measured, not trusted:

- Data associate, tier-2 city: **₹25,000–30,000/month**
- Throughput with a purpose-built entry tool: **45–60 verified SKUs/day** → ~1,100/month
- **Cost per verified SKU: ₹22–28**, excluding the cost of buying products to photograph
- Seeding 2,000 SKUs: **~2 person-months, ₹55–65k labour** + ₹40–60k product purchase

**Freshness.** Assume 15–20% of SKUs change formulation, pack size, or declared serving annually — Bournvita's reformulation after public pressure is the visible example. Budget one refresh cycle per year for the full catalogue and a quarterly recheck for the top 300 SKUs by lookup volume. Annual maintenance: **~₹40–60k** at V1 scale.

At this scale, data is affordable. State that clearly to yourself, because the temptation to raise money to "solve the data problem" is a temptation to solve a problem you do not yet have — while the real problems (retention, payer) stay untested.

## 5. Versioning

Every product is a chain of versions, never an overwrite.

```
product (stable identity: brand + name + pack size + GTIN)
   └── product_version (nutrition, ingredients, source, captured_at, verified_by)
```

Three reasons this matters:
1. **Legal.** If an FMCG company disputes a claim, you must be able to show what the label said on the date you said it.
2. **Trust.** "This changed" is a genuinely differentiated, later-stage feature that requires history you can only accumulate by starting now.
3. **Accuracy.** Verdicts must be reconstructible against the version that produced them.

## 6. Coverage and the OCR fallback

Query-success will be low at launch. Design for it rather than hiding it.

**Fallback chain:** seeded catalogue → barcode lookup → **label photo via vision model** → contribution queue.

The OCR path is what keeps the not-found state from being a dead end. It must include a human confirmation step — the user sees the extracted values and corrects them before a verdict is produced. This does three things: it protects against extraction errors driving a wrong verdict, it makes the user a contributor without asking them to volunteer, and it makes the failure mode honest.

**Target:** ≥70% resolved from seeded catalogue against real cohort baskets by week 8; ≥90% resolved including the OCR path.

## 7. Quality controls

- **Two-pass entry** for the top 300 SKUs by lookup volume. Single-pass elsewhere.
- **Source recorded per field**, not per product.
- **User correction queue** with a 48-hour SLA. Corrections are a gift; treat them as the highest-priority operational queue.
- **Sanity rules at write time:** macros summing beyond 100g per 100g, sodium implausible for category, serving size implausible for pack. Reject and re-verify rather than publish.
- **Never publish a partial panel as complete.** Missing is better than wrong.

## 8. What would change this strategy

| Trigger | Change |
|---|---|
| Query-success stays below 55% after seeding against real baskets | The catalogue approach is wrong; go OCR-first and treat the database as a cache |
| A quick-commerce platform offers catalogue access | Row three becomes viable; revisit cart optimisation |
| Open Food Facts India coverage grows substantially | Reduce seeding effort, increase contribution effort |
| A payer signs and funds data work | Expand categories in the direction of *their* patient population, not your intuition |

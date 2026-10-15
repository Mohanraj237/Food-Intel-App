# Product Policy

> **Scope superseded, 2026-09-12 (D-034, D-042).** This file describes the original, narrow
> packaged-food product. The product is now an individual + family health and wellness
> platform, with the packaged-food decision capability at its core. Scope, modules and roadmap
> live in `docs/architecture/wellness-platform-architecture.md` (v2). What still applies here
> and binds the wider product:
> - food language and explanation rules;
> - no commercial influence (D-012);
> - provenance and alternatives policy (D-013–D-015);
> - "never collapse members into one misleading medical score" (D-040).
>
> Superseded: the identity, MVP scope and exclusion lists below, and line 149's "no meal
> logging (D-005)", which D-039 replaced.

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, 2026-09-05, §1/8/9/10/14/19/23/27/28/29/30/49/50/51/55/56/58/59/76/88/89). Content preserved verbatim from the original numbered sections; reorganized by topic so a session working on product/UX questions doesn't have to load architecture or security content. If this ever disagrees with `docs/decisions/decision-log.md`, the decision log wins.

---

## Project Identity

**Project:** Family Food Intelligence · **Category:** Family Food Intelligence / Household Food Decision Platform · **Primary market:** India

**Primary product promise:** Help households make better food decisions for themselves and the people they care about. The product should answer: **"Is this a good choice for me or my family?"**

The long-term vision is a **Family Food OS** connecting food products, ingredients, nutrition, household members, dietary preferences, parent-controlled child profiles, purchases, meals, recommendations, and household nutrition trends. The initial product is intentionally narrower.

**MVP thesis:** A trusted food-decision assistant that helps a household understand and choose packaged food products based on product facts and household context.

Behavioral loop: `Search / Scan / Snap → Understand → Personalize → Compare → Choose`

The application's value is NOT the number of scans or AI conversations. The core value is: **Did the application help the household make a better food decision?**

---

## Product Scope — MVP

**Account:** authentication · profile · preferences

**Household:** household creation · adult members · parent/guardian-controlled child profile · dietary preferences · relevant dietary constraints · consent management

**Product discovery:** search · barcode · nutrition-label OCR · ingredient-label OCR

**Product intelligence:** structured nutrition information · serving information · ingredients · allergens · relevant additives · product assessment · evidence/provenance · data confidence/freshness where appropriate

**Personalization:** personal fit · household fit · understandable explanation

**Alternatives:** product comparison · better alternatives

**Basic activity:** recent decisions/activity · simple household insight

**Optional meal capture:** low-friction text/photo capture; never require detailed food diary entry to use the core application

---

## Explicitly Out of MVP

Do not implement unless the current PRD explicitly changes: full fitness platform · exercise tracking · sleep tracking · meditation · telemedicine · pharmacy · insurance marketplace · social network · public community · child social features · child-targeted engagement · full calorie-diary workflow · unnecessary wearable integrations · unnecessary microservices · full grocery marketplace · direct dependency on Blinkit/Zepto/Instamart APIs.

Put future ideas into `docs/product/post-mvp-backlog.md`.

---

## Family Model

```text
Household
   |
   ├── Adult
   ├── Adult
   ├── Child
   └── Other dependent
```

A family member may have: goals · preferences · dietary constraints · allergies · food preferences · age band · consent state where required.

The application must distinguish three concepts and never collapse them into one misleading medical score:

- **Product Health** — how the product looks based on validated product facts and the defined scoring methodology.
- **Personal Fit** — how the product relates to a particular person's configured profile.
- **Household Fit** — how the product fits the household's broader context.

---

## Food Language

Prefer: good everyday option · fine occasionally · better limited · higher in added sugar · higher in sodium · better alternative available · doesn't fit the selected dietary goal.

Avoid casually using: toxic · poison · dangerous chemical · never eat · bad food · unhealthy food — unless there is strong evidence and a validated safety context.

Do not turn ordinary food choices into moral judgments. The product philosophy is: **Better, not perfect.**

---

## Search Architecture

The application must not depend solely on barcode scanning. Support:

- **Search** — e.g. "Amul Dark Chocolate"
- **Barcode** — scan package
- **Label OCR** — photograph nutrition/ingredient panel

Future integrations may support receipt, order history, shopping cart, retailer integrations. Do not make the application technically dependent on any specific retailer.

---

## Scoring Engine (product framing)

Never implement `LLM → Score`. Use:

```text
Validated Product Facts → Validation → Deterministic Scoring Rules → Product Score
        → Personalization Rules → Personal Fit → Explanation
```

The scoring algorithm must be deterministic, reproducible, versioned, explainable, testable. Store the score methodology/version used. If the methodology changes, historical scores should remain understandable. (Engineering detail and the full pipeline: `docs/architecture/system-architecture.md` §5; binding decisions D-003, D-009, D-010, D-011.)

---

## Personalization

Personalization must be transparent. If the product recommendation differs between people, the user should be able to understand why — e.g. Adult A's profile yields `Fit = Occasional` while Adult B's yields `Fit = Better Limited` from the same product and nutrition facts. The explanation should identify the material factors. Never make a recommendation based on an opaque hidden AI judgment.

---

## Product Comparison

Comparison should prioritize meaningful differences: added sugar · total sugar where relevant · sodium · saturated fat · trans fat · protein · fiber · calories · serving size · ingredient characteristics · allergens · relevant processing classification. Do not manipulate comparison results to favor commercial relationships.

---

## Alternative Recommendations

Recommendations must be based on explicit criteria. Each recommendation should answer: **Why is this a better alternative?** Possible reasons: lower added sugar · lower sodium · higher fiber · higher protein · different ingredient profile · better fit for household constraints. Never silently prioritize a commercial partner. If commercial relationships ever exist, they must not secretly change health rankings. (See D-013: alternatives must publish `better_on` and `worse_on`.)

---

## Trust & Independence

Do not allow: paid placement to secretly alter health scores · advertisers to override recommendations · brand payments to influence evidence interpretation · hidden affiliate incentives to influence rankings. Any future commercial integration must preserve recommendation integrity.

---

## Commerce Integration

Future commerce integration may include product catalogs, cart, order history, receipts. The application must NOT depend on a particular retailer. Do not assume Blinkit, Zepto, Swiggy Instamart, BigBasket, Amazon, etc. will expose usable APIs. Build adapters. The core system must work independently.

---

## Receipt / Order Intelligence (future)

```text
Receipt / Order → Product extraction → Product normalization → Household basket → Nutrition intelligence
```

Do not implement full purchase automation unless explicitly moved into active scope.

---

## Meal Tracking

Meal tracking is intentionally secondary. Never make detailed manual entry necessary for the core product. Prefer photo / text / simple confirmation over search food → enter grams → choose serving → enter recipe → enter quantities → save. The user should confirm system-generated information rather than perform unnecessary data entry. (MVP decision: no meal logging at all — outcome capture instead. See D-005.)

---

## International vs Indian Context

The primary market is India. Nutrition/product logic must not blindly import assumptions from US, Europe, Australia, or other markets. Consider Indian dietary patterns, products, labels, serving conventions, regulatory context, food categories, household behavior. Where a foreign framework is used, document why it is applicable.

---

## Language / Localization

The product should be architected for future localization. Separate UI text, product data, nutrition values, evidence, generated explanations. Do not hard-code user-facing strings unnecessarily. Future language expansion may include Hindi, Tamil, Telugu, Kannada, Malayalam, other Indian languages. The MVP begins with English (en-IN only, D-019), but architecture must not block localization.

---

## Commercial Model

The architecture should be compatible with: D2C (family subscription) · B2B2C (diagnostics, clinics, employers, insurers, wellness programs) · Commerce (future grocery partnerships). Do not force one model into the architecture prematurely. The core product must remain useful regardless of payer.

---

## Trust and Commercial Separation

Commercial relationships must not silently compromise nutrition recommendations. Never: `Partner pays → Product receives higher health ranking`. Instead: `Health engine → Independent ranking → Commercial layer, if any`. Health/recommendation logic must remain independent from business sponsorship. (Enforced structurally per D-012 — no commercial dependency reachable from `recommendation`.)

---

## Product Search Quality

Search must support exact product names, brand names, partial names, typos where practical, common Indian product terms, category search. E.g. "amul dark", "amul chocolate", "dark chocolate" should all produce useful results.

---

## Family Decision Principle

Food should generally not be framed as Good/Bad. Prefer: Everyday · Occasional · Limit · Not a fit — unless there is a genuine safety issue. Food decisions should consider Product + Portion + Frequency + Context + Person where the available information supports such reasoning.

---

## Future Product Direction

```text
Product Intelligence → Personal Food Intelligence → Family Food Intelligence
        → Purchase Intelligence → Consumption Intelligence → Household Nutrition Intelligence
```

Do not build the later layers prematurely.

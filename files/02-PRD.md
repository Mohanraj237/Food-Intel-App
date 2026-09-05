# 02 — Product Requirements Document (V1)

**Project PANTRY | v0.1 | September 2026**

---

## 1. Product definition

A packaged-food decision tool for Indian households. A user looks up a product, and the product tells them whether it fits each adult in their household, why, and what to buy instead.

**One sentence for the team:** *We answer "should I buy this, for us?" — not "what is in this?"*

**Non-goals, stated so they don't creep back in:** not a calorie counter, not a health tracker, not a diet plan, not a medical device, not a marketplace.

## 2. Personas

**P1 — Rohan, 41, Pune.** HbA1c came back at 6.2 last month. His doctor said "cut sugar" and gave no further detail. He does the weekly Blinkit order. He wants to know which of four atta brands and which biscuits are actually acceptable, and he wants to stop asking his wife. *Primary.*

**P2 — Meera, 36, Bengaluru.** No diagnosis. Reads labels sometimes, follows a nutrition creator, buys mostly online. Health-motivated but not health-anxious. *Secondary / control cohort.*

**P3 — Dr. Nair, diagnostics chain wellness lead.** Owns post-report patient engagement. Currently sends a PDF nobody opens. Judged on repeat testing and package renewals. *The potential payer — see 04-Validation-Plan.*

## 3. The core loop

```
IDENTIFY  →  UNDERSTAND  →  FIT  →  ALTERNATIVE  →  RECORD
 search       nutrition     per      better          what did
 barcode      panel +       person   option in       you
 photo        context       verdict  this category   actually
                                                     do?
```

RECORD is the most important step and the one most easily cut under time pressure. Do not cut it. Without it you cannot measure BO-2, and BO-2 is the thesis.

## 4. Screens

### S1 — Home
Single input with three modes: **Search** (default), **Scan barcode**, **Photograph label**. Below it: household strip showing the adult profiles, and up to three recent lookups.

No dashboard. No score. No streaks. No weight. The home screen asks one question: *what are you looking at?*

### S2 — Not found
Fires often at launch and is therefore a first-class screen, not an error state. Offers: photograph the label to get an answer now, and contribute this product. Must never dead-end.

### S3 — Product
- Product name, brand, pack size, image
- **Household verdict strip** — one chip per adult profile: Everyday / Occasional / Limit / Doesn't fit
- Key numbers per serving *and* per 100g, with the serving size stated explicitly, flagged when the pack's declared serving is unrealistically small
- **Why** — expandable, plain language, naming the specific driver and the reference standard
- FSSAI rating displayed when available, labelled as the regulatory rating, kept visually distinct from the household verdict
- Ingredients list with additives explained on tap
- **Better options** button

### S4 — Why
Rule-by-rule explanation. Format: *"Limit for Rohan — 18g added sugar per serving. Rohan's goal caps added sugar at 25g/day (WHO free-sugar guidance). This one product uses 72% of that."* Every claim traces to a nutrient value and a named public standard. Never an unsourced assertion.

### S5 — Better options
Up to three alternatives in the same category, each showing the single dimension on which it is better and any dimension on which it is worse. **Honesty requirement: if the alternative is worse on something, say so.** One bad substitution costs more trust than ten good verdicts earn.

Explicit "no better option in our data yet" state. Never pad the list.

### S6 — Did you buy it?
Fires on return to the app, or on demand. Three taps: bought the original / bought an alternative / bought neither. This is the measurement instrument. Keep it to one screen and one tap, and never nag more than once per product.

### S7 — Household
List of adult profiles. Add member sends an invite; **the invited adult installs and consents on their own device.** No proxy consent for adults. Expect this funnel to convert badly — instrument it carefully, because it determines whether "household" is a real feature or a hypothesis.

### S8 — Profile setup
Age band (not date of birth), sex, goals (multi-select), allergens, dietary preference, optional self-declared conditions with a clear notice that the product is not medical advice and does not replace a clinician.

## 5. Functional requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-1 | Text search over seeded catalogue with fuzzy and brand-alias matching | Must |
| FR-2 | EAN/UPC barcode scan resolving to a product record | Must |
| FR-3 | Nutrition-label photo → structured nutrient values via vision model, with a mandatory human confirmation step before use | Must |
| FR-4 | Adult profile with goals, allergens, dietary preference | Must |
| FR-5 | Rules-based per-person verdict across four levels | Must |
| FR-6 | Explanation for every verdict, tracing to nutrient + standard | Must |
| FR-7 | Alternatives within category, with declared trade-offs | Must |
| FR-8 | Outcome capture (S6) | Must |
| FR-9 | Hard allergen flag, visually distinct from nutrition verdicts | Must |
| FR-10 | Display FSSAI rating where available | Should |
| FR-11 | Household invite with per-adult consent | Should |
| FR-12 | User-flagged data correction queue | Should |
| FR-13 | Product versioning visible to user ("reformulated on…") | Could |
| FR-14 | Hindi UI | Could |

## 6. The verdict engine

**Design principle: a deterministic rules engine, not a model.** Rules are data, versioned, and auditable. This is required for the explanation, for legal defensibility, and so you can prove what the product said on a given date if challenged.

**Inputs:** nutrient values per serving and per 100g · ingredient list and additives · category · person's goals, allergens, preferences.

**Reference standards — always named, never invented:** WHO free-sugars and sodium guidance · ICMR-NIN RDA 2020 · FSSAI thresholds and the Indian Nutrition Rating where notified. If you cannot cite a standard for a threshold, you do not ship that rule.

**Output vocabulary — fixed, and enforced in code:**

| Verdict | Meaning |
|---|---|
| **Everyday** | Fits this person's stated goals as a regular choice |
| **Occasional** | Fine sometimes; not a staple for this person |
| **Limit** | Works against a stated goal; specific driver named |
| **Doesn't fit** | Conflicts with a declared preference or restriction |
| **Contains [allergen]** | Hard flag. Separate treatment, separate colour, top of screen |

**Banned from the product:** "toxic", "dangerous", "poison", "never eat", "cannot consume", skull iconography, and any numeric score presented as the headline. The founder's original phrasing — telling a person they *cannot* consume something — is the single highest-liability sentence in the concept and must not appear in the UI.

**Also banned:** inferring nutrient deficiency from lookups or logs. "Low in iron-rich foods this week" is a statement about recorded food. "You are iron deficient" is a diagnosis. The product never crosses that line, in any wording.

## 7. Non-functional requirements

- **Latency:** search result under 400ms; verdict under 200ms after product resolution. This is a shelf-side and cart-side tool; slowness kills it.
- **Offline:** cached recent products readable without connectivity. Indian store interiors have poor signal.
- **Accuracy over coverage:** a wrong nutrition value is worse than a missing one. Every seeded product carries a source and a verification date.
- **Data minimisation:** collect nothing not used by a rule. No DOB, no location, no contacts, no photos of people.
- **Auditability:** every verdict reconstructible from (product version, rule version, profile version).

## 8. Explicitly deferred

Cart optimisation and basket scoring · receipt and order-history import · meal logging and consumption tracking · child profiles · elder profiles requiring proxy management · price and availability · commerce integration · lab-result ingestion · household trend dashboards · recipes · restaurant food · regional languages beyond Hindi.

Each is defensible as a future feature. None is required to answer BO-1, BO-2 or BO-3, and each expands either the data requirement or the compliance surface.

## 9. Acceptance criteria for V1

1. A user can find or capture a product and receive a verdict in under 30 seconds from cold open.
2. Every verdict has an explanation naming the nutrient and the reference standard.
3. Every alternative displays at least one honest trade-off where one exists.
4. Query-success rate ≥70% against a held-out list of 200 real products drawn from cohort shopping lists.
5. No screen contains directive medical language or a proprietary headline score.
6. Adding a second adult requires that adult's own consent on their own device.
7. Outcome capture fires and records for at least 40% of product views.
8. A data correction submitted by a user reaches a review queue and can be actioned within 48 hours.

## 10. Release plan

| Release | Contents | Target |
|---|---|---|
| **V0 — clickable mock** | Screens S1–S5, hardcoded data for ~40 products | Week 2. Built for payer conversations, not users |
| **V1.0 — closed cohort** | Full loop, ~2,000 SKUs, adults only, invite-only | Week 8 |
| **V1.1** | Hindi, correction queue, product versioning | Week 14, only if V1.0 gates pass |
| **V1.2** | Parent-controlled child profiles, after legal review | Post-validation |

V0 exists because Track C in the validation plan needs something to show a diagnostics chain in week 3, and building the real product first would waste two months before you learn whether anyone will pay.

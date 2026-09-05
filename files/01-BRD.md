# 01 — Business Requirements Document

**Project PANTRY | v0.1 | September 2026**

---

## 1. Problem statement

Indian households buy an increasing share of their calories as packaged food, and cannot easily tell whether a given product fits the people who will eat it.

Two distinct failures sit underneath this, and the business depends on which one you are actually solving:

**Failure 1 — Information.** The nutrition panel is on the pack but is dense, in English, in per-100g units, and requires the buyer to know their own thresholds. Indian label-literacy research consistently finds back-of-pack tables are rarely used.

**Failure 2 — Decision.** Even when the buyer knows a product is high in sugar, they buy it anyway — taste, price, habit, children's preference, no perceived immediate consequence.

**The business case for this product rests on Failure 2, not Failure 1.** Failure 1 is being solved for free, from three directions at once: FSSAI's forthcoming front-of-pack rating, general-purpose AI assistants that read a label photo, and creator content that has already told urban India that malt drinks and biscuits are sugary. A product that only closes the information gap is entering a market where the gap is closing without it.

What is not being solved for free: *given these specific people, this household's history, and this shelf, what should I put in the basket?*

## 2. Opportunity and its ceiling

**Genuine tailwinds.** India has roughly 101 million people with diabetes and 136 million with prediabetes (ICMR-INDIAB, 2023). Ultra-processed retail grew at ~13.4% CAGR 2011–2021 (WHO–ICRIER). Regulatory attention is at a peak, with the Supreme Court pressing FSSAI on front-of-pack labelling through 2025–26. Consumer appetite for label transparency is demonstrated — Whole Truth Foods reached ₹216 crore revenue in FY25 on a clean-label proposition.

**The ceiling, stated honestly.** The clearest comparable is Yuka: ~55M+ users, ~15 people, no paid marketing, and about $7.3M in annual revenue — built on a food database (Open Food Facts) it obtained free, at European price points. An Indian equivalent starts with no equivalent free database and prices into a market where wellness-app ARPU runs roughly USD 12–16 per paying user per year.

The conclusion this document builds on: **a consumer-subscription-only version of this product is a low-seven-figure business at best, and cannot fund category-complete data.** That is not a reason not to build. It is a reason the payer question must be answered before the product is scaled.

## 3. Business objectives (120 days)

This is a validation programme, not a launch. The objectives are epistemic.

| # | Objective | Measured by |
|---|---|---|
| BO-1 | Determine whether a defined household segment retains | Week-8 retention in beachhead cohort |
| BO-2 | Determine whether the product changes purchase behaviour | Substitution acceptance rate, self-reported and repeat |
| BO-3 | Determine whether a third party will pay for that change | ≥1 signed paid pilot or LOI with a rupee value |
| BO-4 | Determine the true cost of adequate product data | Cost per verified SKU, and query-success rate against real baskets |
| BO-5 | Establish a defensible position on medical-advice and DPDP boundaries | Legal review completed before any cohort beyond 200 users |

Note what is absent: revenue targets, user-count targets, download targets. At this stage those are vanity and will push you to build the wrong thing.

## 4. Target segments

**Primary beachhead — metabolic-health adults.** Adults recently told by a doctor or a lab report to change their diet: prediabetic, newly diagnosed type 2 diabetic, hypertensive, dyslipidaemic. Chosen because the constraint is *internal* — the person deciding and the person complying are the same person. This is the variable that matters more than emotional intensity.

**Secondary cohort — general health-motivated adult household buyers.** Tested as a control, to see whether pain is actually doing the work or whether the product is broadly useful.

**Explicitly deferred — parents managing children's food.** This is likely a larger long-term market and possibly a better *marketing* story. It is a worse *first* cohort for two reasons. First, DPDP requires verifiable parental consent for a child's personal data and restricts behavioural monitoring of children — the compliance architecture is real work. Second, and more important commercially, the parent's constraint is externally negotiated: the app can tell the parent the cereal is a limit-item and the child still wants it, so knowledge does not convert to purchase change. Behaviour change is what a payer buys. Revisit at V1.2 with a parent-controlled, non-tracking design.

**Explicitly out of scope — anyone seeking clinical management.** Pregnancy nutrition, CKD, paediatric therapeutic diets, eating-disorder-adjacent goals. These require clinician oversight the product will not have.

## 5. Candidate business models, ranked by what you can test now

| Model | Testable in 120 days | Verdict |
|---|---|---|
| **Diagnostics / clinic-led B2B2C** — lab or clinic gives the tool to patients post-report | Yes, cheaply | **Test first.** Natural trigger moment, short sales cycle relative to insurance, buyer already owns the patient relationship |
| **Employer wellness** | Yes | Test second. Slower procurement, but real budgets |
| **Consumer subscription** | Partly — you can test willingness, not sustainability | Test as a signal, do not plan the company around it |
| **Insurer-led** | No — sales cycles and evidence requirements exceed your horizon | Defer to month 12+ |
| **Commerce / affiliate on recommended alternatives** | Yes, technically | **Do not.** The moment users suspect ranking can be bought, the asset is gone. This is the one thing Yuka refuses and the reason its recommendations are trusted |
| **Selling household consumption data to FMCG** | No | **Do not.** Same reason, worse optics |

**Standing constraint for all models: no brand-paid placement, no sponsored ranking, no advertising, ever.** Write this into the founding documents so it cannot be quietly reversed under revenue pressure later.

## 6. Scope

**In scope for V1**
- Packaged food and beverage products sold in India
- Adult household member profiles with dietary goals and constraints
- Product lookup by search, barcode, and nutrition-label photo
- Per-person suitability verdict with an explanation
- Alternative product suggestions within a narrow set of seeded categories
- Consent, audit and event logging sufficient for DPDP and for measurement

**Out of scope for V1**
Home-cooked and loose food · restaurant food · recipe analysis · meal logging · calorie counting · micronutrient deficiency inference · child profiles · cart optimisation · price and stock data · commerce integration · wearables · lab-result ingestion · regional languages beyond Hindi · cosmetics and non-food

## 7. Cost model — 120 days only

Indicative, to be replaced with your real numbers.

| Line | Estimate | Note |
|---|---|---|
| Product data seeding (~2,000 SKUs) | ₹1.0–1.5 lakh | See 03-Data-Strategy. One associate at ~₹28k/month for two months, plus product purchase for label photography |
| Infrastructure (hosting, DB, OCR/vision API) | ₹15–25k total | Trivial at this scale |
| Legal — DPDP and medical-advice review | ₹75k–2 lakh | Do not skip. Single largest avoidable risk |
| Incorporation and compliance | ₹25–50k | If not already done |
| Cohort recruitment incentives | ₹50k | Modest; do not pay for retention or you corrupt the signal |
| **Total non-salary** | **₹2.5–4.5 lakh** | Excludes founder time |

The important finding embedded here: **data is cheap at 2,000 SKUs and ruinous at category-complete coverage.** One associate can verify roughly 1,000–1,200 SKUs a month with decent tooling. Answering questions about products users bring you is affordable. Recommending across whole categories with price and availability is not, at your stage. This is the arithmetic that justifies the V1 scope cuts.

## 8. Risks to the business case

| Risk | Severity | Mitigation |
|---|---|---|
| Users engage but do not change what they buy | **Critical** — invalidates the thesis | BO-2 is designed to detect this by day 90. If it fails, this is a content product, not a business |
| No payer materialises | **Critical** | Tested first and cheapest (Track C). Failure here forces a lean-utility strategy with a small ceiling — a legitimate choice, but choose it knowingly |
| Query-success rate too low on real baskets | High | Seed against actual cohort shopping lists, not category guesses |
| Incumbent ships the feature | High | HealthifyMe has ~40M users and an Indian food database. Speed is not a defence; trust and a specific workflow are |
| FSSAI mandate makes the score redundant | Medium | Product is architected to *display* the regulatory rating and add personal fit on top. This is a tailwind if the design holds |
| FMCG legal challenge | Medium | See 05. Evidence trail, sourced claims, no unsourced health assertions |
| DPDP non-compliance | Medium–High | Adults only in V1, minimisation by default, legal review before scaling |
| Founder capacity | Depends on A1 | If solo, cut the secondary cohort and run one segment |

## 9. Success and failure criteria

**Proceed to a funded, larger build if, by day 120:**
Week-8 beachhead retention ≥25% **and** substitution acceptance ≥15% **and** at least one paid pilot or priced LOI signed.

**Pivot to lean consumer utility if:** retention and substitution hold but no payer emerges. Accept a small team and a small ceiling, or reconsider.

**Pivot to content or tooling if:** people use it, enjoy it, and do not change purchases. The value is educational, not behavioural.

**Stop if:** retention tracks the category norm (~20% at week 8) despite a pain-selected cohort, **and** no payer signs. Both failing together means neither the consumer nor the market values the outcome enough to sustain the data cost.

# Project PANTRY — Document Set

*Working codename. Not a brand recommendation.*

**Version 0.1 — drafted September 2026**

---

## Read this page first

These documents are written against assumptions you have not confirmed. If an assumption is wrong, the affected documents are listed so you know what to re-scope rather than reading all of it again.

| # | Assumption | If wrong, revise |
|---|---|---|
| A1 | Founding team of 1–3 people, at least one who can ship a full-stack product | 02-PRD (scope), 06-Technical-Architecture |
| A2 | Pre-funding or friends-and-family only. 9–12 months of runway. No budget for a data-entry team beyond ~₹2–3 lakh total | 03-Data-Strategy (entirely), 02-PRD |
| A3 | India-only. English + Hindi at launch. Urban tier-1/tier-2 | 02-PRD, 04-Validation-Plan |
| A4 | You are willing to run founder-led B2B sales conversations in parallel with building | 04-Validation-Plan (Track C is the spine) |
| A5 | You have not yet incorporated or engaged an Indian privacy lawyer | 05-Compliance-Risk-Register (timing of legal review) |
| A6 | Goal is to find out whether this is a business within ~120 days, not to launch a polished product | All |

If you are actually funded with a team of eight, tell me — the correct V1 is materially larger and the sequencing changes.

---

## Decisions locked in this version

These are the calls that shaped every document. Each has a reversal condition, so you can argue with them on evidence rather than taste.

| Decision | Reasoning | Reverse if |
|---|---|---|
| **Packaged food only.** No home-cooked, loose, or restaurant food | Only packaged goods have a reliable nutrition panel. Roughly 92% of Indian grocery is still unorganised, but packaged is where the data exists and where the target segment's discretionary risk sits | You get access to a validated Indian composite-dish database |
| **No cart optimisation ("Fix my cart") in V1** | It requires complete, priced, in-stock coverage of whole categories — an order of magnitude more data than answering questions about products users bring you | A quick-commerce platform gives you catalogue access |
| **No meal logging in V1** | Median ~70% of health apps are abandoned within 100 days; diet apps worse. Logging is the highest-churn interaction in the category | Passive capture becomes genuinely passive |
| **Adults only in V1. No child profiles** | Every adult family member is a separate data principal requiring their own consent; children require verifiable parental consent under DPDP. Both are real, but only one can be solved in V1 | After legal review and after the single-adult product retains |
| **No proprietary score as the headline** | FSSAI's rating is coming; LLMs read labels for free. The score is an input, not the product | Never — this one is structural |
| **Search-first, camera-second** | Both interactions ship, but search is the primary because the paying urban segment increasingly buys through quick commerce, where no package is in hand | Beachhead testing shows offline-store scanning dominates |
| **Payer validation runs first, not last** | It is the cheapest experiment (founder time + a clickable mock) and its answer reshapes everything else | Never — run it first regardless |

---

## The one-line thesis these documents encode

> Help an Indian household decide whether a packaged food product fits the specific adults in that household, at the moment of purchase — and find out within 120 days whether a consumer will retain, a household will change what it buys, and a third party will pay for that change.

Everything not required to test those three things is out of scope for V1.

---

## Document map

| Doc | What it answers | Primary reader |
|---|---|---|
| **01-BRD** | Why this business, for whom, what winning looks like, what it costs | You, co-founder, any investor |
| **02-PRD** | What exactly gets built in V1, screen by screen, rule by rule | Whoever writes the code |
| **03-Data-Strategy** | Where the product data comes from, what it costs, how it stays fresh | You (this is the real operational risk) |
| **04-Validation-Plan** | The 120-day experiment, the metrics, and the kill gates | You |
| **05-Compliance-Risk-Register** | DPDP, medical-advice boundary, FMCG legal exposure, data sourcing legality | You + lawyer |
| **06-Technical-Architecture** | Data model, scoring engine design, stack | Whoever writes the code |

---

## What is deliberately not in this set

- **Pitch deck.** Premature. You have no evidence yet; a deck now would be a document of assumptions.
- **Financial model.** A three-year projection built on zero data points is theatre. 01-BRD carries a cost model for the next 120 days only, which is the horizon you can actually forecast.
- **Brand and naming.** Decide after you know who the customer is. The payer answer may change the name entirely.
- **Full design system.** V1 is a validation instrument, not a product launch.

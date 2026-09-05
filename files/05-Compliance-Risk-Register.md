# 05 — Compliance & Risk Register

**Project PANTRY | v0.1 | September 2026**

**This document is not legal advice.** It is a structured brief for the Indian privacy lawyer you should engage before your cohort exceeds ~200 users, and a register of the risks that should shape design decisions now. Every item marked ⚖️ requires legal sign-off before launch.

---

## 1. Data protection (DPDP Act 2023 + Rules 2025)

### The design principles, in order of importance

**1. Adults only in V1.** Not because children don't matter, but because two consent problems cannot be solved simultaneously by a small team.

**2. Every adult is their own data principal.** ⚖️ This is the constraint most often missed in "family app" designs. A household head cannot consent on behalf of a spouse or an elderly parent to the processing of their health conditions and dietary restrictions. Each adult installs, each adult consents, each adult can withdraw independently — and withdrawal must remove that person's verdicts from the household view without breaking the app for everyone else.

**3. Minimise by default.** Collect only what a rule consumes:

| Collect | Do not collect |
|---|---|
| Age band | Date of birth |
| Sex | Name beyond a display label |
| Goals, allergens, preferences | Location, contacts, photographs of people |
| Self-declared conditions (optional) | Diagnosis documents, prescriptions, lab PDFs |
| Lookup and outcome events | Anything not consumed by a rule or a metric |

**4. Purpose limitation is architectural, not a policy page.** Consent is captured per purpose, versioned, and checked at processing time. If you later want to use lookup history for a new purpose, that is a new consent, not a terms update.

**5. No advertising, no profiling for advertising, no sale of individual data.** Ever. Also the correct commercial position (see 01-BRD §5).

### Children — deferred, and how to do it later ⚖️

DPDP requires verifiable parental consent before processing a child's personal data (under 18), and restricts tracking, behavioural monitoring and targeted advertising directed at children. Penalties run to ₹200 crore.

When you do build it, the shape that is most likely defensible:

- Parent is the account holder and controller; the child has no account
- Verifiable parental consent per the Rules 2025 mechanisms, not a checkbox
- Data limited to age band, allergens, dietary preferences, restrictions
- **Product suitability only — no consumption tracking of the child, no behavioural profile, no streaks, no notifications directed at the child, no weight or body-composition targets for a minor**
- The last point is a child-safety requirement independent of the law: never present a weight-loss or body-fat goal for a child in any form

Commercially, note the finding in 01-BRD: parents are a high-emotion, low-compliance cohort. Their constraint is externally negotiated with the child. Build this because it's a large market, not because it will validate behaviour change faster.

---

## 2. The medical-advice boundary

This is the highest-liability area in the original concept.

| Never say | Say instead |
|---|---|
| "You cannot consume this" | "Doesn't fit your stated goal" |
| "Not safe for diabetics" | "18g added sugar per serving — 72% of a 25g daily cap" |
| "You are iron deficient" | "Your recorded choices this week were low in iron-rich foods" |
| "This will raise your blood sugar" | "High in added sugar" |
| "Toxic", "dangerous", "poison" | *(banned outright)* |

**Structural rules:**
- The product states facts about food and compares them to named public standards. It does not make claims about a person's body, disease state, or prognosis.
- Self-declared conditions adjust thresholds; they never produce a diagnosis, a prognosis, or a treatment implication.
- Every screen touching conditions carries a clear notice that this is not medical advice and does not replace a clinician. ⚖️
- Allergens are the one hard-flag case, and even there the wording is "contains", not "will harm you". Never claim allergen-free — declare what the label declares and state that manufacturing cross-contamination is not covered. ⚖️
- **Out of scope entirely:** pregnancy nutrition, paediatric therapeutic diets, CKD, oncology, and any goal framed around rapid weight loss or restriction. If a user's stated goal reads as disordered eating, the product should not produce numeric targets at all.

---

## 3. FMCG legal exposure

This is not hypothetical. Mondelez India issued a legal notice over the Bournvita video, and Revant Himatsingka has reportedly received around six such notices. Internationally, Yuka lost at first instance to the French charcuterie federation (€20,000, 2021) before winning on appeal (2023, €60,000 awarded against FICT).

**Defensive architecture:**
1. **Every statement traces to the manufacturer's own declared panel**, captured with a date and a source. You are reporting what the brand printed.
2. **Every threshold traces to a named public standard** — WHO, FSSAI, ICMR-NIN. No invented cut-offs. If you cannot cite it, you do not ship the rule.
3. **Versioned records.** You must be able to prove what the label said on the date you said it. This is the single strongest reason for the versioning design in 03-Data-Strategy.
4. **No safety, disease-causation, or "harmful" claims.** Quantity and comparison only. The line between "this contains 40g sugar per 100g" and "this is harmful" is the line between a factual statement and a disparagement claim.
5. **Fast, visible correction path.** A brand that emails a correction and sees it applied in 48 hours usually does not escalate.
6. **No brand-paid ranking, ever** — also your best answer if accused of bias.

---

## 4. Data sourcing legality

| Practice | Position |
|---|---|
| Photographing packs you bought | Clean |
| Brand-published nutrition data | Clean — factual |
| Open Food Facts | Open under ODbL. **Attribution required; share-alike likely applies to derived database content.** Contribute back ⚖️ |
| Human cross-check of public product pages | Grey. Human-only, cross-check only, never copy descriptions or images |
| Automated scraping of e-commerce | **Do not.** ToS breach on every major platform; potential IT Act §43 exposure; Delhi HC has granted injunctive relief against scraping in comparable matters |
| Reading users' email for receipts | **Not in V1.** Restricted OAuth scope requires Google verification plus annual third-party security assessment; and under DPDP, ingesting a mailbox to extract purchases is a purpose-limitation and minimisation problem ⚖️ |

---

## 5. Risk register

| ID | Risk | L | I | Mitigation | Owner |
|---|---|---|---|---|---|
| R-01 | Wrong nutrition value drives a wrong verdict | M | **High** | Two-pass entry on top SKUs; sanity rules at write; correction queue; founder reviews every reported error for 90 days | Founder |
| R-02 | Adult-consent friction kills the household feature | **High** | Med | Instrument the funnel; treat <10% as a finding about the thesis, not an onboarding bug | Product |
| R-03 | FMCG legal notice | Med | Med | §3 above; keep an evidence pack per product | Founder ⚖️ |
| R-04 | DPDP non-compliance found late | Med | **High** | Legal review before 200 users; minimisation from day one | Founder ⚖️ |
| R-05 | Verdict read as medical advice; user harmed | Low | **Critical** | Fixed vocabulary enforced in code; excluded populations; notices | Product ⚖️ |
| R-06 | Query-success too low; users churn on "not found" | **High** | High | Seed against real baskets; OCR fallback; never dead-end | Founder |
| R-07 | Incumbent ships the feature | Med | High | Not defensible by speed. Trust + specific workflow + payer relationship | — |
| R-08 | FSSAI mandate makes the score redundant | Med | Low | Display the regulatory rating; personal fit sits on top | Product |
| R-09 | Data-entry cost overruns | Low | Med | Scope capped at ~2,000 SKUs; no category-complete coverage in V1 | Founder |
| R-10 | Vision model misreads a label into a wrong verdict | Med | High | Mandatory human confirmation before verdict; never auto-publish OCR output to the catalogue | Eng |
| R-11 | Founder capacity exceeded | Depends on A1 | High | Cut the control cohort before cutting instrumentation | Founder |
| R-12 | User in distress uses the product for restriction | Low | **Critical** | No numeric targets for restriction-framed goals; no weight targets for minors; route to professional support | Product |

---

## 6. Pre-launch checklist ⚖️

- [ ] Privacy policy and consent flows reviewed by an Indian privacy lawyer
- [ ] Consent ledger implemented — per purpose, per version, per principal
- [ ] Withdrawal path tested, including partial withdrawal within a household
- [ ] Verdict vocabulary enforced in code, not in guidelines
- [ ] Medical-advice notice on every conditions-touching screen
- [ ] Excluded-population logic implemented (pregnancy, minors, clinical conditions)
- [ ] Evidence pack format defined: product version + source + capture date + rule version
- [ ] Open Food Facts attribution and contribution pipeline live
- [ ] No advertising, ranking-payment, or data-sale capability exists in the codebase
- [ ] Breach and correction escalation paths written down

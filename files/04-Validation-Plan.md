# 04 — Validation Plan & Metrics

**Project PANTRY | v0.1 | September 2026**

---

## 1. The three questions

| Track | Question | Cost to run | Start |
|---|---|---|---|
| **C — Payer** | Will a third party pay for the outcome? | Founder time + a clickable mock | **Week 3** |
| **A — Consumer** | Will a defined segment keep using it? | Build V1.0 | Week 8 |
| **B — Behaviour** | Does it change what households buy? | Instrumentation inside V1.0 | Week 8 |

**Track C runs first because it is nearly free and its answer reshapes the other two.** A diagnostics chain that says yes will tell you which conditions, which categories, and which patient moment — which changes what you seed and what you build. Running C last, after two months of engineering, wastes the cheapest information available to you.

---

## 2. Track C — Payer validation (weeks 3–10)

**Instrument:** V0 clickable mock. Forty products, hardcoded, five screens. Two weeks of work.

**Targets:** 5 diagnostics chains · 5 diabetes or endocrinology clinics · 5 mid-size employers with existing wellness spend. Fifteen conversations.

**The ask is not "would you be interested."** That question returns a yes from everyone and means nothing. The ask is:

> "We'd like to run a paid 90-day pilot with 200 of your post-report patients. Here's the price. Can we get an LOI?"

**Price it.** A number forces a real answer. Suggested anchor: ₹150–250 per enrolled patient for 90 days. If they negotiate the number, you have a buyer. If they will not discuss the number, you have a compliment.

**What to record from every conversation:** who owns the budget · what they are currently measured on · what they send patients today · what would make this a renewal · what they would need to see to sign · the objection.

**Gate C (day 60):** ≥1 signed paid pilot or a priced LOI.
- **Pass** → build V1.0 shaped around that payer's population.
- **Fail, but ≥3 conversations reached a pricing discussion** → continue to A/B, re-approach with usage evidence.
- **Fail, with no pricing discussion in 15 conversations** → there is no B2B2C business here in this form. Consumer-only, small ceiling. Decide knowingly.

---

## 3. Tracks A & B — Consumer and behaviour (weeks 8–18)

**Cohort 1 — Beachhead, n≈120.** Adults with a metabolic diagnosis or abnormal lab result in the last 90 days. Recruited through the clinics you met in Track C, a nutrition creator, or diabetes support communities.

**Cohort 2 — Control, n≈80.** General health-motivated adult household buyers. No diagnosis.

**Why a control:** if both cohorts retain identically, pain is not doing the work and your beachhead reasoning is wrong. That is worth knowing before you build a go-to-market around it.

**Recruitment rules:** no payment for continued use — it corrupts the retention signal entirely. A small one-time onboarding incentive is fine. Recruit from real channels, not friends and family; friendly users produce flattering, useless data.

---

## 4. North star and supporting metrics

**North star: Weekly Resolved Decisions (WRD)** — product lookups that reached a recorded outcome, per active household per week.

This is deliberately not scans. Ten million scans with no behaviour change is a content product. WRD only counts when someone actually decided something and told you what they did.

| Metric | Definition | Target | Why |
|---|---|---|---|
| **W8 retention** | % of cohort with ≥1 lookup in week 8 | **≥25%** beachhead | Category norm is ~20% at 12 weeks; you must beat it or pain isn't working |
| **Query-success rate** | Lookups resolved to a full nutrition panel | **≥70%** catalogue, ≥90% incl. OCR | Below this, nothing else is measurable |
| **Outcome capture rate** | Product views reaching an S6 answer | **≥40%** | Your measurement instrument's own reliability |
| **Alternative view rate** | Views where user opened Better Options | ≥30% | Intent signal |
| **Substitution acceptance** | Of alternatives viewed, % where user reports buying the alternative | **≥15%** | **The thesis metric** |
| **Repeat substitution** | % of accepted substitutions repurchased within 60 days | ≥40% | Distinguishes a nudge from a habit |
| **Household expansion** | % of households adding a second consenting adult | ≥20% | Tests whether "family" is real or a hypothesis |
| **Median lookups/active week** | | ≥3 | Below this it's not a shopping-moment tool |

**On the household metric specifically:** every additional adult requires their own install and their own consent. Expect this to convert badly. If it lands under 10%, the family thesis is not supported and you have a single-user product — which changes the whole positioning and should be treated as a finding, not a bug to fix with better onboarding copy.

---

## 5. Event taxonomy

Instrument this from day one; retrofitting analytics after a cohort has run means the cohort is wasted.

```
app_opened            { source: cold|notification|share }
lookup_started        { mode: search|barcode|photo }
lookup_resolved       { mode, product_id, version_id, latency_ms,
                        source: catalogue|barcode|ocr }
lookup_failed         { mode, query_text_hash }
verdict_shown         { product_id, profile_id, verdict, rule_ids[] }
why_expanded          { product_id, profile_id }
alternatives_viewed   { product_id, count_shown }
alternative_selected  { from_product_id, to_product_id }
outcome_recorded      { product_id, outcome: original|alternative|neither }
correction_submitted  { product_id, field }
household_invite_sent  / household_member_joined
profile_created       { goals[], has_conditions: bool }
consent_granted       { purpose, version }
```

Two rules: hash any free text before storage, and log `rule_ids[]` on every verdict so you can reconstruct exactly why the product said what it said.

---

## 6. Gates

**Day 60 — Gate C.** As above.

**Day 90 — Gate A.**
- W8 beachhead retention ≥25% → continue
- 15–25% → continue only if substitution is strong; the product works for fewer people than hoped
- <15% → stop building; diagnose whether it's data coverage or value

**Day 120 — Gate B, the decision point.**

| Retention | Substitution | Payer | Decision |
|---|---|---|---|
| Pass | Pass | Pass | **Build.** Raise or commit properly |
| Pass | Pass | Fail | **Lean utility.** Small team, small ceiling, no data expansion. Legitimate — choose it deliberately |
| Pass | Fail | Either | **Not a behaviour product.** Consider content, tooling, or B2B data |
| Fail | — | Pass | Payer wants the outcome, users won't self-serve. Rebuild inside the payer's workflow |
| Fail | Fail | Fail | **Stop.** |

---

## 7. Qualitative work, in parallel

Numbers at n=200 will be noisy. Run these alongside:

- **10 shop-alongs.** Watch someone actually use it while buying — in a store *and* inside a quick-commerce app. This is the fastest way to settle the camera-versus-cart question empirically instead of by argument.
- **Weekly churn interviews.** Five people who stopped, every week. Ask what they were doing instead, not why they left.
- **Every wrong verdict, reviewed by you personally.** For the first 90 days, no exceptions. A wrong verdict is the failure mode that destroys the only asset you have.

---

## 8. Anti-patterns

- Counting downloads or scans as progress.
- Paying users to stay, then reading retention.
- Recruiting friends.
- Adding features when retention is weak. Weak retention is a signal about value, and features are how founders avoid hearing it.
- Running Track C last.
- Treating a "very interesting, send me a proposal" as a pass on Gate C.

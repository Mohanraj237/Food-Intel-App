# Post-MVP Backlog

The only sanctioned landing place for ideas that are not in the MVP. Adding an item here is free; moving one into active scope requires a decision-log entry and a change to `requirements-traceability.md`.

---

## Release 0.2 — the next honest increment (Pack 04 "Should have")

| Item | Why it waits | Entry condition |
|---|---|---|
| Saved products and comparisons | Not required to test the thesis | Cohort asks for it |
| Recent-scan history beyond the current shallow list | Same | Retention signal exists |
| Product formulation-change notifications ("reformulated on…") | Needs version history that only accumulates with time | ≥6 months of version data |
| Hindi UI | D-019; architecture is ready, translation is the work | Cohort composition justifies it |
| Correction queue ops console | MVP has the queue; the console is manual for now | Correction volume exceeds manual handling |
| Simple weekly packaged-food insights | Risks becoming the dashboard the product deliberately isn't | Only if it demonstrably drives a better decision |
| Basket analysis (select 5–20 products → substitutions) | Pack 04 R0.2; needs category-complete data | Category coverage in ≥3 categories |
| Receipt upload | Purpose-limitation and minimisation questions unresolved | Privacy counsel sign-off ⚖️ |

## Release 1+ (Pack 04 "Later")

Legitimate order-history integrations · low-friction meal capture by voice or photo · Indian recipe intelligence · household nutrition trends · B2B2C partner dashboards · clinical/diagnostic integrations where legally appropriate · commerce deep links · regional languages beyond Hindi · elder profiles requiring proxy management ⚖️ · FSSAI Indian Nutrition Rating display (blocked on a consumable data feed) · native app shell (only if D-006's reversal condition triggers).

## Do not build until validated (Pack 04, PANTRY, brief §30)

> **Partly superseded by D-034.** Nutrition logging, activity, sleep and hydration moved
> into scope as Phase 2 of the wellness platform. The rest of this list stands.

Full calorie diary · exercise or wearable tracking · sleep · meditation · social network or community feed · doctor marketplace · telemedicine · pharmacy · insurance claims workflows · broad "all diseases" personalisation · child engagement or gamification · cart optimisation requiring price and stock by pin code · full grocery marketplace or checkout.

## Permanently excluded — not a backlog, a boundary

These are not "later". They are architectural commitments that must survive revenue pressure (D-012, PANTRY 01 §5, Constitution §30):

- Retail media, sponsored ranking, paid placement in health recommendations
- Advertising of any kind; profiling for advertising
- Sale of individual or household consumption data
- Any capability directing advertising, engagement mechanics or notifications at a child
- Weight, body-fat or body-composition targets for a minor — a child-safety commitment independent of law
- Diagnosis, treatment, prescription or medical triage

The corresponding code paths do not exist and an architecture test asserts their absence. Adding any of them is a decision-log entry that reverses D-012, not a backlog groom.

## SUPERSEDED 2026-09-12 by D-034 — "family health & wellness platform" request

> **Status: superseded the same day.** The assessment below was written while the narrow
> packaged-food scope was still current, and it is kept for the reasoning, not the verdict.
> The scope change was subsequently taken deliberately as **D-034**, with **D-035** to
> **D-040** covering the architecture, the age gate, interpretation, visibility, meal logging
> and the deferred wellness score. Where this section and D-034 disagree, D-034 wins.
>
> What did **not** change: the "Permanently excluded" boundary below still stands, and
> D-034 restates it explicitly. Weight and body metrics became available **for adults only**
> (D-036); every child-safety and diagnosis boundary is unchanged.

A request arrived to extend this product into a full family health platform in the
HealthifyMe mould: weight/BMI/body-fat tracking, blood pressure, glucose, SpO2, steps,
workouts, sleep, hydration, medications, appointments, uploaded medical reports with OCR,
streaks, badges, family leaderboards, and child activity tracking with reminders.

Recorded here rather than actioned, because most of it is not a backlog groom:

| Requested | Disposition | Reference |
|---|---|---|
| Weight, BMI, body fat, waist targets | **Excluded by decision.** The goal vocabulary is a closed 7-value enum with a mirrored DB `CHECK`; no weight or body-composition goal is representable for any profile | D-017, AC-3.7, `GoalCode.java` |
| Weight/body-composition targets for a minor | **Permanent boundary**, independent of law | post-mvp-backlog "Permanently excluded" |
| Streaks, badges, family leaderboards | **Excluded by decision** — engagement mechanics | AC-3.7 |
| Child activity/sleep/step tracking, reminders to a child | **Permanent boundary.** Structurally impossible today: `activity_event.account_id` is a user account, and a `CHILD` has no account, so a child cannot be the subject of an activity event by schema | CH-004, CH-005, D-001/D-002 |
| Blood pressure, glucose, SpO2, temperature, heart rate | Medical measurement capture; adjacent to the diagnosis/triage boundary and needs clinical and privacy review before any schema exists | "Diagnosis, treatment, prescription or medical triage" is permanently excluded |
| Medications, dosage, adherence tracking | Not in scope; "no recommendation of medication changes" is a hard safety line and adherence data is sensitive-category health data | ⚖️ privacy + clinical counsel |
| Appointments, health records, report OCR | Legitimate future scope but a new domain with its own DPDP posture; OCR also blocked on an AI provider credential | Release 1+ / "Deferred with a specific external dependency" |
| Calorie diary, exercise/wearable tracking, sleep, meditation | Already listed under "Do not build until validated" | post-mvp-backlog |
| Household nutrition trends, family dashboards | Already Release 1+; "Simple weekly packaged-food insights" carries the note that it *risks becoming the dashboard the product deliberately isn't* | post-mvp-backlog R0.2/R1+ |

**What was built instead** (2026-09-12): a household readiness overview on `/family`
(`HouseholdOverview.tsx`, `useHouseholdOverview.ts`). It answers "how is my household
doing?" in the terms this product can answer honestly — who can receive a personal result,
what is blocking each person who cannot, what the household is collectively watching, and
the union of declared allergens for shopping. It composes existing endpoints only
(`/households/{id}/members`, `.../profile`, `/consent/*`) and introduces no new metric.

**Entry condition for the rest.** Any of the above moving into scope is a decision-log entry
that reverses D-017 and/or D-012 plus a change to `requirements-traceability.md` — and, for
the child-related items and medical measurements, clinical and privacy counsel sign-off ⚖️.
The most valuable next increment remains Phase 4 (an active scoring ruleset), because the
existing personal-result feature currently returns "Not enough data yet" for every input.

---

## Deferred with a specific external dependency

| Item | Blocked on |
|---|---|
| Phone/OTP authentication | SMS provider contract (D-008) |
| DPDP-verifiable parental consent mechanism | Indian privacy counsel ⚖️ (D-001) |
| Live OCR and AI explanation | AI provider credential |
| Growing the dev catalogue past 893 products toward the 5000+ target | Open Food Facts API outage spanning the feature-completion sprint (2026-09-06) — the import (`tools/data-import/`, D-031) is built, idempotent, and resumable; this is a re-run, not new engineering, once the source recovers |
| Open Food Facts **contribute-back** pipeline (submitting corrections upstream to OFF) | Distinct from the import, which now exists (D-031) — contribute-back is separate, later work with its own ODbL share-alike considerations |

# Wellness Platform Architecture

Target architecture for the individual + family scope accepted in **D-034**. This document
is the scope authority for the pivot; where it conflicts with `docs/product/product-policy.md`
or `docs/engineering/implementation-plan.md`, both of which were written for the narrow
packaged-food product, this document wins. It does **not** override
`docs/decisions/decision-log.md` (D-034 §"What this decision explicitly does not do") or the
"Permanently excluded" boundary in `docs/product/post-mvp-backlog.md`.

---

## 1. The four questions

Every screen in the product exists to answer one of these, and a feature that answers none
of them is not in scope:

| Question | Answered by | Subject |
|---|---|---|
| How am I doing? | Individual dashboard | one member |
| How is my family doing? | Family dashboard | household, filtered by visibility |
| What changed? | Insights over observation history | either |
| What should I do next? | Goal progress + suggestions (never medical advice) | either |

---

## 2. Domain model

The health subject is `household_member`, unchanged (**D-035**). Individual Mode is a
household of one. Nothing in this architecture introduces a second person model.

```
user_account ──1:0..1── household_member ──N:1── household
                   │                               └── is_individual  (D-035)
                   ├──1:N── person_profile (versioned; age_band = safety gate, D-036)
                   ├──1:N── health_observation      ◄── Phase 1
                   ├──1:N── metric_visibility        ◄── Phase 1  (D-038)
                   ├──1:N── wellness_goal            ◄── Phase 2
                   ├──1:N── habit / habit_entry      ◄── Phase 2
                   ├──1:N── meal_entry ──N:1── meal  ◄── Phase 2  (D-039)
                   ├──1:N── health_record            ◄── Phase 4  ⚖️
                   ├──1:N── medication ──1:N── dose  ◄── Phase 4  ⚖️
                   └──1:N── appointment              ◄── Phase 4

guardian_link: (guardian household_member) ──► (child household_member)   [existing]
consent_record: per (subject, purpose, policy_version)                    [existing]
audit_event:    every cross-member read of sensitive data (D-038)         [existing]
```

### 2.1 Why one observation table, not one table per tracker

Weight, steps, sleep duration, water, systolic BP and SpO2 are all *(subject, metric, value,
unit, time, source)*. Modelling them as six tables means six repositories, six permission
checks, six insight code paths, and a seventh migration the first time a wearable adds a
metric. One typed observation table with a closed metric enum gives every tracker the same
permission enforcement, the same history query, and the same insight pipeline for free.

The brief's own warning — *"avoid building temporary structures that will need to be
completely replaced later"* — is the reason this is Phase 1 rather than Phase 2. Phase 2's
trackers are then UI and metric definitions, not new architecture.

### 2.2 What the observation table deliberately does not have

- **No derived category column.** No `bmi_category`, no `bp_category`. Interpretation needs a
  citable standard (**D-037**); a column would invite one to be invented.
- **No goal or target column.** Goals are their own entity with their own age gate (D-036).
- **No free-text note on a clinical metric.** A note field on a blood-pressure row becomes a
  symptom diary, which is a medical record with a different legal posture.

---

## 3. Metric taxonomy and the age gate

Each metric code declares its category, unit, and the age classes it is permitted for. The
enum constructor requires `permittedFor`, so a metric cannot be added without deciding
(**D-036**).

| Category | Metrics | Adult | Child | Notes |
|---|---|---|---|---|
| `BODY` | weight, height, BMI, waist, body-fat % | ✅ | ⛔ | D-036: refused in the domain for child bands, not hidden in UI |
| `ACTIVITY` | steps, active minutes, distance, workout duration | ✅ | ✅ | Child: no streaks, badges, ranking or notifications (D-036) |
| `SLEEP` | duration, bedtime, wake time | ✅ | ✅ | |
| `HYDRATION` | water volume | ✅ | ✅ | |
| `NUTRITION` | energy, protein, carbohydrate, fat, fibre, sugar, sodium | ✅ | ✅ | Written by Phase 2 meal logging, not entered directly |
| `CLINICAL` | systolic, diastolic, resting HR, glucose, SpO2, temperature | ✅ | ⛔ | Capture only; no interpretation without a standard (D-037) |
| `WELLBEING` | mood, stress | ✅ | ✅ | Self-report only |

`height` sits in `BODY` and is therefore adult-only, which also closes the obvious
side-channel: recording a child's height and weight to derive a percentile is exactly the
growth tracking D-036 puts out of scope.

---

## 4. Permission model (D-038)

Three independent questions, evaluated in this order. A "no" at any step is final.

1. **Is the metric permitted for this subject's age band?** → `MetricCode.permittedFor`
2. **Does the actor have a relationship to the subject?** → self, or `guardian_link`
3. **Has visibility been granted for this category to this audience?** → `metric_visibility`

```
actor reads subject's BODY observations
  ├─ actor == subject                      → allow
  ├─ guardian_link(actor → subject)        → allow if category permitted for age (D-036)
  ├─ metric_visibility grant exists        → allow, write audit_event
  └─ otherwise                             → 403, write audit_event(outcome=DENIED)
```

Default-closed: absence of a grant is a denial. Household `role` is not consulted — `OWNER`
is not a health-data role.

---

## 5. Module map

Extends the existing modular monolith (D-006, D-021: one Maven module, ArchUnit boundaries).

| Module | Status | Responsibility |
|---|---|---|
| `identity`, `household`, `consent`, `profile` | existing | accounts, membership, guardianship, consent ledger, age band |
| `catalog`, `scoring`, `recommendation`, `capture` | existing | packaged-food lookup, per-member fit, alternatives |
| **`wellness`** | **Phase 1 — new** | metric definitions, observations, visibility grants, age enforcement |
| `nutrition` | Phase 2 | meals, portions, family meal apportioning → writes `NUTRITION` observations |
| `goals` | Phase 2 | goals and habits over metric categories |
| `insights` | Phase 3 | derives statements from observation history; owns no data |
| `records` | Phase 4 ⚖️ | documents, medications, appointments |

`wellness` reaches `household`/`profile` only through a published interface, mirroring
**D-023**'s `HouseholdAccess` pattern — never a shared repository or entity.

---

## 6. Insight architecture (Phase 3)

Insights are **derived, never stored as fact**. The pipeline reads observations and emits
typed statements; it has no table of its own beyond a cache.

```
observations ─► window (today | week | month | long) ─► comparator (vs previous window,
                                                        vs the user's own goal)
             ─► typed Insight { metricCategory, direction, magnitude, windowe, confidence }
             ─► locale template ─► rendered sentence
```

Every insight carries the three-part shape the brief asks for:

- **What happened** — from observation data only.
- **Why it matters** — from the user's own stated goal, never an invented norm.
- **What to consider** — a suggestion bounded by the user's goals; never medical advice, never
  a medication change (D-037).

Insufficient data is a first-class output. An insight with fewer than the required
observations in a window renders as coverage ("4 of 7 days logged"), not as a trend — the
same posture as `INSUFFICIENT_DATA` in scoring (D-024).

**AI boundary (D-011, unchanged).** The intelligence layer may summarise and phrase insights
computed by this pipeline. It may not originate a number, threshold, category or verdict, and
it is never on the path that decides whether data may be read.

---

## 7. Roadmap and dependencies

| Phase | Contents | Depends on | Gate |
|---|---|---|---|
| **1 — Foundation** | `wellness` module: metric taxonomy + age gate, `health_observation`, `metric_visibility`, observation API, Individual Mode marker | — | Age gate refused in domain with a test; default-closed visibility proven |
| **2 — Tracking** | Nutrition logging + family meals (D-039), activity, sleep, hydration, goals, habits | 1 | Every tracker writes through `wellness`; no new observation path |
| **3 — Insights** | Individual + family insights, four timeframes, coverage-aware | 2 (needs history) | No insight without the data to support it |
| **4 — Health management** | Records, medications, appointments | 1, and ⚖️ privacy/clinical signoff | Legal review of sensitive-category storage |
| **5 — Intelligence** | AI summaries, NL queries, recommendations | 3, AI provider credential | D-011 enforced structurally |
| **6 — Differentiation** | Family challenges, premium, integrations | 2, 3 | Child exclusion from ranking (D-036) |

**Cross-cutting, still blocked:** DPDP-verifiable parental consent ⚖️ (D-001) gates child data
at scale. Clinical interpretation ⚖️ (D-037) gates every "is this normal?" question. Neither
blocks Phase 1.

**Sequencing note.** Phase 4 is *not* last by value — records and appointments are the
strongest elderly-mode features and the most requested. It is fourth because it needs legal
review that Phase 1–3 do not, and it is the only phase that can start in parallel once that
review lands.

---

## 8. What Phase 1 deliberately leaves out

So that a reader does not mistake absence for oversight:

- No dashboards. A dashboard over an empty observation table is the fake dashboard the brief
  forbids. Dashboards arrive in Phase 2 with data behind them.
- No goals or habits. They are Phase 2 and need the age gate that Phase 1 establishes.
- No wellness score (**D-040**, deferred with reasons).
- No clinical interpretation (**D-037**).
- No records, medications or appointments (Phase 4, ⚖️).

# Wellness Platform Architecture — v2

**Individual + family health and wellness, with personalised food decisions at its core.**

- **Version:** 2.0 (2026-09-12). v1 covered only D-034's first pass.
- **Scope authority:** this document is the scope authority for the pivot (D-034, D-042). Where it conflicts with `docs/product/product-policy.md` or `docs/engineering/implementation-plan.md` (both written for the narrow packaged-food product), this document wins.
- **What it does not override:** `docs/decisions/decision-log.md`. Every safety boundary restated in D-034 and D-042 stands.

Status vocabulary (CLAUDE.md §8): `IMPLEMENTED` · `PARTIAL` · `NOT_STARTED` · `MOCKED` · `BLOCKED` · `DEFERRED` · `EXCLUDED`.

---

## 1. What the product answers

Every screen answers one of four questions. A feature that answers none of them is not in scope.

| Question | Answered by | Subject | Data rule |
|---|---|---|---|
| **How am I doing?** | "Me" lens: Today, trackers, goals | the caller's own member | everything they recorded |
| **How is my family doing?** | "Family" lens: household overview | each member, filtered | only what the access policy allows (D-044) |
| **What changed?** | Insights over history, four timeframes | either | own-baseline comparisons, never population norms (D-037) |
| **What should I do next?** | Goal progress, suggestions, reminders | either | bounded by the person's own goals; never medical advice |

The packaged-food decision capability sits inside the first two questions: *Search product → View product → Evaluate for me → Evaluate for a family member → Understand the reason → View the source.* It is the product's differentiator, not a side feature.

---

## 2. Current state (verified 2026-09-12, not inferred from docs)

| Area | Status | Evidence / gap |
|---|---|---|
| Packaged-food lookup, search, scan, product detail, compare | IMPLEMENTED | `catalog`, `search`, `scoring`, `recommendation`; ~894 real products |
| Personal / household food fit | IMPLEMENTED (allergen dimension real; nutrient rules empty by design, D-024) | `scoring` |
| Household, invitations, children, guardian links, consent ledger, profiles | IMPLEMENTED | `household`, `consent`, `profile` |
| Observation store (28 metrics, age gate, default-closed sharing) | IMPLEMENTED (backend); no sharing UI | `wellness` |
| Meals with family apportioning (D-041) | IMPLEMENTED (backend); UI in progress | `nutrition` |
| Goals, habits | IMPLEMENTED (backend); UI in progress | `goals`, `habits` |
| Insights | PARTIAL: 6 trend detectors, WEEK only in UI; English prose from the server (violates D-019) | `insights` |
| Individual Mode (D-035) | PARTIAL: column only; nothing sets `is_individual` | fixed in Phase 1 (D-043) |
| Caregiver / elderly | NOT_STARTED | Phase 1 (D-045, D-046) |
| Consent for wellness tracking | **Missing**: no purpose, and child tracking contradicts `CHILD_FOOD_SUITABILITY` | Phase 1 (D-048) |
| Erasure/export of health data | **Missing**: observations, goals, habits and portions survive erasure | Phase 1 |
| Meal read authorization | **Defect**: household membership reveals other adults' solo meals | Phase 1 (D-044) |
| Records, medications, appointments, notifications, AI, challenges | NOT_STARTED | Phases 2–6 |

---

## 3. Product modes — lenses, not accounts (D-043)

```
                    ┌──────────── one account ────────────┐
                    │                                     │
          "Me" lens │ my member → my data                 │  always available
      "Family" lens │ household → members → what I may see│  meaningful at ≥2 members
                    └─────────────────────────────────────┘
```

- **Onboarding** asks once: *Just me* (`POST /api/v1/me/individual` → a household of one, `is_individual = true`) or *Me and my family* (an ordinary household).
- **A second member joining** (invitation accepted, or child added) flips `is_individual` to false. No data moves.
- **A solo user is never nagged** toward adding family (AC-2.7).

---

## 4. Domain model

The health subject is always `household_member` (D-035). There is no second person model.

```
Users ──► Family ──► Members ──► Health data ──► Tracking ──► Goals ──► Insights ──► Actions

user_account ─1:N─ household_member ─N:1─ household (is_individual, time_zone)
     │                  │
     │                  ├─ person_profile (versioned; age_band → experience tier, D-046;
     │                  │                   activity_level; sex; diet; allergens; conditions)
     │                  ├─ guardian_link (guardian member → CHILD member)          [existing]
     │                  ├─ health_access_grant (subject → audience, scope, level)  [P1, D-044]
     │                  ├─ consent_record (subject, purpose, policy version)       [existing]
     │                  │
     │                  ├─ health_observation (metric, value, unit, effective_at,  [existing]
     │                  │                      source, source_ref, recorded_by)
     │                  ├─ meal_portion ─N:1─ meal (household) ─1:N─ meal_item      [existing]
     │                  ├─ wellness_goal (metric, direction, target, period)       [existing]
     │                  ├─ habit ─1:N─ habit_completion                             [existing]
     │                  ├─ notification_preference, notification (inbox)           [P2]
     │                  ├─ reminder (schedule rule → notifications)                [P2/P4]
     │                  ├─ health_record ─1:N─ record_file (private object store)  [P4 ⚖️]
     │                  ├─ medication ─1:N─ medication_schedule, dose_log          [P4 ⚖️]
     │                  └─ appointment                                             [P4]
     │
     └─ audit_event (actor, action, resource, subject_member_id [P1], outcome)     [existing]

household ─1:N─ food_item / recipe (household library)                             [P2]
household ─1:N─ meal_plan, grocery_list                                            [P6]
household ─1:N─ challenge ─1:N─ challenge_participant (adults only)                [P6]
```

### 4.1 Why one observation table

Weight, steps, sleep, water, blood pressure and SpO2 all have the same shape: *(subject, metric, value, unit, time, source)*. With one typed table and a closed metric enum:
- every tracker inherits the same age gate, access check, history query and insight pipeline;
- a wearable adding a metric is an enum entry, not a migration.

This stays the rule: **no tracker gets its own observation path** (Phase 2 gate).

### 4.2 What the observation table deliberately lacks

- **No derived category column.** Interpretation needs a standard (D-037).
- **No target column.** Targets live in goals.
- **No free-text note on clinical metrics.** A note on a clinical reading turns it into a symptom diary.
- **Metric enum changes.** Future additions such as `ACTIVE_ENERGY_KCAL` (device- or user-entered, never estimated by us) are enum entries with an explicit `permittedFor`.

---

## 5. Actors, relationships and experience tiers

### 5.1 Relationship of a caller to a subject (D-044, D-045)

| Relationship | How it arises | Default scopes | Can grant onward? |
|---|---|---|---|
| **SELF** | the caller *is* the member | all, MANAGE | the only relationship that can grant |
| **GUARDIAN** | live `guardian_link` to a CHILD member | all, MANAGE (age gate still applies) | no: child data is never grantable |
| **CAREGIVER** | the subject granted ≥1 MANAGE scope | the granted scopes only | no |
| **SHARED** | the subject granted VIEW scopes only | the granted scopes only | no |
| **NONE** | any other household member | none; name and presence only | no |

The household role (`OWNER`/`ADULT`/`DEPENDENT`) is **never** an input to this table.

### 5.2 Experience tiers (D-046)

Derived on the server and returned with capability flags. The UI renders the flags and never infers them.

| Capability | CHILD | ADULT | OLDER_ADULT | UNKNOWN (no profile) |
|---|---|---|---|---|
| Nutrition, activity, sleep, hydration, wellbeing tracking | ✅ guardian-entered | ✅ | ✅ | ⛔ until profile |
| BODY metrics (weight, BMI, waist, body fat, height) | ⛔ domain-refused | ✅ | ✅ | ⛔ |
| CLINICAL metrics (BP, HR, glucose, SpO2, temp): capture only | ⛔ | ✅ | ✅ | ⛔ |
| Body goals | ⛔ | ✅ no deadline/rate (D-047) | ✅ | ⛔ |
| Habits | ✅ | ✅ | ✅ | ✅ |
| Streaks / badges / challenges | ⛔ permanent | opt-in, P6 (D-051) | opt-in, P6 | ⛔ |
| Notifications to the subject | ⛔ permanent (guardian receives) | opt-in (D-050) | opt-in | opt-in |
| Records, medications, appointments | ✅ guardian-managed (P4) | ✅ (P4) | ✅ **home priority** (P4) | ✅ |
| Caregiver invitation surfaced | — | available | **prominent** | available |
| Larger-type layout default | — | off | offered | off |

Two rules the table encodes:
- **Child is not a smaller adult.** Capabilities are removed and copy changes (encouraging, non-evaluative). The layout is not simply shrunk.
- **Older adult is not a restriction tier.** Age 60+ changes ordering and emphasis only.

---

## 6. Permission model (D-044)

### 6.1 Scopes and levels

| Scope | Covers | Notes |
|---|---|---|
| BODY, ACTIVITY, SLEEP, HYDRATION, NUTRITION, CLINICAL, WELLBEING | observations in that category, and goals on metrics in that category | mirrors `MetricCategory`; a test asserts the two enums stay in step |
| HABITS | habits and completions | own scope because a habit name can be sensitive |
| FOOD_PROFILE | allergens, dietary preference, food goals | enables "evaluate this product for Grandma"; never includes conditions |
| MEDICATIONS, APPOINTMENTS, RECORDS | Phase 4 domains | defined now so the grant model does not change later |

- **VIEW:** read.
- **MANAGE:** record, log, or act on the subject's behalf in that scope. It implies VIEW.

### 6.2 Evaluation (one implementation, `access.application.HealthAccessPolicy`)

```
decide(caller, subject):
  subject unknown/departed           → NOT_FOUND
  caller == subject.account          → SELF       {all: MANAGE}
  subject CHILD ∧ guardianOf(caller) → GUARDIAN   {all: MANAGE}
  grants(subject → caller's member)  → CAREGIVER (any MANAGE) | SHARED (VIEW only)
  otherwise                          → NONE       {}

requireView(caller, subject, scope)   → allow | 403 + audit(DENIED)
requireManage(caller, subject, scope) → allow | 403 + audit(DENIED)
every allowed cross-member access     → audit(SUCCESS, subject_member_id)
```

- **Age gate.** The age gate (D-036) is a separate check on the metric and runs after access. MANAGE never makes a metric permitted for a child.
- **Consent.** Consent (D-048) is a separate check that runs at processing time:
  - writes need `HEALTH_TRACKING` (adult) or `CHILD_WELLNESS_TRACKING` (child);
  - a cross-member read needs the subject's purpose to be active.

### 6.3 Meals

- **Visibility.** A meal is visible to its logger and to every member served in it, as long as the caller can see the nutrition of at least one served member.
- **Portion nutrition.** Within a visible meal, a portion's nutrition is returned only to that member, their guardian, or a NUTRITION viewer, and is otherwise `null` with `nutritionWithheld: true`.
- **Derivation.** Unchanged from D-041. Nutrition is derived immediately for members the logger may MANAGE for NUTRITION whose tracking consent is active. Other adults confirm their own portion.

### 6.4 Access log

- `GET /api/v1/members/{id}/access-log` returns the audit events where someone *other than the subject* read or wrote the subject's health data.
- It is visible to the subject, and to the guardian for a child.
- It is the trust feature that makes sharing safe to offer.

---

## 7. Module map (one Maven module; ArchUnit boundaries, D-021)

| Module | Status | Owns | May depend on (published application interfaces only) |
|---|---|---|---|
| `platform`, `identity`, `audit`, `household`, `consent`, `profile` | existing | accounts, membership, guardianship, consent, profiles | — |
| `catalog`, `search`, `scoring`, `recommendation`, `capture`, `activity` | existing | packaged-food intelligence | — |
| **`access`** | **P1 new** | `health_access_grant`, `HealthAccessPolicy`, access log | household, audit |
| `wellness` | existing | metric taxonomy, observations, age gate | access, consent, household, profile |
| `nutrition` | existing | meals, portions, D-041 derivation | access, wellness, catalog, household |
| `goals`, `habits` | existing | targets, habit log | access, wellness, household |
| `insights` | existing, P3 rework | derived statements, no tables | wellness, goals, habits, nutrition (read APIs) |
| **`experience`** | **P1 new** | `GET /me`, tiers + capabilities, household-of-one provisioning | household, profile, access |
| **`dashboard`** | **P1 new** | today summary, family overview (read-only composition, no tables) | access, wellness, goals, habits, nutrition, household, experience |
| `datarights` | existing, P1 extended | export and erasure across all health modules | every module's erase/export API |
| `notifications` | P2 | preferences, inbox, reminder scheduler | access, household, experience |
| `library` | P2 | household foods and recipes | catalog, household |
| `records` | P4 ⚖️ | records, medications, appointments | access, consent, notifications, platform.storage |
| `ai` | P5 (MOCKED) | phrasing adapter, intent router | insights (facts only), never `scoring`/`access` |
| `challenges`, `planning` | P6 | cooperative challenges, meal plans, grocery lists | access, insights, library |

Rules added to `ModuleBoundaryTest` in Phase 1:
- `access` depends on no health module.
- No health module touches `household.domain/infra/api` or another module's `infra`.
- Only `access` touches `health_access_grant`.
- `ai` never depends on `access` or `scoring` (D-011).

---

## 8. API map

Existing endpoints are unchanged unless noted. **P1** marks endpoints added in Phase 1.

| Area | Endpoint | Phase |
|---|---|---|
| Me | `GET /api/v1/me` → account, household, member, profile presence, experience tier, capabilities | P1 |
| | `POST /api/v1/me/individual` → provision household of one (idempotent) | P1 |
| | `GET /api/v1/members/{id}/experience` → tier + capabilities for a member the caller relates to | P1 |
| Access | `GET /api/v1/members/{id}/access` → caller's relationship + effective scopes (drives honest UI) | P1 |
| | `GET/POST /api/v1/members/{id}/access-grants`, `DELETE …/{grantId}` → subject's own grants | P1 (replaces `/wellness/members/{id}/shares`) |
| | `GET /api/v1/me/shared-with-me` → grants where the caller is the audience | P1 |
| | `GET /api/v1/members/{id}/access-log` | P1 |
| Dashboard | `GET /api/v1/members/{id}/today?date=` → per-metric day values (SUM vs LATEST), goal progress, habits due/done, meals awaiting confirmation, coverage | P1 |
| | `GET /api/v1/households/{id}/overview?date=` → per member: relationship, visible scopes, today summary for visible scopes only | P1 |
| Wellness | `/api/v1/wellness/metrics`, `/members/{id}/observations[/{metric}]`, `/members/{id}/visibility` | existing (visibility now backed by `access`) |
| Nutrition | `/api/v1/nutrition/meals…` | existing (P1 fixes read authorization) |
| Goals / habits | `/api/v1/goals…`, `/api/v1/habits…` | existing (P1: policy + consent + D-047) |
| Insights | `/api/v1/insights/members/{id}?window=` | existing; P3 adds typed `messageKey` + params and `LONG_TERM` |
| Notifications | `/api/v1/me/notifications`, `/api/v1/members/{id}/notification-preferences`, `/reminders` | P2 |
| Library | `/api/v1/households/{id}/foods`, `/recipes` | P2 |
| Records | `/api/v1/members/{id}/records`, `/medications`, `/doses`, `/appointments` | P4 ⚖️ |
| Intelligence | `/api/v1/members/{id}/summary?period=`, `/api/v1/ask` | P5 (MOCKED phrasing) |
| Challenges / planning | `/api/v1/households/{id}/challenges`, `/meal-plans`, `/grocery-lists` | P6 |

Every endpoint that takes a `members/{id}` path resolves the caller's relationship through `HealthAccessPolicy`. None trusts the client.

---

## 9. Frontend architecture

React + TypeScript PWA (D-006), with the existing design system and locale bundles (D-019).

### 9.1 Navigation (signed in; five items max, per the design system)

| Tab | Lens / question | Contents |
|---|---|---|
| **Today** | How am I doing? | day summary, quick log, goals, habits, insights teaser |
| **Family** | How is my family doing? | member cards (permission-aware), awaiting-confirmation meals, family insights |
| **Log** (centre action) | — | meal, water, activity, sleep, body/clinical (adult), mood, habit check |
| **Food** | the food decision | search, scan, product, evaluate for me / a family member |
| **You** | — | profile, sharing and caregivers, access log, notifications, data rights, settings |

The OLDER_ADULT home reorders Today: reminders, medications and appointments first (P4), then the day summary.

### 9.2 Feature modules by phase

| Phase | Screens |
|---|---|
| P1 | Onboarding (Just me / Me + family) · Today on `/members/{me}/today` · Family overview + member view · Sharing & caregivers · Access log · profile completion (activity level; height/weight as observations for adults) |
| P2 | Log sheet · meals (log, detail, confirm) · tracker detail per category (entry + history) · goals · habits · notification inbox + preferences · my foods / recipes |
| P3 | Insights hub (Today / Week / Month / Long term) · weekly summary · family insights |
| P4 | Records timeline · medications + dose log · appointments · reminders |
| P5 | Ask · AI summary (labelled MOCKED until a credential exists) |
| P6 | Challenges · milestones · meal planner · grocery list · integrations · premium |

### 9.3 Honesty rules the UI inherits

- An unlogged day renders as "not logged", never as `0`.
- A metric the caller may not see renders as "not shared with you", never as blank.
- A capability that doesn't exist yet is not linked. (Two dead links existed: `/meals/new` and `/product/:id/report`.)
- No composite score (D-040). No clinical colour coding (D-037). No numbers invented for empty states.

---

## 10. Event flows

**Record an observation**

```
UI → POST /wellness/members/{id}/observations
   → policy.requireManage(caller, subject, scopeOf(metric))
   → consent: HEALTH_TRACKING | CHILD_WELLNESS_TRACKING active for subject
   → age gate: metric.isPermittedFor(band) ∧ subject not CHILD-typed for adult-only
   → persist (source forced to MANUAL for API writes)
   → audit(HEALTH_METRIC_RECORDED, subject_member_id)
```

**Family meal (D-041 + D-044 + D-048)**

```
POST /nutrition/meals → meal + items + portions stored (household facts)
   for each portion:
     policy.canManage(caller, member, NUTRITION) ∧ consent active → derive observations
     else                                                         → awaiting (derived_at NULL)
   other adult → POST /meals/{id}/confirm → derive as self (audited)
```

**Caregiver setup**

```
elder (SELF) → POST /members/{elder}/access-grants {audience: son, scope: MEDICATIONS, level: MANAGE}
   → validate: caller is subject; audience is an active adult member of the same household
   → persist + audit(ACCESS_GRANTED)
son → GET /me/shared-with-me → sees "Amma: medications (manage)"
son → acts → every action audited with subject_member_id → visible in the elder's access log
```

**Family overview**

```
GET /households/{id}/overview
   for each active member: decision = policy.decide(caller, member)
     NONE → {name, relationship: NONE}                                  (no health data)
     else → today summary over metrics in decision.viewableScopes only,
            goal progress for goals whose metric scope is viewable,
            habits if HABITS viewable
```

**Reminder (P2 hydration/habits, P4 medication/appointments)**

```
reminder rule → DB-polled scheduler (no queue, D-007) → recipient resolution
   subject CHILD → guardians only (D-050)
   → preference + quiet hours + daily cap → notification row (in-app) → [push, later]
```

---

## 11. Insight generation architecture (Phase 3)

Insights are **derived, never stored as fact**. The pipeline owns no tables beyond an optional cache.

```
observations/goals/habits/meals (visible to caller)
   → window: TODAY | WEEK | MONTH | LONG_TERM (90 days, monthly buckets), member-local days (D-053)
   → detectors: own-baseline trend · goal days-met · habit consistency · coverage ·
                meal regularity · sleep timing consistency · adult weight trend (no category)
   → typed Insight { type, scope, metric, direction, magnitude, window, evidence[], suggestionKey }
   → locale template (messageKey + params)  →  rendered by the client (D-019)
   → [P5] optional AI rephrase behind numeric-fidelity check (D-011)
```

Every insight carries the brief's four-part shape, with each part sourced as follows:

| Part | Source | Never |
|---|---|---|
| **What happened** (observed) | evidence rows: values, days, window | a value not in the evidence |
| **Calculated** | own-baseline arithmetic, shown with its inputs | a population norm, a reference range |
| **Why it matters** | the member's own stated goal | an invented norm, a clinical claim |
| **What to consider** | a closed catalogue of non-medical suggestions keyed to goal/scope | medication changes, diagnosis, "see a doctor about X" alarms |

Further rules:
- **Coverage.** Insufficient data is a first-class output ("water logged 3 of 7 days"), with the same posture as `INSUFFICIENT_DATA` in scoring.
- **Family insights** are computed only from scopes the caller can see. They never rank members, and a total over two members never reveals the other member's value unless it is shared (D-038 aggregate rule).

---

## 12. Feature map

The Phase column is the build phase. Status is as of this document.

### 12.1 Individual

| Feature | Phase | Status | Notes / dependency |
|---|---|---|---|
| Profile: name, age band, sex, diet, allergies, conditions, food goals | — | IMPLEMENTED | no DOB by design (CH-006) |
| Profile: activity level (self-reported) | P1 | NOT_STARTED | never used to compute an energy target (D-047) |
| Height / weight / body measurements | P1–P2 | PARTIAL | BODY observations exist; entry UI P2 |
| Body composition, BMI | P2 | PARTIAL | BMI computed arithmetic only, no category (D-037) |
| BP, heart rate, glucose, SpO2, temperature | P2 | PARTIAL | capture only; interpretation ⚖️ BLOCKED |
| Food logging, meals, meal history | P2 | PARTIAL | backend done; UI in progress |
| Calories, protein, carbs, fat, fibre, sugar, sodium | — | IMPLEMENTED (backend) | 7 nutrients |
| Micronutrients | P2+ | NOT_STARTED | limited by catalogue nutrient coverage |
| Recipes, favourite foods (household library) | P2 | NOT_STARTED | no scraped recipes (D-016) |
| Generic Indian food composition | — | BLOCKED | IFCT licensing review (D-042) |
| Packaged-food lookup + personalised evaluation | — | IMPLEMENTED | nutrient rules await D-025 |
| Steps, active minutes, distance, workout minutes | P2 | PARTIAL | metrics + quick log |
| Activity types (walk/run/cycle), sessions | P2 | NOT_STARTED | |
| Calories burned | P2 | NOT_STARTED | capture from device/user only; estimation needs a reviewed standard |
| Sleep duration, bedtime, wake | P2 | PARTIAL | metrics exist |
| Sleep consistency, trends | P3 | NOT_STARTED | |
| Hydration logging, goal | P2 | PARTIAL | |
| Hydration reminders | P2 | NOT_STARTED | notifications (D-050) |
| Goals (fitness, nutrition, hydration, sleep, activity, weight for adults) | P1 guardrail, P2 UI | PARTIAL | D-047 |
| Habits: create, track, complete, trends | P2 | PARTIAL | backend done |
| Habit recommendations | P5 | NOT_STARTED | rule-based (D-052) |
| Progress: weekly, monthly, long-term, improvements, attention areas | P3 | PARTIAL | WEEK trends only |
| Individual dashboard (real data only) | P1 | PARTIAL | server-side today summary in P1 |

### 12.2 Family

| Feature | Phase | Status | Notes |
|---|---|---|---|
| Individual/Family modes, household of one | P1 | PARTIAL → P1 | D-043 |
| Family overview, member status (permission-aware) | P1 | PARTIAL (setup readiness only) | D-044 |
| Member permissions, sharing, caregivers, access log | P1 | NOT_STARTED | D-044, D-045 |
| Age-specific experiences | P1 | NOT_STARTED | D-046 |
| Family meals, shared logging, portions, confirmation | P2 UI | PARTIAL (backend) | D-041 |
| Evaluate a product for a family member (beyond self/children) | P2 | NOT_STARTED | FOOD_PROFILE scope |
| Family nutrition summaries, meal patterns | P3 | NOT_STARTED | visible data only |
| Family goals / challenges / achievements | P6 | NOT_STARTED | cooperative, adults only (D-051) |
| Meal planning, grocery lists | P6 | NOT_STARTED | no commerce (D-012) |
| Upcoming appointments, reminders | P4 | NOT_STARTED | |
| Health records per member, timeline | P4 | NOT_STARTED | ⚖️ release gate (D-049) |
| Member alerts | P2/P4 | NOT_STARTED | reminders only, never clinical alarms |

### 12.3 Health management, intelligence, platform

| Feature | Phase | Status | Notes |
|---|---|---|---|
| Lab reports, prescriptions, vaccinations, scans, documents | P4 | NOT_STARTED | ⚖️ D-049 |
| Medications, schedule, reminders, taken/missed | P4 | NOT_STARTED | ⚖️; no change advice, ever |
| Appointments (doctor, lab, dentist, vaccination, follow-up) | P4 | NOT_STARTED | |
| Personal / family AI summary, weekly AI summary | P5 | NOT_STARTED → MOCKED | no credential |
| Natural-language questions | P5 | NOT_STARTED | closed intents (D-052) |
| Meal / recipe / goal recommendations | P5 | NOT_STARTED | own data + goals, never sponsored |
| Notification framework + preferences | P2 | NOT_STARTED | D-050 |
| Search across food, products, recipes, members, records, appointments, medications | P2–P4 | PARTIAL (products) | permission-filtered |
| Streaks, milestones, badges | P6 | NOT_STARTED | adults, opt-in (D-051) |
| Wearable integrations | P6 | BLOCKED | native shell + credentials |
| Premium | P6 | NOT_STARTED | never gates safety (D-042) |
| Composite wellness score | — | DEFERRED | D-040 |
| Diagnosis, med changes, child weight tracking, family leaderboards | — | EXCLUDED | D-042 |

---

## 13. Dependencies

```
access (P1) ─┬─► wellness / goals / habits / nutrition authorization (P1)
             ├─► dashboard: today + family overview (P1)
             ├─► FOOD_PROFILE household fit (P2)
             ├─► records / medications / appointments (P4)
             └─► challenges (P6)
consent purposes (P1) ─► every tracking write (P1) ─► records purposes (P4)
experience tiers (P1) ─► UI capability gating (P1+) ─► older-adult home (P4)
time_zone (P1) ─► today, goals, habits, insights day boundaries
trackers + history (P2) ─► insights (P3) ─► AI summaries (P5, + credential)
notifications (P2) ─► hydration/habit reminders (P2) ─► medication/appointment reminders (P4)
library: foods/recipes (P2) ─► meal planning + grocery lists (P6)
⚖️ counsel ─► production launch of child data (D-001) and records/medications (D-049)
```

---

## 14. Roadmap

| Phase | Contents | Exit gate (must pass before the next phase) |
|---|---|---|
| **1 — Foundation** | decision review (D-042–D-053) · `access` module + V14 · policy refactor of wellness/goals/habits/nutrition · meal-read fix · consent purposes enforced · profile band/type consistency · household of one + `GET /me` + experience tiers · member-local days · today summary + family overview APIs · data-rights coverage · ArchUnit rules · P1 UI (onboarding, sharing/caregivers, access log, family overview) | policy matrix tests green; meal-leak regression test; consent enforced on every write; child band mismatch refused; erasure/export include all health data; every dashboard number traceable to observations |
| **2 — Tracking** | tracker entry/history UIs · meals UI · goals/habits UI · notification framework (in-app) + hydration/habit reminders · household foods/recipes · FOOD_PROFILE household fit | every tracker writes through `wellness`; notification child-exclusion test; honest empty states everywhere |
| **3 — Insights** | typed, localised insights · four timeframes · sleep/weight/habit/goal/meal detectors · weekly/monthly summary · family insights | no insight without evidence; no population norm; family insights use visible data only; no ranking |
| **4 — Health management** ⚖️ | records + documents · medications + dose log · appointments · medication/appointment reminders · older-adult home · vaccination records | consent purposes live; upload security tests; no medication-advice code path; release gate documented until counsel signs off |
| **5 — Intelligence** | deterministic summaries · closed-intent Ask · rule-based recommendations · AI phrasing adapter (MOCKED) | numeric-fidelity test; injection probes; child-data exclusion test; MOCKED clearly labelled |
| **6 — Differentiation** | cooperative family challenges · milestones · meal planner · grocery lists · integrations (when unblocked) · premium | children excluded from all mechanics; no member ranking; premium never gates safety |

**Sequencing notes.**
- **Phase 4 is fourth for legal readiness, not for value.** Records and appointments are the strongest older-adult features. Its schema can start once D-049's review lands, in parallel with Phase 3.
- **The foundation comes first.** Phases 2–6 would otherwise each re-implement authorization and hit the same meal-leak class of defect.

---

## 15. What is explicitly out of scope

See D-042's "remain excluded" list. It restates D-034's safety boundaries and adds:
- ranking between family members, for anyone;
- weight-loss competitions;
- very-low-calorie plans;
- safety information behind a paywall.

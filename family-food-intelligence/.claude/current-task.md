# Current task

## Where the last session stopped (2026-09-12)

**Product scope changed deliberately** to an individual + family wellness platform.
Recorded as **D-034**–**D-040** in `docs/decisions/decision-log.md`; target architecture in
`docs/architecture/wellness-platform-architecture.md`. Safety boundaries were explicitly
*not* relaxed — D-034 restates them.

**Wellness Phase 1 is delivered and green:**

- `V12__wellness_foundation.sql` — `household.is_individual` (D-035), `health_observation`
  (one time-series table for every future tracker), `metric_visibility` (default-closed,
  partial unique index on live grants).
- `com.familyfood.wellness` — `MetricCode` (28 metrics, each declaring category, canonical
  unit and age class), `WellnessService` (age gate + visibility + audit), REST surface at
  `/api/v1/wellness`.
- Tests: 34 taxonomy + 13 service (backend suite 149 green); 25 live-API checks in
  `scratchpad/audit/verify-phase1.js`.

## The next concrete step — Phase 2 (Tracking)

Build trackers **through `wellness`**; do not add a second observation path.

1. **Nutrition logging** (D-039 supersedes D-005): `meal` + `meal_entry`, a family meal
   logged once and apportioned per member, writing `NUTRITION` observations.
2. **Goals + habits**: their own entities, reusing `MetricCode.isPermittedFor` for the age
   gate — a weight goal must be as impossible for a child as a weight observation is.
3. **Activity / sleep / hydration entry UI**: metric definitions already exist; this is form
   work plus `GET /wellness/metrics` to decide what to offer per subject.

Only then Phase 3 (insights), which needs history to exist first.

## Open, and blocking later phases

- ⚖️ Clinical interpretation (D-037) — gates every "is this normal?" question.
- ⚖️ DPDP-verifiable parental consent (D-001) — unchanged, gates child data at scale.
- ⚖️ Phase 4 (records, medications, appointments) needs privacy/clinical review before schema.
- **Environment:** backend OOM-crashes roughly every 15 min (~0.5 GB free RAM). Maven is
  available via IntelliJ's bundle:
  `"/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.4.1/plugins/maven/lib/maven3/bin/mvn"`
  with `JAVA_HOME=/c/Program Files/Java/jdk-21`. Restart helper: `scratchpad/be.sh`.

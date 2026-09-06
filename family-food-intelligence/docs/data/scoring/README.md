# Scoring rule evidence pack — process, not content

**This directory holds no rule content yet.** It exists to define the process by which real
`scoring_rule`/`score_component` content gets authored, so that the first session that does
the authoring doesn't have to invent the process at the same time it's under pressure to also
invent the numbers. See D-024 and **D-025** (`docs/decisions/decision-log.md`) for why this
process is mandatory rather than a nice-to-have: the backend engine and schema are complete
and tested (Phase 4), and ship with **zero active rules** specifically because no document in
this repository specifies a real numeric health threshold — and Claude must never originate
one from general knowledge, however confident-sounding.

## The rule, in one sentence

```
domain source → reviewed specification → migration → automated tests → activate rule_set
```

Nothing shortens this. A migration that inserts a `scoring_rule`/`score_component` row is not
written until the specification below exists and has been reviewed.

## Required layout, once authoring starts

```
docs/data/scoring/
├── README.md              this file — process only
├── scoring-methodology.md how ProductHealthScore/PersonalFit aggregate rule outcomes into a
│                          verdict/score — the engineering-facing model, not nutrition content
├── rules-v1.md             the specification for rule_set version "v1" (or whatever version
│                          is being activated) — one row per rule, fields below
├── evidence/
│   ├── source-001.md      one file per cited source: what the authoritative body actually
│   │                      published, verbatim or precisely quoted, with a real document URI
│   └── ...
└── review.md               who reviewed this rule_set, against what evidence, and when it
                           was approved for activation
```

## Three things every rule specification must keep separate

These are three different claims and conflating them is exactly how a wrong number reaches a
user. Each rule in `rules-vN.md` states all three, distinctly:

1. **Source** — what the authoritative organisation actually says (e.g. "WHO recommends free
   sugars intake below 10% of total energy, WHO/FAO 2015"). A pointer into `evidence/`, not a
   restatement in the rule author's own words.
2. **Product interpretation** — how that population-level guidance was translated into a
   per-100g/per-serving product rule for this application (e.g. converting a % of daily energy
   into a gram threshold requires an assumed reference intake — state which one, and why).
3. **Engineering implementation** — the literal `scoring_rule` row: `definition` (`{field, op,
   value, unit}`), `basis`, `verdict`, `severity`, `standard_ref`. This is what `RuleEngine`
   actually executes (see `RuleEngineImpl` in
   `backend/src/main/java/com/familyfood/scoring/application/`).

## Required fields per rule (`rules-vN.md`)

| Field | Meaning |
|---|---|
| Rule ID | Stable code, matches `scoring_rule.code` |
| Nutrient/fact | e.g. `ADDED_SUGAR_G` — matches `nutrient.code` |
| Unit | e.g. `g`, `mg` |
| Basis | `PER_SERVING` \| `PER_100G` \| `BOTH` (D-010) |
| Applicable category | `category.code`, or "all" |
| Population/age scope | `applies_to_age_band`, or "all" |
| Threshold | The numeric value and comparison operator |
| Severity | Relative ranking among rules that can co-fire (most severe wins) |
| Score impact | If this rule also backs a `score_component`, its weight/direction |
| Reason code | The structured code the API returns (see `Reason` in
  `scoring/application/Reason.java`) — extend the existing vocabulary
  (`NUTRIENT_EXCEEDS_THRESHOLD`, etc.) rather than inventing a parallel one per rule |
| Source | Which file under `evidence/` |
| Source date/version | The publication date/version of the cited standard |
| Reviewer | Who approved this specific rule |
| Rule_set version | Which `rule_set.version` this rule belongs to |
| Effective date | `scoring_rule.active_from` |
| Confidence/verification status | Draft / reviewed / approved-for-activation |

## Activation is a reviewed release

Flipping a `rule_set` from `DRAFT` to `ACTIVE` (and the superseded one to `RETIRED` — the
partial unique index on `rule_set.status = 'ACTIVE'` means both happen in the same migration)
is written up in `review.md` with a named reviewer and a date, the same way a schema migration
touching production data would be. Nothing in the application code transitions a `rule_set`'s
status at runtime — activation is always a migration, by design (D-014, D-024).

## What this directory is not for

Not for illustrative or test-fixture rules — those live only in
`backend/src/main/resources/db/migration/V5__scoring.sql`'s `DRAFT` `"v1-draft-examples"`
rule_set, explicitly labelled as fabricated-for-testing and never activated. Anything written
under `docs/data/scoring/` is treated as a real, reviewed candidate for production activation.

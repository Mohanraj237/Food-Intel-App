# Implementation Plan

**Version:** 1.0 · **Date:** 2026-09-05 · **Owner:** Orchestrator

---

## 1. Target layout

```
family-food-intelligence/
├── backend/                        Java 21 / Spring Boot, one Maven module (D-021)
│   ├── pom.xml
│   └── src/main/java/com/familyfood/
│       ├── platform/               config · security · errors · logging · observability
│       ├── identity/  household/  consent/  profile/
│       ├── catalog/   search/     capture/
│       ├── scoring/   recommendation/
│       └── activity/  audit/      ai/
├── frontend/                       Vite · React · TypeScript PWA
│   └── src/{app,design-system,features,lib,locales}
├── infrastructure/                 docker-compose, Dockerfiles, .env.example
├── e2e/                            Playwright
├── docs/                           as specified
└── .github/workflows/              CI
```

The pre-existing empty `apps/` `packages/` `services/` directories implied a JS monorepo and are superseded by D-006; they are removed in Phase 1.

---

## 2. Phase sequence, gates and dependencies

```
PHASE 0  Discovery ──────────────────────────────────────── [DONE]
   │  repository assessment · decision log · architecture · database design
   │  requirements traceability · acceptance criteria · this plan
   ▼
PHASE 1  Foundation
   │  git + .gitignore · Maven skeleton · Spring Boot boot · PostgreSQL + Flyway
   │  reference-data migrations · auth (Argon2id + JWT) · error handling · logging
   │  + masking · ArchUnit boundaries · frontend shell + design tokens · CI · Docker
   │  GATE: app boots clean, migrations apply, auth round-trips, CI green
   ▼
PHASE 2  Household, consent, profiles          ◄── depends on 1
   │  household · membership · roles · invitations · consent ledger · ConsentGate
   │  adult profile (versioned) · guardian-controlled child profile · permission matrix
   │  deletion + export
   │  GATE: household isolation and child-consent gate provably enforced;
   │        security test suite passes (IDOR, cross-household, child access)
   ▼
PHASE 3  Product intelligence                  ◄── depends on 1 (parallel with 2)
   │  catalogue schema · per-field provenance · versioning · search (FTS+trgm)
   │  barcode lookup · label capture · OCR adapter · confirmation · correction queue
   │  seed catalogue with edge cases
   │  GATE: known test products return correct structured data; sanity rules reject bad data
   ▼
PHASE 4  Scoring and personalisation           ◄── depends on 2 + 3
   │  reference standards · rule set · rules engine · ProductHealthScore
   │  PersonalFit · HouseholdFit · explanations · alternatives with worse_on
   │  outcome capture · AI explanation layer (grounded, numeric-fidelity checked)
   │  GATE: scoring deterministic + reproducible; no LLM path to a number;
   │        every rule has a standard_ref; banned-vocabulary test green
   ▼
PHASE 5  Core UX                               ◄── depends on 4
   │  design system · all MVP screens wired to real APIs · offline read cache
   │  empty/error/loading states · accessibility
   │  GATE: critical journey works end-to-end against the real backend
   ▼
PHASE 6  Quality hardening
   │  security · privacy · accessibility · performance · data quality · AI safety
   │  GATE: no unresolved critical/high finding
   ▼
PHASE 7  Release candidate
      production build · migration test · smoke · backup/restore · release readiness
```

Phases 2 and 3 are genuinely independent (household/consent vs catalogue/search) and share no files. They are the one real parallelisation opportunity.

---

## 3. Phase detail

### Phase 1 — Foundation

| # | Task | Output |
|---|---|---|
| 1.1 | `git init`, `.gitignore`, `.gitattributes`, `.editorconfig`, README | Repo under version control before any secret can be committed (RA-09) |
| 1.2 | Maven project, Java 21 toolchain, package skeleton (D-021) | `backend/` builds |
| 1.3 | Spring Boot app module; profiles `local`/`test`/`prod` | Boots on `:8080` |
| 1.4 | `docker-compose`: PostgreSQL 16, MinIO; `.env.example` | `docker compose up` gives a working stack |
| 1.5 | Flyway V1 — extensions, reference tables, seed reference data | `nutrient`, `allergen`, `category`, `reference_standard`, `consent_purpose` populated |
| 1.6 | Flyway V2 — identity tables | |
| 1.7 | Argon2id hashing, JWT issue/refresh/revoke, Spring Security chain | Register → login → refresh → logout round-trips |
| 1.8 | RFC 7807 error handling, correlation id filter | No stack trace reaches a client |
| 1.9 | Structured JSON logging + sensitive-field masking layer | Deny-listed fields never serialised |
| 1.10 | ArchUnit suite: module boundaries, `scoring`↛`ai`, no commercial deps | Build fails on violation (PX-9) |
| 1.11 | Testcontainers harness; first integration test | |
| 1.12 | Vite + React + TS scaffold; design tokens; app shell; bottom nav; router | Frontend boots, talks to `/api/v1/health` |
| 1.13 | i18n bundle loader + `en-IN.json`; no-hard-coded-string lint rule | NFR-14 |
| 1.14 | GitHub Actions: format, lint, typecheck, unit, integration, build, secret scan, dep scan | CI green |
| 1.15 | Micrometer metrics, health/readiness endpoints | |

**Gate 1:** application boots clean; migrations apply from empty; auth round-trips; CI green on a clean checkout; ArchUnit enforcing.

### Phase 2 — Household, consent, profiles

Household CRUD · membership + roles · invitation flow (D-002: invited adult signs up and consents independently) · consent ledger + `ConsentGate` enforced at processing time · versioned adult profile with closed goal vocabulary (D-017) · guardian-controlled child profile with hard processing gate (D-001) · `PermissionService` and the full role × resource × action matrix · audit events on every consent and child-data action · data export and erasure with per-adult isolation.

**Gate 2:** the security suite proves — cross-household access denied; IDOR denied on every ID-bearing endpoint; a non-guardian adult cannot read a child profile; no child data is processed with consent absent; withdrawal by one adult leaves the household functional for others; erasure removes the right rows and leaves `consent_record` + `audit_event`.

### Phase 3 — Product intelligence

Catalogue tables · `product_source` per-field provenance · versioning with supersede semantics · conflict retention · search (weighted tsvector + trigram, brand aliases) · barcode lookup incl. duplicate-GTIN disambiguation · image upload hardening (magic bytes, size, re-encode, EXIF strip) · `OcrProvider` port + adapter · extraction proposal with per-field confidence · **mandatory confirmation** · contribution queue · correction queue · write-time sanity rules · seed catalogue with all 13 edge cases.

**Gate 3:** seeded products return correct structured data; sanity rules reject implausible panels; a missing nutrient is absent rather than zero; no path reaches the catalogue without confirmation + review.

### Phase 4 — Scoring and personalisation

`reference_standard` content · rule set v1 authored as data, every rule carrying a `standard_ref` (D-009) · deterministic engine evaluating both bases (D-010) · allergen short-circuit · `ProductHealthScore` with factor breakdown · `PersonalFit` · `HouseholdFit` across consenting members · deterministic explanation templates · alternatives with `better_on` **and** `worse_on` (D-013) · honest empty result · outcome capture (D-005) · AI explanation layer with the untrusted-data envelope and numeric-fidelity check (D-011).

**Gate 4:** same inputs → same verdict, always; every verdict reconstructible from its three version references; no code path from a model to a number; banned-vocabulary test green across locale bundles and rule templates; injection probes rejected.

### Phase 5 — Core UX

Full design system · every MVP screen wired to real APIs · verdict-first hierarchy with score demoted (D-003) · sign-in gate at the fit strip (D-020) · OCR confirmation screen · compare · alternatives · settings and consent management · offline read cache · every empty/error/loading/degraded state · accessibility pass.

**Gate 5:** the critical journey — open → search/scan/snap → understand → who's eating → family fit → alternatives → decision → outcome — works end-to-end against the real backend.

### Phase 6 — Quality hardening
Independent passes: functional, UX, accessibility (axe + keyboard + contrast + SR semantics), performance against NFR-01..04, security (the full §21 list), privacy (consent, deletion, export, log inspection), data quality, AI safety, edge cases. Findings triaged; no unresolved critical/high.

### Phase 7 — Release candidate
Production build · migration test from empty and from a populated database · deployment validation · smoke tests · backup/restore drill · security review · performance baseline · privacy checklist · `docs/release/release-readiness.md` with honest per-feature status.

---

## 4. Agent / task map

Delegation is used where a workstream is genuinely independent and does not share files. Two Phase-0 documentation agents were launched and lost to a session rate limit before writing; that work was absorbed by the orchestrator. Planned delegation:

| Workstream | Mode | Rationale |
|---|---|---|
| Phase 0 discovery, decisions, architecture | Orchestrator | Requires whole-corpus context; delegation would re-derive it |
| Phase 1 foundation | Orchestrator | Cross-cutting; every later workstream depends on its conventions |
| Phase 2 household/consent ‖ Phase 3 catalogue/search | Parallel candidates | Disjoint modules, disjoint migrations, disjoint tests |
| Phase 4 scoring | Orchestrator | The product's core correctness and liability surface |
| Phase 5 frontend features | Parallel candidates after the design system exists | Feature folders are disjoint |
| Phase 6 security / accessibility / performance review | Parallel | Read-mostly, independent findings |

Rules for any delegated task: exact objective, files in scope, files out of scope, expected outputs, acceptance criteria, tests required. Every result is reviewed by the orchestrator before integration; a completion message is not acceptance (Constitution §4).

---

## 5. Definition of done, applied per requirement

Requirement satisfied · UX implemented · API and data flow working · validation at boundaries · error states handled · security reviewed · tests passing at the right level · accessibility reviewed · documentation updated · observability sufficient · no known critical defect. Anything short of all eleven is reported with its honest status (`PARTIALLY_IMPLEMENTED`, `MOCKED`, `BLOCKED`, `DEFERRED`) and never as "implemented".

---

## 6. Phase 4 completion and post-foundation sequencing (added 2026-09-06)

Phase 4's deterministic engine, schema, API and frontend wiring are built and tested (see
`.claude/state.md`), but the phase is not at Gate 4 yet: the `ACTIVE` rule_set intentionally
ships with zero rules (D-024), `recommendation` is still `PLANNED`, and `capture` (ingestion)
— scoped under Phase 3 but never built — is the reason the engine has no real product facts to
run against. The workstreams below close that gap and sequence what comes after. Priority
reflects value and dependency, not phase number; several of these can run in parallel.

| Priority | Workstream | Depends on | Closes |
|---|---|---|---|
| P0 | Scoring rule content (evidence pack → spec → migration → activation) | Domain/regulatory review, `docs/data/scoring/` | Gate 4 — real scores and verdicts |
| P0 | Controller tests for `ProductHealthController`/`HouseholdFitController` | None | The one known test gap from the Phase 4 session (see below) |
| P0 | Product ingestion (`capture` module) | None | Real product facts — without this, scoring has nothing to score |
| P1 | `recommendation` module (alternatives, `better_on`/`worse_on`) | Real scoring rules, real product data | FR-007, PX-2, D-013 |
| P1 | Household list endpoint (`GET /api/v1/households` or equivalent, scoped to the caller) | None | Removes the frontend's locally-cached-household-id workaround |
| P1 | Real invitation lifecycle (`CREATED → SENT → OPENED → ACCEPTED`, plus `EXPIRED`/`REVOKED`) + email delivery | An email provider | Replaces the current raw-token-shown-to-inviter flow |
| P2 | `activity` module (verdict/lookup persistence, D-014's version references) | Real scoring rules (recording a verdict is more useful once verdicts are real) | FR-014, recent/saved history |
| ~~P2~~ | ~~Data rights (export, account/child deletion)~~ | None | **Done** (2026-09-06, `datarights` module, D-028/D-029) — Phase 2's own gate requirement is now met and integration-tested |
| P3 | Phase 5 UX polish, Phase 6 hardening, Phase 7 release candidate | All of the above | See §3 above — unchanged, not restated here |

**Parallelizable now, independently:** scoring rule content, controller tests, product
ingestion, and the household list endpoint share no files and no data dependencies on each
other. Recommendation and activity are sequenced *after* real scoring content because a
recommendation ranked against an empty ruleset, or an activity log full of `INSUFFICIENT_DATA`
verdicts, tests nothing real.

### P0 — Scoring rule content

Governed by **D-025**: a human-owned specification (rule id, nutrient, unit, basis, category/
age scope, threshold, severity, reason code, source, source date, reviewer, rule_set version,
effective date, confidence status) precedes any migration; source, product interpretation, and
engineering implementation are recorded separately; activation is a reviewed release. Full
process and required layout: `docs/data/scoring/README.md`. Claude does not originate a
threshold value from general knowledge under any framing of this task — that is the exact
failure D-024 and D-025 exist to prevent.

### P0 — Controller tests

`ProductHealthController`: known-product happy path, unknown product (404), a product with no
declared nutrients (`NOT_YET_SCORED`, not an error), response carries `ruleSetVersion` and
structured `reasons`, determinism across repeated calls. `HouseholdFitController`:
unauthenticated (401), caller not a member of the target household (403,
`NOT_HOUSEHOLD_MEMBER`) even when that household is real and populated, a household owner
correctly excluded from another adult's fit, a guardian correctly included for a linked
child and excluded for an unlinked one, consent-gated statuses reachable end-to-end through
the HTTP layer (not just the service layer, which `PersonalFitServiceIT`/`HouseholdFitServiceIT`
already cover). These two controllers currently have no automated coverage of their own — see
`.claude/current-task.md` for why (an explicit instruction not to add tests in the session that
built them, not an oversight).

### P0 — Product ingestion (`capture` module)

Break into: (1) an ingestion data model — source, capture method, captured timestamp,
verification status, confidence, source reference, raw vs. normalised values, matching the
per-field provenance `catalog` already models (D-015); (2) barcode ingestion — scan → lookup →
found (serve facts) or not found → capture/request flow, building on the existing barcode
lookup in `catalog`; (3) OCR fallback for products not yet in the catalogue, kept explicitly
separate from authoritative product facts until the existing mandatory-confirmation and
ops-review gates (`system-architecture.md` §6) are satisfied — no OCR output enters the
catalogue unconfirmed or unreviewed, regardless of how this module is sequenced.

### P1 — Recommendation module, proposed starting shape

The following ranking is a **proposed starting point for product sign-off, not a decision** —
recorded here so the next session doesn't have to reconstruct the conversation, not as
something Claude decided unilaterally: household compatibility (does it even fit this
household's constraints) → allergen conflicts (hard exclusion) → product health assessment →
relevant nutrient/component differences → price/availability (deferred; no pricing/inventory
data source exists yet). Start deterministic (rank by these dimensions in order), not
ML-ranked — matches the "smallest useful" precedent D-024 set for scoring itself. Every
alternative shown publishes both `better_on` and `worse_on` (D-013, PX-2) — no exception.

### A pre-existing documentation gap, surfaced not fixed here

`docs/product/requirements-traceability.md` states it is "updated at the end of every phase"
but every row still reads `NOT_STARTED`, including requirements multiple completed phases
satisfy (e.g. FR-009 household management, PX-6 adult self-consent, PX-9 no-advertising-
capability). This predates Phase 4 and was not introduced by it. A full status audit across
every FR/PX/CH row is a real task worth doing, but is a separate, larger effort from the
Phase 4 sequencing above and was not attempted here — don't treat that document's status
column as current for anything until it's been through that audit.

## 7. Known external blockers

| Blocker | Effect | Handling |
|---|---|---|
| No AI provider credential | OCR extraction and AI explanation prose cannot run live | Ports implemented; a deterministic local adapter serves development; status reported `MOCKED` until a key exists. The product remains fully usable — manual entry replaces OCR, deterministic templates replace AI prose |
| No SMS provider | Phone/OTP auth | `BLOCKED` (D-008); email + password ships |
| Privacy counsel not engaged | DPDP-verifiable child consent mechanism | `PROVISIONAL` (D-001); guardian attestation is implemented and explicitly marked `REQUIRES_LEGAL_SIGNOFF`; child pathway is a documented release blocker |
| No FSSAI Indian Nutrition Rating data feed | FR-10 display | `DEFERRED`; display slot and standard entry exist |
| No real seed catalogue | Coverage | Development seed with realistic products and deliberate edge cases; real seeding is a data-ops activity, not an engineering one, and is scoped in `docs/data/` |

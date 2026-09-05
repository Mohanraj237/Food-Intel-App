# Repository Assessment — Phase 0

**Date:** 2026-09-05 · **Assessor:** Orchestrator · **Status:** Complete

---

## 1. Verdict up front

The repository contains **no application code of any kind**. It is a documentation-only repository with an empty directory skeleton. There is nothing to preserve, migrate or refactor, and no existing stack constrains the architecture choice.

There is also **no git repository** — `git init` has not been run. Version control is therefore itself a Phase 1 deliverable.

What does exist is unusually good: three independent documentation sets totalling ~130KB, including a full engineering constitution, a commissioned 11-document product pack, and a sharply argued market/risk critique. The main Phase 0 work was not code archaeology — it was **reconciling three sets of requirements that disagree on scope**.

---

## 2. Actual current state

```
c:\Users\mohan\Documents\Food Intel App\
├── Family_Food_Intelligence_Documentation_Pack_v0.2\   11 × .docx  (~440 KB)
├── files\                                              6 × .md     (~54 KB)
└── family-food-intelligence\                           ← application root
    ├── CLAUDE.md                                       40 KB engineering constitution
    ├── apps\            (empty)
    ├── packages\        (empty)
    ├── services\        (empty)
    ├── infrastructure\  (empty)
    └── docs\            13 empty subdirectories
```

| Checked for | Found |
|---|---|
| Package/dependency manifests (`package.json`, `pom.xml`, `build.gradle`, `requirements.txt`) | **None** |
| Source directories with source files | **None** — `apps/`, `packages/`, `services/` are empty |
| Environment configuration (`.env*`, config files) | **None** |
| Database configuration, migrations, schema | **None** |
| API configuration, OpenAPI specs | **None** |
| CI/CD (`.github/`, `.gitlab-ci.yml`, `Jenkinsfile`) | **None** |
| Docker (`Dockerfile`, `docker-compose.yml`) | **None** |
| Tests of any kind | **None** |
| Design system, component library, tokens | **None** |
| Authentication implementation | **None** |
| Deployment configuration | **None** |
| Git repository | **Not initialised** |
| `.gitignore` | **None** — a real risk given the secrets this product will handle |

The `apps/` `packages/` `services/` triple implies an intended JS-monorepo layout. That intent is not binding — it predates any code, and D-006 selects a Java backend. The layout is superseded in §6.

---

## 3. Documentation inventory and standing

### 3a. Engineering Constitution — `family-food-intelligence/CLAUDE.md`
66 sections, ~40 KB. Covers orchestration, phases, product scope, child policy, medical safety, food language, product data, provenance, freshness, OCR, search, UX, design system, scoring, LLM role, grounding, prompt-injection defence, personalisation, comparison, trust, privacy, household authorization, auth, database, API, frontend/backend architecture, testing, accessibility, performance, observability, errors, empty/offline states, integrations, commerce, analytics, commercial model, git workflow, code review, definition of done, release gates.

**Assessment:** high quality and internally consistent. This is the operating manual and is treated as rank 2 in the source hierarchy. Its one gap is that it states policy without settling scope conflicts against the other two sets — which is what `docs/decisions/decision-log.md` now does.

### 3b. Documentation Pack v0.2 — 11 `.docx`
Extracted to text and read in full. Commissioned specification set with named owners per document and its own decision log in doc 00.

| Doc | Content that binds implementation |
|---|---|
| 00 Index | Document control; decision log (search-first, keep child profiles, optional meal logging, transparent scoring, early B2B2C validation, educational health scope); 12 research sources S1–S12 |
| 01 Strategy & Vision | Three product layers; MVP = product intelligence + personal/family fit + purchase intelligence; moat is trust and workflow, not AI or the database |
| 02 BRD | Business outcomes (decision utility, behaviour change, trust, retention, commercial viability); 8 business acceptance criteria |
| 03 PRD MVP | **FR-001..FR-014**, 5 core journeys J1–J5, UX targets (≤10s to verdict), NFRs (99.5% availability, p95 lookup ≤2s, OCR p95 ≤8s), 7 acceptance scenarios |
| 04 MVP Scope & Roadmap | Release 0.1 Must list; 0.2 Should; 1+ Later; "do not build until validated" list; gates A–E |
| 05 System/Data/AI | Reference architecture; **core domain model (15 entities)**; provenance rules; AI use-case table with guardrails; scoring layering; recommendation ranking rules |
| 06 Privacy/Child Consent | **CH-001..CH-010** child controls; health-language avoid/prefer table; launch-blocker checklist |
| 07 Validation/Metrics/GTM | H1–H6 hypotheses; north-star = Healthy Decision Rate; go/pivot/stop framework |
| 08 Scoring Methodology | **Score layers, required dimensions, evidence hierarchy A–D, rule governance, personal-fit rule table, explainability template** |
| 09 Data Operations SOP | Source table with controls; product lifecycle states; verification checklist; freshness SLA; change detection; quality metrics |
| 10 UX/IA Scope | Primary navigation; result-screen hierarchy; child-profile UX; error/uncertainty UX; copy principles; accessibility |
| 11 Security/Threat Model | Asset sensitivity table; threat/control table; AI security rules; security testing; privacy-by-design checkpoints |

**Assessment:** this is the authoritative product spec (rank 3). It is complete enough to build from. Its weakness is that it is written at requirement altitude — it names *what* without settling *how*, and it is materially less rigorous than the PANTRY set on legal exposure.

### 3c. Project PANTRY — `files/` 6 `.md`
Independent critical intelligence written against six named assumptions, with reversal conditions attached to every locked decision. Rank 4 — a risk lens, not a competing spec (see decision log preamble).

**Where it is more rigorous than the pack, and therefore adopted:** adult data-principal independence (no proxy consent); banned-vocabulary enforcement in code; mandatory `standard_ref` on every rule; per-serving *and* per-100g evaluation; per-field provenance; honest `worse_on` on alternatives; data-sourcing legality; excluded populations; the FMCG-disparagement defence.

**Where it conflicts on scope, and is overruled:** removal of child profiles (D-001), and the stack recommendation (D-006).

**Its most valuable single contribution** is `03-Data-Strategy.md`: data cost is roughly flat for answering questions about products the user brings you and explosive for recommending across whole categories. That finding is why the MVP catalogue is scoped at ~2,000 SKUs seeded against real baskets rather than aiming at category completeness, and why cart optimisation is out of scope.

---

## 4. Conflicts found and resolved

| # | Conflict | Resolution | Decision |
|---|---|---|---|
| 1 | Child profiles in MVP (Pack FR-010) vs deferred to V1.2 (PANTRY) | Build in full, gate processing behind ACTIVE consent; verifiable-consent mechanism blocked on counsel | D-001 |
| 2 | Who consents for an adult member | Adults consent for themselves; invitation flow only | D-002 |
| 3 | 0–100 score (Pack 08) vs no headline score (PANTRY) | Score computed, persisted, explainable; verdict chip is the headline | D-003 |
| 4 | Three different verdict vocabularies | Single closed enum + separate allergen hard flag | D-004 |
| 5 | Optional meal capture (brief, CLAUDE.md) vs none (Pack 04 defers, PANTRY excludes) | No meal logging; outcome capture only | D-005 |
| 6 | Java/Spring (brief) vs React Native + Node/Python (PANTRY 06) | Java 21 / Spring Boot / PostgreSQL / React PWA | D-006 |
| 7 | Redis "where justified" | Not justified at MVP scale; Caffeine + DB rate limiting | D-007 |
| 8 | Phone-first identity (PANTRY schema) vs no SMS provider | Email + password now, phone adapter deferred and marked BLOCKED | D-008 |
| 9 | "Don't force registration" (brief §9) vs consent required for health processing | Anonymous browsing; sign-in gate at the personal-fit strip | D-020 |
| 10 | Hindi at launch (PANTRY A3) vs `Could` priority (Pack FR-14) | English only, fully externalised strings | D-019 |

All twenty decisions, with rationale, alternatives and consequences, are in `docs/decisions/decision-log.md`.

---

## 5. Technical debt

None. There is no code.

The *documentation* debt worth naming:

| Item | Impact | Handling |
|---|---|---|
| No git repository, no `.gitignore` | A product handling health data with no secret-leak guard is an immediate risk | Phase 1, first task |
| Documentation Pack has no OpenAPI spec, no wireframes, no rule-set content | The pack's own doc 00 lists these as "required future documents" | Produced in Phase 0/1 as `docs/api/`, `docs/design/`, and the seeded rule set |
| Scoring rules exist as methodology, not as content | Cannot score anything without actual thresholds | Phase 4: rules authored as data rows, each with a `standard_ref` (D-009) |
| No seed catalogue | The single largest delivery risk (PANTRY R-06) | Phase 3: ~40 realistic seed products with deliberate edge cases; real seeding is a data-ops activity, not an engineering one |
| Three source sets will drift | Future contradictions | The decision log is now rank 1 and supersedes all three |

---

## 6. Missing infrastructure — the full Phase 1 build list

Everything below must be created from nothing:

**Repository:** git init, `.gitignore`, `.gitattributes`, `.editorconfig`, README, LICENSE decision, `CONTRIBUTING.md`.

**Backend:** Maven multi-module project; Spring Boot application; module boundaries + ArchUnit enforcement; PostgreSQL + Flyway; Spring Security + JWT; global error handling (RFC 7807 problem+json); request/response validation; structured JSON logging with a sensitive-field masking layer; OpenAPI generation; Testcontainers harness.

**Frontend:** Vite + React + TypeScript; router; server-state layer; design-system tokens and components; PWA manifest + service worker; typed API client generated from OpenAPI; i18n bundle loader; error boundary; skeleton/empty/error primitives.

**Data:** schema for ~30 tables; seed catalogue; seeded rule set; reference standards table.

**Infrastructure:** `docker-compose` (PostgreSQL, MinIO, app); Dockerfiles; `.env.example`; environment profiles (local / test / prod).

**CI:** GitHub Actions — format, lint, typecheck, unit, integration (Testcontainers), build, dependency scan, secret scan, E2E.

**Testing:** JUnit 5 + AssertJ + Testcontainers; MockMvc/RestAssured API tests; Vitest + Testing Library; Playwright E2E; axe accessibility checks; ArchUnit; banned-vocabulary test; numeric-fidelity test for AI output.

**Observability:** structured logs, health/readiness endpoints, Micrometer metrics, the metric set in `docs/operations/observability.md`.

---

## 7. Risks carried into implementation

| ID | Risk | Sev | Mitigation | Phase |
|---|---|---|---|---|
| RA-01 | Catalogue coverage too low; users hit "not found" and churn (PANTRY R-06) | **High** | OCR fallback is a first-class path, never a dead end; "not found" is a designed screen; seed against realistic basket categories | 3 |
| RA-02 | A wrong nutrition value drives a wrong verdict (PANTRY R-01) | **High** | Write-time sanity rules; per-field provenance; confidence + verification state; correction queue; `INSUFFICIENT_DATA` verdict rather than a guess | 3 |
| RA-03 | Verdict read as medical advice (PANTRY R-05) | **Critical** | Closed verdict enum; banned-vocabulary build test; excluded populations in code; notice on every conditions-touching screen | 4, 5 |
| RA-04 | Child-data processing before valid consent | **Critical** | Hard processing gate; consent checked at processing time; child pathway flagged not-production-ready until counsel signs off (D-001) | 2 |
| RA-05 | Prompt injection via ingredient text or an uploaded label | High | Untrusted-data envelope; no model tool access to authz or retrieval; numeric-fidelity check; injection probes in the test suite (D-011) | 3, 4 |
| RA-06 | Cross-household data access / IDOR | **Critical** | Every query scoped by authenticated principal; no repository method accepts a bare ID from a path; dedicated security test suite | 1, 2 |
| RA-07 | PWA barcode scanning underperforms native | Medium | `BarcodeDetector` + WASM fallback behind a port; measured; native shell is the documented escape hatch (D-006) | 3 |
| RA-08 | Scope creep back toward a health dashboard / calorie tracker | Medium | Post-MVP backlog is the only landing place; phase gates | All |
| RA-09 | Secrets committed | High | `.gitignore` first commit; secret scanning in CI; `.env.example` only | 1 |
| RA-10 | AI provider credential unavailable | Medium | AI is never on the critical path; deterministic templates serve every explanation; OCR degrades to manual entry. Status reported honestly as MOCKED/BLOCKED, never as implemented | 3 |

---

## 8. Recommended target architecture

Full detail in `docs/architecture/system-architecture.md`. In one paragraph:

A **modular monolith** in Java 21 / Spring Boot, deployed as a single container, with thirteen internal modules whose boundaries are compile-time-enforced by ArchUnit. PostgreSQL 16 is the single source of truth, holding identity, household, consent ledger, versioned product catalogue, versioned rule set, verdicts, outcomes and audit. Search is PostgreSQL full-text plus trigram fuzzy matching. Label images live in S3-compatible object storage. The client is a mobile-first installable React/TypeScript PWA talking to a versioned REST API. A deterministic rules engine — not a model — produces every score and verdict; an AI adapter behind a port handles OCR extraction proposals and explanation prose, with structural guarantees that it can never originate a number, a threshold, a verdict or an authorization decision.

**Why not microservices:** there is no team boundary, no independent scaling need, and no deployment-independence requirement. The Engineering Constitution (§3), the brief (§5) and PANTRY (06 §1) all say the same thing. Module boundaries enforced in-process give the seam without the distributed-systems tax; extraction stays cheap if it is ever warranted.

---

## 9. Migration strategy

Not applicable — greenfield. The only migration concern is forward: schema changes ship as Flyway migrations from the first commit, never as manual DDL, so that the versioning guarantees in D-014 hold from day one.

---

## 10. Phase 0 exit assessment

| Gate | Status |
|---|---|
| Repository fully inspected | Pass |
| All product documentation read | Pass — 3 sets, 18 documents |
| Conflicts identified and resolved with rationale | Pass — 10 conflicts, 20 decisions logged |
| Architecture coherent with documentation | Pass |
| MVP boundary explicit | Pass |
| Risks registered with mitigations and owning phase | Pass |
| Implementation plan sequenced | Pass |

**Phase 0 gate: PASS.** Proceed to Phase 1 (Foundation).

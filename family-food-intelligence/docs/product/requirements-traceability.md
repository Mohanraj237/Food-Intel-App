# Requirements Traceability Matrix

**Version:** 2.0 · **Date:** 2026-09-06 · **Maintained by:** Orchestrator

Every MVP requirement traced from source document → decision → module → API → tests.

**Maintenance note (corrects v1.0's claim):** this document is **not** updated
automatically at the end of every phase — that never actually happened (three phases'
worth of rows sat at `NOT_STARTED` after Phases 0–4 shipped real, tested implementation).
It is updated by a deliberate traceability audit session, which re-verifies every row
against the repository rather than trusting the previous version of this file or any
prior session's summary. Treat a row's status as current as of the date above; if
material backend/frontend work has landed since, re-audit before relying on it.

**Status vocabulary** (this session's audit; supersedes v1.0's
`IMPLEMENTED`/`PARTIALLY_IMPLEMENTED`/`MOCKED` set, which referenced a Constitution
section number that no longer exists after the CLAUDE.md v2 split):

| Status | Meaning |
|---|---|
| `COMPLETE` | Implemented, with evidence it works (tests and/or live verification). A phase-level content gap (e.g. D-024's empty rule set) does not by itself demote a requirement whose *mechanism* is fully built and proven — see the Gap/Notes column. |
| `PARTIAL` | Some meaningful portion implemented; the full requirement is not satisfied. |
| `NOT_STARTED` | No meaningful implementation exists. |
| `BLOCKED` | Cannot reasonably proceed — missing external dependency (credential, legal signoff, data feed, reviewed domain content). |
| `DEFERRED` | Explicitly postponed by a documented decision. |
| `NOT_APPLICABLE` | Superseded or made irrelevant by a documented decision. |

A requirement with no evidence reference is not done. **Do not confuse "mechanism
implemented" with "phase gate satisfied"** — see §7 of `docs/engineering/implementation-plan.md`
and the Phase Summary at the end of this document for the gate-level view.

---

## 1. Functional requirements — Documentation Pack PRD (authoritative)

| ID | Requirement | Decisions | Module | Status | Evidence | Gap / Notes |
|---|---|---|---|---|---|---|
| FR-001 | Product search by brand/product/category; return confidence and source | D-006 | `search`, `catalog` | **COMPLETE** | `search/api/SearchController.java` (`GET /api/v1/search`), `search/application/SearchService.java`, `search/infra/ProductSearchRepository.java` (FTS+trgm), `SearchServiceTest`, `SearchControllerIT`; `dataQuality`/source fields on `ProductSummaryDto` | Search-result latency target (NFR-03) not benchmarked |
| FR-002 | Barcode lookup; "not found" recovery via OCR | D-006 | `catalog`, `capture` | **PARTIAL** | `catalog/api/ProductController.java` (`GET /products/barcode/{gtin}`, 409 on ambiguous GTIN per AC-4.4); frontend `ScanScreen.tsx` (native `BarcodeDetector` + manual GTIN, D-022); not-found path routes to `ContributeProductForm` → `POST /api/v1/contributions` | The "recovery **via OCR**" clause is `BLOCKED` (no `ai`/OCR module exists at all — see FR-003). What ships instead is a real, working manual-entry recovery path, which is a different mechanism than the requirement names |
| FR-003 | Label OCR — nutrition, ingredients, allergens, serving size; confirmation for low confidence | D-011 | `capture`, `ai` | **BLOCKED** | None found — no `ai` package, no `OcrProvider` port/adapter anywhere in `backend/src/main` | External blocker: no AI/OCR provider credential. Note: `docs/engineering/implementation-plan.md` §7 states "Ports implemented; a deterministic local adapter serves development" for OCR — that does not match the current codebase (nothing was found on disk). Flagged here, not corrected, per this session's scope |
| FR-004 | Product profile — facts, source, updated date, score components, key concerns | D-003, D-014, D-015 | `catalog`, `scoring` | **PARTIAL** | `catalog/domain/ProductVersion.java`, `ProductSource.java` (per-field provenance), `ProductQueryService` (`dataQuality`, `currentVersion.verifiedAt`, `keyFacts`); `scoring/api/ProductHealthController.java` (`GET /products/{id}/assessment`) | Score components/key concerns return `NOT_YET_SCORED` for every real product until D-024/D-025 real thresholds are authored — facts/provenance/versioning are real, scored content is not |
| FR-005 | Personal fit against a member's goals/preferences/constraints | D-004, D-010 | `scoring`, `profile` | **COMPLETE** (mechanism) | `scoring/application/PersonalFitServiceImpl.java`; `PersonalFitServiceTest`, `PersonalFitServiceIT`; reached via `HouseholdFitController` for the caller's own entry | Real verdict content is `BLOCKED` by D-024/D-025 — every call returns `INSUFFICIENT_DATA`/`NO_ACTIVE_RULES` today. The evaluation mechanism itself (consent gate → profile → goal aggregation → most-severe-wins) is fully built and tested against the DRAFT rule set |
| FR-006 | Family comparison — fit for each eligible member in one view | D-002, D-020 | `scoring` | **COMPLETE** (mechanism) | `scoring/api/HouseholdFitController.java` (`GET /households/{id}/products/{id}/fit`, caller-scoped per D-002/D-024 pt.4); `HouseholdFitServiceIT`; frontend `ProductScreen.tsx` (`FitSection`/`MemberFitRow`) | Same D-024/D-025 content gap as FR-005 |
| FR-007 | Alternatives ranked by relevant improvement dimensions | D-012, D-013 | `recommendation` | **NOT_STARTED** | No `recommendation` package exists. `ModuleBoundaryTest.recommendationMustNotDependOnCommercialConcepts` is explicitly `allowEmptyShould(true)` — confirmed inert | Sequenced after real scoring content + real product data per `implementation-plan.md` §6 (P1). Distinct from Compare (below): a Phase 5 session built a real, honest two-product Compare screen (`features/compare/CompareScreen.tsx`, D-027) that composes existing endpoints client-side for a caller-chosen second product — that is not the same requirement as *ranked, system-suggested* alternatives, which still needs `recommendation` and remains `NOT_STARTED` |
| FR-008 | Explainability — factors behind score/fit, using evidence and source data | D-003, D-009, D-011 | `scoring`, `ai` | **PARTIAL** | `scoring/application/Reason.java`; `RuleEngineImpl` structured reason codes (`NUTRIENT_EXCEEDS_THRESHOLD`, `MISSING_NUTRIENT_DATA`, `NO_ACTIVE_RULES`), each carrying `standard_ref`; `ModuleBoundaryTest.scoringMustNotDependOnAi` (D-011 structurally enforced); **frontend now renders it (Phase 5 session)** — `design-system/ReasonList.tsx`/`lib/reasons.ts`, the per-person "why" disclosure on `ProductScreen.tsx`, and `features/product/HealthAssessmentSection.tsx` for the product-level assessment (`GET /products/{id}/assessment`, implemented Phase 4, called from the frontend for the first time this session) | Deterministic reason codes exist and are now visible end-to-end; the AI explanation *layer* (grounded natural-language prose) still does not exist — no `ai` module |
| FR-009 | Family management — add/remove members, roles, permissions | D-002 | `household` | **COMPLETE** | `household/api/HouseholdController.java` (create/list/members/invite/accept/revoke/remove); `HouseholdIT` (`nonMemberIsDeniedEverywhere` — Gate-2 IDOR, `removalRulesAreEnforced`); frontend `FamilyScreen.tsx` | "Permissions" here means the 3-role model (`OWNER`/`ADULT`/`DEPENDENT`), not a granular resource×action matrix — see NFR-07 |
| FR-010 | Child profile — guardian-controlled, consent status, purpose, minimum data | **D-001** | `household`, `consent`, `profile`, `datarights` | **PARTIAL** | `household/domain/GuardianLink.java`, `HouseholdService.addChild`; `profile/domain/PersonProfile.java` (`AgeBand`, no DOB column); `consent/application/ConsentService.java` (`CHILD_FOOD_SUITABILITY` purpose, `GUARDIAN_ATTESTATION` mechanism); frontend `FamilyScreen.tsx` + `ConsentPanel.tsx`. **New this session:** `POST /households/{id}/members/{id}/erase` (`HouseholdService.eraseChildMember` + `DataRightsService.eraseChild`) — guardian-only, immediate; erases the child's `person_profile` rows and scrubs the membership, `ChildErasureIT` (guardian succeeds; non-guardian co-member and unrelated-household adult both denied; erased child unreachable after; `consent_record`/`audit_event` retained; guardian and unrelated households untouched) | One real gap remains: the guardian-attestation consent mechanism is `PROVISIONAL`/`REQUIRES_LEGAL_SIGNOFF` per D-001 — `BLOCKED` on privacy counsel. The child-erasure gap is closed (D-028) |
| FR-011 | Feedback — flag incorrect product data and recommendations | D-015 | `catalog` | **NOT_STARTED** | None found — no feedback/flag/correction endpoint on an existing catalog product, in backend or frontend | Distinct from the new-product contribution flow (`capture`), which only handles products *not yet* in the catalogue |
| FR-012 | Event capture — optional "bought"/"consumed" with minimal confirmation | **D-005** (purchase half only) | `activity` | **NOT_STARTED** | No `activity` package exists. A Phase 5 session built the frontend confirmation UI (`features/product/OutcomeCapture.tsx`, D-027) — it deliberately makes no network call and never claims the choice was saved (`outcome.notPersisted`), so it does not change this row's status | Sequenced after real scoring content per `implementation-plan.md` §6 (P2). The UI is ready; only the `activity` backend and a wire-up of `OutcomeCapture`'s `onChange` remain |
| FR-013 | Versioning — formulation/data versions and effective dates | D-014 | `catalog` | **COMPLETE** | `catalog/domain/ProductVersion.java` (supersede semantics, `uq_product_current_version`); `profile/domain/PersonProfile.java` (`uq_profile_current`); `scoring/domain/RuleSet.java` (`uq_rule_set_active`); `ProfileVersionChainIT` | None material — the versioning pattern is applied consistently across every module that needs it |
| FR-014 | Auditability — log scoring-rule version and data source per verdict | D-014 | `activity`, `audit` | **PARTIAL** | `audit/AuditEvent.java` + `AuditService.java` (immutable, DB-trigger-enforced — see NFR-08); `ProductHealthResponse`/`PersonalFitResponse` carry `ruleSetVersion` | No `activity` module exists to persist a verdict alongside its three D-014 version references as queryable history — today's audit log records account/consent/household actions, not per-verdict version snapshots |

## 2. Requirements adopted from PANTRY because they are stricter

| ID | Requirement | Decision | Module | Status | Evidence | Gap / Notes |
|---|---|---|---|---|---|---|
| PX-1 | Hard allergen flag, visually and structurally distinct from nutrition verdicts | D-004 | `scoring` | **COMPLETE** | `PersonalFitServiceImpl.matchAllergens` (real, factual matching from day one — D-024 pt.2); frontend `design-system/AllergenFlag.tsx`; `i18n.vocabulary.test.ts` asserts no verdict label exists for `CONTAINS_ALLERGEN` and that cross-contamination copy is present | None material |
| PX-2 | Every alternative displays an honest trade-off where one exists | D-013 | `recommendation` | **NOT_STARTED** | Same as FR-007 — no `recommendation` module | — |
| PX-3 | Verdict vocabulary is a closed enum; banned words unrepresentable | D-004 | `scoring`, frontend | **COMPLETE** | `scoring/domain/RuleVerdict.java` (Java enum); `frontend/src/lib/i18n.vocabulary.test.ts` (banned-vocabulary gate + closed-enum label-coverage test, part of the frontend's passing test suite); `design-system/VerdictChip.tsx` | None material |
| PX-4 | Every rule cites a named public standard or cannot activate | D-009 | `scoring` | **COMPLETE** (mechanism) | `V5__scoring.sql`: `scoring_rule.standard_ref NOT NULL REFERENCES reference_standard(code)` — DB-enforced, not just a code convention | Zero rules are currently active (D-024) — the constraint is proven against the DRAFT rule set only |
| PX-5 | Both per-serving and per-100g evaluated; implausible serving flagged | D-010 | `scoring` | **COMPLETE** | `V5__scoring.sql`: `scoring_rule.basis CHECK (PER_SERVING\|PER_100G\|BOTH)`; `RuleEngineImpl.basesOf`/`evaluateGoal` evaluates both and lets the more severe verdict win (`RuleEngineImplTest`, `RuleEngineIT`); `ProductVersion.servingPlausible` column exists for the implausible-serving flag | None material |
| PX-6 | Adults consent for themselves; no proxy consent for an adult | D-002 | `consent`, `household` | **COMPLETE** | `consent/domain/ConsentMechanism.java` (`SELF_ATTESTATION` vs `GUARDIAN_ATTESTATION`); `HouseholdService.inviteAdult` creates only an invitation — no member/profile/consent row exists for an invited adult until they accept with their own account | None material |
| PX-7 | Per-field source recording | D-015 | `catalog` | **COMPLETE** | `catalog/domain/ProductSource.java`, `ProductSourceRepository` — a dedicated per-field provenance table (`V4__catalog.sql`) | None material |
| PX-8 | Excluded populations produce no numeric targets | D-017 | `profile`, `scoring` | **COMPLETE** | `profile/domain/GoalCode.java` — a closed 7-value enum with no weight, body-composition or calorie-target goal representable at all, mirrored by a DB `CHECK` constraint | Satisfied structurally by omission (no such target can exist for *any* profile) rather than by a runtime "is this person in an excluded population" detector — a stronger guarantee for the same outcome |
| PX-9 | No advertising/ranking-payment capability exists in the codebase | D-012 | architecture test | **COMPLETE** | `architecture/ModuleBoundaryTest.recommendationMustNotDependOnCommercialConcepts`, `.noSponsorshipConceptsExistAtAll` — build fails on violation | None material |
| PX-10 | Sanity rules at write time; never publish a partial panel as complete | D-015 | `catalog` | **PARTIAL** | `catalog/domain/DataQuality.java`, `ProductVersion.servingPlausible`/`dataQuality` columns — schema fully supports "flag, don't fabricate" | No live write path into `catalog` exists yet to exercise this at runtime — real products currently arrive only via migration/dev-seed, and `capture` contributions deliberately never reach `catalog` (they stay `PENDING`). The mechanism is schema-proven, not yet exercised by a real ingestion pipeline |

## 3. Child-data controls — Documentation Pack 06

| ID | Control | Status | Evidence | Gap / Notes |
|---|---|---|---|---|
| CH-001 | Guardian relationship and authority basis recorded | **COMPLETE** | `household/domain/GuardianLink.java` (relationship + `authorityBasis`), `HouseholdService.addChild` | None material |
| CH-002 | No child data processed until consent requirements met | **COMPLETE** | `consent/application/ConsentGate.require(...)`, called by `PersonalFitServiceImpl` before any profile data is read for a child (`CHILD_FOOD_SUITABILITY` purpose) | None material |
| CH-003 | Purpose limitation | **COMPLETE** | `consent/domain/ConsentPurpose.java` + `ConsentGate.require(subjectId, purposeCode)` — gated per purpose, not globally | None material |
| CH-004 | No targeted advertising or engagement to children | **COMPLETE** | Same evidence as PX-9 | None material |
| CH-005 | No behavioural surveillance of children | **COMPLETE** | No `activity`/consumption-tracking module exists anywhere in the codebase | Holds today by absence of the capability rather than by an explicit guard against it — re-verify this row once FR-012 (`activity`) is built, since D-005 scopes that module to "bought/not bought," not consumption tracking |
| CH-006 | Data minimisation — age band over identifiers | **COMPLETE** | `profile/domain/AgeBand.java`; `PersonProfile` has no date-of-birth column at all | None material |
| CH-007 | Consent versioning — version, timestamp, mechanism, status | **COMPLETE** | `consent/domain/ConsentPolicyVersion.java`, `ConsentRecord.java` (mechanism, status, timestamps) | None material |
| CH-008 | Guardian revocation route; delete/stop processing | **COMPLETE** | `ConsentService.withdraw` (real, immediate stop-processing); `HouseholdService.eraseChildMember` + `DataRightsService.eraseChild` (`POST /households/{id}/members/{id}/erase`, new this session) — guardian-only, hard-deletes the child's `person_profile` rows (all versions) and scrubs the membership (`leave` + `displayLabel` overwritten); `guardian_link` rows revoked | "Stop processing" and "delete" are both real now. `consent_record` and `audit_event` deliberately survive as evidence (AC-9.3, D-028) — this is the correct reading of "delete," not a remaining gap. Verified against a real database in `ChildErasureIT`, not asserted from reading the code |
| CH-009 | Child profile visible only to authorised guardians | **COMPLETE** | `householdAccess.isGuardianOf(...)` checks in `ProfileService`, `ConsentService`, `PersonalFitServiceImpl` — consistently re-derived, not shared state | None material |
| CH-010 | Privacy review before child data reaches external model providers | **NOT_STARTED** | No `ai` module exists, so no hard block (or test of one) exists | The risk surface is currently zero only because the gated capability itself doesn't exist yet — this is not the same as a deliberate, tested block being in place. Must be built alongside (not after) whatever eventually calls an external model, per D-011's own logic |

## 4. Non-functional requirements

| ID | Requirement | Target | Status | Evidence | Gap / Notes |
|---|---|---|---|---|---|
| NFR-01 | Product lookup p95 | ≤ 2 s (excl. OCR) | **NOT_STARTED** | No load-testing tooling found in the repo (no Gatling/k6/JMeter) | Phase 6 concern |
| NFR-02 | Verdict after resolution p95 | ≤ 200 ms | **NOT_STARTED** | No benchmark test found — `RuleEngineImplTest` is a correctness test, not a performance one | Phase 4/6 concern |
| NFR-03 | Search result p95 | ≤ 400 ms | **NOT_STARTED** | No benchmark test found | Phase 3/6 concern |
| NFR-04 | OCR result p95 | ≤ 8 s | **BLOCKED** | — | Depends entirely on FR-003, itself `BLOCKED` on an AI/OCR provider credential |
| NFR-05 | Availability | 99.5% | **NOT_STARTED** | No production deployment exists yet | Phase 7 concern |
| NFR-06 | Encryption in transit and at rest | TLS 1.2+; disk + column | **PARTIAL** | `platform/security/PasswordHasher.java` (Argon2id), `platform/util/Hashing.java` (opaque token hashing) protect secrets at the application layer | No TLS configuration and no column-level encryption at rest found anywhere (`infrastructure/docker-compose.yml` has no TLS/cert setup) — this is a deployment/infra task not yet started, distinct from the secret-hashing that is done |
| NFR-07 | RBAC for household members | Full permission matrix | **PARTIAL** | `household/domain/MemberRole.java` (`OWNER`/`ADULT`/`DEPENDENT`); `HouseholdIT.nonMemberIsDeniedEverywhere` ("Gate-2 IDOR"), `.removalRulesAreEnforced` — real, tested access control | What exists is solid per-service role/ownership checking (and it passes the IDOR bar Gate 2 actually requires), not a centralized `PermissionService`/resource×action matrix as originally scoped in `implementation-plan.md`. Functionally adequate for the current surface area; would need consolidation if the permission model grows more roles or resources |
| NFR-08 | Immutable audit log | UPDATE/DELETE revoked | **COMPLETE** | `V2__identity_and_audit.sql`: `trg_audit_event_no_update`/`trg_audit_event_no_delete` triggers, UPDATE/DELETE revoked from the application role at the database level | None material — enforced below the application, not just by convention |
| NFR-09 | Data delete/export | Both, tested | **COMPLETE** | New `datarights` module (`docs/decisions/decision-log.md` D-028/D-029): `GET /api/v1/account/export` (own account/households/profile history/consent ledger — including `evidence`, via the new `ConsentExportView` — /guarded children/contributions, never another person's data); `POST /api/v1/account/deletion` (immediate request, blocks sign-in, revokes every session; hard erasure runs after `familyfood.privacy.erasure-grace-period` via `AccountErasureScheduler`, anonymising `user_account` in place rather than row-deleting it). Tested: `AccountErasureIT` (request blocks login/refresh; repeated request is safe; hard erasure deletes `person_profile`, anonymises `user_account`, leaves `consent_record`+`audit_event`; a second household member, a child, and an unrelated household are all verified untouched), `AccountExportIT` (own-data scope, no password hash, guarded-child inclusion is guardian-only, no cross-member leakage) | This is Phase 2's own stated gate requirement and it is now met. Known, accepted limitations (not gaps in this requirement): no self-service cancellation of a pending deletion, and deleting the sole guardian of a child does not also erase the child (D-028) |
| NFR-10 | Observability of failures | Metric set complete | **COMPLETE** | `platform/logging/{RequestLoggingFilter,CorrelationId,SensitiveDataMasker}.java`; `spring-boot-starter-actuator` + `micrometer-registry-prometheus` in `backend/pom.xml` | A dedicated ops review of "is the metric set complete" hasn't formally run, but the infrastructure itself is real and substantial, matching Phase 1's own gate |
| NFR-11 | Offline read of recent products | Service worker cache | **PARTIAL** | `frontend/vite.config.ts`: `VitePWA`/Workbox `StaleWhileRevalidate` for `/api/v1/products/{id}` and `/api/v1/reference/`, with personal/API responses explicitly denylisted from the navigation fallback | Mechanism is real and correctly scoped (read-only, non-personal data only), but no automated E2E test verifies it — no `e2e/` directory exists in the repo despite being named in the target layout |
| NFR-12 | Accuracy over coverage | Missing beats wrong | **PARTIAL** | `DataQuality` enum, `ProductVersion.servingPlausible`, nullable per-field nutrient facts — the schema is built around "absent, not zero" | No dedicated data-quality test suite found exercising this claim directly |
| NFR-13 | Accessibility | WCAG 2.2 AA | **PARTIAL** | `aria-*`/`role=` attributes present across 17 frontend component/screen files (84 occurrences) | No axe or other automated accessibility test found, and no formal accessibility audit has been performed — matches the Phase 5 known gap named in this session's brief |
| NFR-14 | Localisation-ready | Zero hard-coded strings | **PARTIAL** | `frontend/src/locales/en-IN.json`, `lib/i18n.ts`, `i18n.vocabulary.test.ts` — substantial real externalization, actively enforced for banned vocabulary and verdict-label coverage | No ESLint config exists in the frontend at all (`frontend/.eslintrc*`/`eslint.config.*` — neither found), so there is no automated lint rule catching a *new* hard-coded string; today's discipline is convention, not a build-time gate |

## 5. Out of scope for MVP — recorded so it cannot creep back

Diagnosis · treatment · prescription · medical triage · automatic child behavioural tracking · weight-loss targets for children · advertising to children · marketplace/checkout · full meal planner · recipe engine · wearables · lab integrations · calorie diary · cart/basket optimisation · receipt import · price and availability data · commerce integration · exercise/sleep/meditation · telemedicine · pharmacy · insurance · social network or community feed · regional languages beyond English.

Sources: Pack PRD §8, Pack 04 "Do not build until validated", PANTRY 02 §8, brief §30. Landing place for future ideas: `docs/product/post-mvp-backlog.md`.

## 6. Coverage check

| Source document | Requirements | Traced |
|---|---|---|
| Pack 03 PRD — FR-001..FR-014 | 14 | 14 |
| Pack 03 PRD — NFRs | 8 | 8 |
| Pack 06 — CH-001..CH-010 | 10 | 10 |
| PANTRY 02 PRD — FR-1..FR-14 | 14 | 14 (12 mapped to Pack FRs, 2 to PX) |
| PANTRY stricter constraints | 10 | 10 |
| **Total distinct MVP requirements** | **56** | **56** |

Two PANTRY requirements are deliberately not implemented in MVP: FR-10 (display FSSAI rating — `DEFERRED`, no data source until the Indian Nutrition Rating is notified and published in a consumable form; the display slot and `reference_standard` entry exist) and FR-14 (Hindi UI — `DEFERRED` per D-019, with the string architecture in place).

## 7. Phase summary (verified, not aspirational)

```text
Phase 0 — Discovery                     COMPLETE
Phase 1 — Foundation                    COMPLETE
Phase 2 — Household, consent, profiles  PARTIAL   (IDOR/consent gating real and tested;
                                                    data export and account/child erasure
                                                    now real and tested too — NFR-09,
                                                    CH-008, FR-010 above, D-028/D-029;
                                                    D-026 consent wiring is the one
                                                    remaining open item)
Phase 3 — Product intelligence          PARTIAL   (search/barcode/manual contribution real
                                                    and tested; OCR BLOCKED, no ops-review
                                                    console, no correction queue, no real
                                                    catalogue data — FR-002/003/011, PX-10)
Phase 4 — Scoring & personalisation     PARTIAL   (deterministic engine, both-basis
                                                    evaluation, personal/household fit,
                                                    frontend wiring all real and tested;
                                                    zero active rules by design (D-024),
                                                    controller tests for the two scoring
                                                    endpoints still missing, recommendation
                                                    and AI explanation unbuilt)
Phase 5 — Core UX                       PARTIAL   (household/profile/consent/auth/
                                                    product-fit/scan-contribution screens
                                                    real and live-verified; a later session
                                                    added a real Compare screen (client-side
                                                    composition, no comparison backend,
                                                    D-027), wired up the previously-unused
                                                    product-level health assessment and
                                                    per-person "why" reasons, and added an
                                                    honest, unpersisted Outcome-capture UI
                                                    (D-027) — typecheck/tests verified but
                                                    NOT browser-verified against real data,
                                                    since the running dev database currently
                                                    has zero seeded products; still no e2e
                                                    suite, no formal accessibility audit,
                                                    no ranked alternatives (needs
                                                    `recommendation`), no outcome
                                                    persistence (needs `activity`))
Phase 6 — Quality hardening             NOT_STARTED
Phase 7 — Release candidate             NOT_STARTED
```

A phase's `PARTIAL` status here reflects a specific, named gate-critical gap, not an average of its requirement rows — see `docs/engineering/implementation-plan.md` §2 for each phase's actual gate text.

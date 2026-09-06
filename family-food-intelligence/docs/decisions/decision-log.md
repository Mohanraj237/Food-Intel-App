# Decision Log — Family Food Intelligence

Every material product, architecture, security, data, privacy or scope decision.
Append-only. Supersede by adding a new entry that references the old one; do not edit history.

**Status vocabulary:** `ACCEPTED` · `SUPERSEDED` · `PROVISIONAL` (holds until a named external input arrives)

---

## Source-of-truth hierarchy established for this project

Three documentation sets exist in the repository and they do not agree. Ranking used throughout:

| Rank | Set | Location | Standing |
|---|---|---|---|
| 1 | This decision log | `docs/decisions/` | Authoritative once an entry is `ACCEPTED` |
| 2 | Engineering Constitution | `family-food-intelligence/CLAUDE.md` | Authoritative on engineering practice and product policy |
| 3 | Documentation Pack v0.2 | `Family_Food_Intelligence_Documentation_Pack_v0.2/` (11 docs, 05 Sep 2026) | Authoritative product spec — PRD > BRD > Strategy |
| 4 | Project PANTRY set | `files/` (6 docs, Sep 2026) | Independent critical/market intelligence. Not a competing spec. Used as a **risk and legal-safety lens**; where it is *stricter* than the pack, the stricter reading is adopted |

Rationale: the Documentation Pack is the commissioned product specification set — its index (doc 00) enumerates documents 01–11 with named owners and its own decision log. The PANTRY set is explicitly framed as a challenge document written against unconfirmed assumptions ("If an assumption is wrong, the affected documents are listed"). It is superb risk analysis and its legal reasoning is more rigorous than the pack's — so it governs where it **narrows** exposure, but it does not get to delete features the commissioned PRD marks Must.

---

## D-001 — Child profiles remain in the MVP, as a consent-gated guardian-controlled capability

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** Documentation Pack PRD FR-010 and MVP Scope R0.1 both include a parent-controlled child profile. PANTRY 02-PRD §8 and 05-Compliance §1 defer child profiles entirely to V1.2, after legal review, on the grounds that two consent problems cannot be solved at once by a small team.

**Decision.** Build the child-profile capability in full — domain model, guardian relationship, consent ledger, purpose limitation, access control, deletion, audit — but ship it behind a **hard processing gate**. No child personal data is processed, scored, or sent to any AI provider until a `GuardianConsent` record is `ACTIVE` for the specific purpose being invoked. The Documentation Pack's own MVP Scope wording is "Parent-controlled child profile design/consent shell", which is precisely this.

The verifiable-consent *mechanism* required by DPDP Rules 2025 is **PROVISIONAL and BLOCKED on privacy counsel**. MVP ships a `VerifiableConsentProvider` port with one implementation — `GuardianAttestationProvider` — which records a guardian attestation with full audit evidence and is explicitly marked `REQUIRES_LEGAL_SIGNOFF`. It is not represented to the user, or in our documentation, as DPDP-verifiable consent. Production launch with real child data is a documented release blocker.

**Alternatives rejected.** (a) Drop child profiles — contradicts the commissioned PRD, the Engineering Constitution and the orchestration brief; retrofitting consent architecture later is exactly the mistake DPDP penalises. (b) Ship a checkbox as verifiable consent — misrepresents compliance; ₹200 crore penalty exposure per PANTRY 05 §1.

**Consequences.** Child profiles are configurable in MVP; verdicts for a child render only after consent is `ACTIVE`; the child pathway carries a permanent "not cleared for production" flag until counsel signs off. Cohort testing runs adults-only, honouring PANTRY's sequencing without losing the architecture.

---

## D-002 — Adults are their own data principals. No proxy consent for an adult, ever

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** Documentation Pack FR-009 ("add/remove household members") is silent on who consents. PANTRY 05-Compliance §1.2 is explicit: a household head cannot consent on behalf of a spouse or an elderly parent to processing of their health conditions.

**Decision.** Adopt the stricter PANTRY reading. Adding an adult sends an **invitation**; that adult creates their own account and grants their own consent on their own device. Withdrawal by one adult removes their profile and verdicts from household views without breaking the household for anyone else.

**Consequences.** The household-invite funnel will convert poorly. That is instrumented as a product finding (PANTRY R-02), not papered over. A household of one is a fully valid, fully useful state and the app must never nag toward household completion.

---

## D-003 — A numeric score exists and is explainable; the **verdict** is the headline

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** Documentation Pack 08 §2 specifies a Product Health Score of "0–100 or grade". PANTRY 02-PRD §6 bans "any numeric score presented as the headline". The orchestration brief §13 requires that a user can ask "Why did this score 56?" and get an answer.

**Decision.** Compute a deterministic, versioned `ProductHealthScore` (0–100, category-aware). Persist it with its rule-set version and inputs. Expose it through the API and in an expandable "How this was assessed" panel with a full factor breakdown. **The primary visual element on every product screen is the per-person verdict chip, not the number.** The score never appears as the largest element on a screen and never appears above the verdict.

**Consequences.** Satisfies the pack's transparency requirement and PANTRY's liability reasoning simultaneously. Also survives FSSAI's Indian Nutrition Rating becoming mandatory (PANTRY R-08): the regulatory rating is displayed separately and labelled as the regulator's, and our score degrades gracefully to a secondary detail.

---

## D-004 — Fixed verdict enum, enforced by the type system

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** One closed enum, used by engine, API and UI:

| Enum | Display (en-IN) | Meaning |
|---|---|---|
| `EVERYDAY` | Good everyday option | Fits this person's stated goals as a regular choice |
| `OCCASIONAL` | Fine occasionally | Fine sometimes; not a staple for this person |
| `LIMIT` | Better limited | Works against a stated goal; the specific driver is named |
| `DOESNT_FIT` | Doesn't fit | Conflicts with a declared preference or restriction |
| `INSUFFICIENT_DATA` | Not enough data yet | Product facts are missing or in conflict; no verdict is claimed |

`CONTAINS_ALLERGEN` is **not** a verdict. It is a separate hard flag with its own field, its own colour, its own position at the top of the screen, and it short-circuits the engine.

Banned vocabulary — toxic, poison, dangerous, harmful, never eat, cannot consume, safe for everyone, clean — is unrepresentable: there is no enum value for it and no free-text verdict field. A test scans user-facing string bundles and rule explanation templates for the banned list and fails the build on a hit.

**Consequences.** Reconciles Pack 08 §2 ("Good / occasional / limit / conflict"), Pack 10 §2, PANTRY §6 and brief §33. `INSUFFICIENT_DATA` is added because every source demands honesty about missing data and none of them named the state.

---

## D-005 — No meal logging in MVP. Outcome capture instead

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** The orchestration brief §8.9 and CLAUDE.md §8 permit optional low-friction meal capture. Documentation Pack 04 puts meal capture in "Release 1+" and only "optional purchase event" in R0.2. PANTRY excludes meal logging outright and makes outcome capture a Must (FR-8), calling it the only place the thesis is measurable.

**Decision.** No meal logging, no calorie diary, no photo meal inference in MVP. Implement **outcome capture**: after a product view, one screen, one tap — *bought this · bought an alternative · bought neither · skip*. Never more than once per product. This is Pack FR-012 narrowed to its purchase half, and it is the north-star instrument (Healthy Decision Rate).

**Consequences.** The `FoodEvent` entity from Pack 05 §2 is modelled with an event-type discriminator so consumption events can be added later without migration pain, but only `PURCHASE_OUTCOME` is implemented and reachable.

---

## D-006 — Stack: Java 21 + Spring Boot modular monolith, PostgreSQL, React/TypeScript PWA

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** PANTRY 06 §2 recommends React Native (Expo) + Node/TS or Python/FastAPI, reasoning from assumption A1 (a 1–3 person team shipping a 120-day validation instrument). The orchestration brief states a default preference of Java 21 / Spring Boot / PostgreSQL / TypeScript React. The repository is empty, so no existing stack constrains the choice.

**Decision.** Follow the brief.

- **Backend:** Java 21, Spring Boot 3.x, modular monolith (Maven multi-module, boundaries enforced by ArchUnit), PostgreSQL 16, Flyway.
- **Frontend:** React + TypeScript + Vite, mobile-first installable PWA.
- **Search:** PostgreSQL `tsvector` + `pg_trgm`. A few thousand SKUs does not need a search cluster; brand aliases matter more than the engine (PANTRY 06 §2, adopted).
- **Storage:** S3-compatible object storage; MinIO in dev.
- **Tests:** JUnit 5 + Testcontainers + Playwright.

**Barcode scanning in a PWA** is the one real cost of not going native. Mitigated with the native `BarcodeDetector` API where present (Chrome/Android — the dominant Indian platform) and a WASM ZXing fallback elsewhere, behind a `BarcodeScanner` port so a native shell can be added without touching domain code.

**Reversal condition.** If cohort testing shows in-store scanning dominates and PWA scan reliability measures below ~85% first-try success, wrap the PWA in a thin native shell rather than rewriting.

---

## D-007 — No Redis in MVP

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** The brief permits "Redis where justified". At MVP scale the justifications (hot-product cache, rate-limit counters) are served by in-process Caffeine caches and a database-backed rate limiter, with one fewer piece of infrastructure to secure, back up and monitor. A `CacheProvider` port keeps the swap cheap.

**Reversal condition.** More than one application instance, or rate limiting that must be globally consistent.

---

## D-008 — Email + password authentication for MVP; phone/OTP is a deferred adapter

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Phone-first identity is the right long-term choice for India and PANTRY 06 §3 models `phone_hash`. It requires an SMS provider contract we do not have. MVP implements email + password (Argon2id) behind an `AuthenticationMethod` abstraction, with the schema already carrying a nullable hashed-phone column. Phone/OTP is **BLOCKED — awaiting SMS provider credential** and is recorded as such, never as done.

---

## D-009 — Every scoring rule requires a citable public standard or it cannot be activated

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** A rule without a resolvable `standard_ref` fails validation at write time and can never enter `ACTIVE` state. Permitted standards for MVP are enumerated (WHO free sugars 2015, WHO sodium 2012, ICMR-NIN Dietary Guidelines for Indians 2024, ICMR-NIN RDA 2020, FSSAI thresholds / Indian Nutrition Rating). This is Pack 08 §5 and PANTRY 06 §4 in agreement, and it is the primary defence against the FMCG-disparagement exposure documented in PANTRY 05 §3.

No rule may originate from an LLM labelling an ingredient "bad" (Pack 08 §5).

---

## D-010 — Both per-serving and per-100g are evaluated for every rule

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Indian pack serving sizes are frequently unrealistically small, so a per-serving-only verdict is manufacturer-gameable (PANTRY 06 §4). The engine evaluates both bases, the more severe verdict wins, and the UI states the declared serving explicitly and flags it when it is implausible for the category.

---

## D-011 — AI never produces a number, a threshold, a verdict, or an authorization decision

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** The deterministic engine computes; AI explains, extracts and translates. Enforced structurally, not by prompt wording:

- The explanation endpoint receives an already-computed verdict object and may only render prose from it. Its output passes a **numeric-fidelity check** — any number in the generated text that is not present in the structured input causes the AI output to be discarded and the deterministic template served instead.
- OCR output is a *proposal*. It cannot reach a verdict or the catalogue without human confirmation (PANTRY 03 §6, Pack 05 §5).
- All product-derived text (names, ingredients, OCR, external API responses) is passed to models inside a delimited untrusted-data envelope with a standing instruction that content inside it is data and never instruction. Injection probes are part of the test suite.
- The model has no tool access to authorization or data-retrieval decisions (Pack 11 §3).

**Consequences.** Every AI feature degrades to a deterministic template rather than failing. AI is never on the critical path for a verdict.

---

## D-012 — No advertising, no sponsored ranking, no data sale — absent from the codebase, not just the policy

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** No field, column, config flag or code path exists by which a commercial relationship can influence a score, a verdict, or an alternatives ranking. PANTRY's pre-launch checklist requires the *capability* to be absent. An architecture test asserts the recommendation module has no dependency on any commercial or partner module.

---

## D-013 — Alternatives must publish their trade-offs

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Every alternative shown persists both `better_on` and `worse_on`. If the alternative is worse on any tracked dimension, that is displayed alongside the improvement. The empty state — "no better option in our data yet" — is a first-class result and the list is never padded. One dishonest substitution costs more trust than ten correct verdicts earn (PANTRY 06 §5).

---

## D-014 — Product facts are versioned and never overwritten

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** `product` holds stable identity; `product_version` holds facts, source, capture method, confidence and verification state. A formulation change creates a new version and marks the old one superseded. Every verdict stores the exact product version, profile version and rule-set version that produced it, making any past verdict reconstructible for a user asking "why did it say that" and for a brand's counsel asking the same question.

---

## D-015 — Source is recorded per field, not per product

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** PANTRY 03 §7 requires per-field provenance; Pack 05 §3 requires a source and last-verified timestamp on every product fact and a retained conflict state where sources disagree. A single `source_type` on the version satisfies neither. Nutrient facts carry their own provenance rows.

**Consequences.** More write complexity, and the only way to honestly answer "where did this number come from" at the granularity a dispute will demand.

---

## D-016 — No automated scraping of e-commerce. No commercial nutrition APIs in MVP

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Permitted sources: photographed physical packs, brand-published panels, Open Food Facts (ODbL — attribution required, contribute back), FSSAI/ICMR-NIN public references, and human cross-check of public pages recorded as cross-check only. Automated scraping is prohibited in code and in process (ToS breach on every major platform; IT Act §43 exposure). US-centric commercial nutrition APIs are declined — poor Indian coverage at $299–1,850/month buys the wrong catalogue.

---

## D-017 — Excluded populations are enforced in code

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** The product produces no numeric targets and no condition-specific rules for pregnancy nutrition, paediatric therapeutic diets, CKD, oncology, or any goal framed around rapid weight loss or restriction. No weight, body-fat or body-composition goal exists for any profile, and for a child profile no such field exists at all — the goal vocabulary that could express restriction is simply not offered. Where a declared condition falls in the excluded set, the app returns general nutrition information plus a route to a qualified professional.

---

## D-018 — Search text and phone numbers are hashed; no third-party analytics SDK

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Analytics events are written to our own PostgreSQL and exported for analysis. No third-party analytics SDK ships before privacy counsel review (PANTRY 06 §7). Free-text search queries are stored hashed. Consent is checked at processing time against the consent ledger, not merely captured at signup.

---

## D-019 — MVP is English (en-IN) only, fully externalised

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Hindi is `Could` in Pack PRD FR-14 and Release 1.1 in PANTRY. MVP ships English only, but zero user-facing strings are hard-coded: all copy lives in locale bundles from day one, including verdict labels, rule explanation templates and error messages. Adding Hindi is then a translation task, not a refactor.

---

## D-020 — Registration is required before a personal verdict; anonymous browsing is allowed

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** Brief §9 says "do not force registration before demonstrating useful product intelligence unless technically or legally necessary". A personal-fit verdict is computed *from* a health profile, which cannot exist without a data principal and a consent record.

**Decision.** Anonymous users may search, open a product, and see product-level facts and the product-level assessment. **The per-person fit strip is where sign-in is required**, and the prompt at that boundary states plainly why. This gives the demonstration the brief asks for while keeping consent legally coherent.

---

## D-021 — One Maven module; boundaries enforced by ArchUnit, not by POM graph

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** `docs/architecture/system-architecture.md` specifies thirteen modules. The question is whether those become thirteen Maven modules or thirteen enforced packages in one.

**Decision.** One Maven module. Module boundaries are packages under `com.familyfood.*`, and an ArchUnit suite fails the build on any violation — including the two rules that carry product weight: `scoring` must not depend on `ai` (D-011) and `recommendation` must not depend on any commercial concept (D-012).

**Rationale.** Thirteen POMs buy compile-time rejection instead of test-time rejection, in exchange for fourteen build files, slower builds and expensive refactoring, on a codebase with one team and one deployable. A failing build is a failing build. This is the boring choice the Constitution §3 and the brief §5 both ask for.

**Reversal condition.** A second deployable, or a module that genuinely needs an independent release cadence.

---

## D-022 — Barcode scan progressive enhancement

**Date:** 2026-09-05 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** Use the browser's native barcode detector and rear camera only where available; manual GTIN entry is the universal path and uses the same lookup API. Camera frames and decoded values remain in memory for the active lookup only. Label OCR remains visibly unavailable until its secure processing and confirmation flow exists.

---

## D-023 — Cross-module membership facts flow through one published interface, never a shared repository or entity

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** `profile`, `consent`, and eventually `scoring`/`capture`/`activity` all need the same fact — is the caller this household member, or a live guardian of this (child) member — but the module table (`docs/architecture/system-architecture.md` §3) does not let any of them depend on `household`'s persistence layer, and the `ConsentRecord`/domain style already established (subject ids stored as raw `UUID`, never a JPA relationship into another module's entity) rules out an object-graph shortcut.

**Decision.** `household` publishes exactly one interface for this, `com.familyfood.household.application.HouseholdAccess`:
```java
public interface HouseholdAccess {
    Optional<MemberInfo> findMember(UUID memberId);
    boolean isGuardianOf(UUID guardianAccountId, UUID childMemberId);
    record MemberInfo(UUID id, UUID householdId, UUID userAccountId, String memberType, boolean active) {}
}
```
`profile` and `consent` each constructor-inject it and apply the identical authorization shape: a departed or unknown member is `RESOURCE_NOT_FOUND`; a `CHILD` subject requires `isGuardianOf(caller, subjectMemberId)` else `NOT_A_GUARDIAN`; an `ADULT` subject requires `caller.equals(member.userAccountId())` else `ACCESS_DENIED`. `memberType` stays a raw string (`"ADULT"`/`"CHILD"`, the DB's own CHECK values) rather than a shared enum type, so no module needs a compile-time dependency on another module's enum.

**Rationale.** This is D-001/D-002 (guardian-only child data, self-only adult data) made structural in exactly one place instead of re-derived per module. A future module (e.g. `scoring` reading a profile to compute a household-fit verdict) extends the same pattern rather than inventing a `PermissionService` god-object or reaching into `household`'s repositories directly — the latter is exactly what `ModuleBoundaryTest#repositoriesAreNotSharedAcrossModules` exists to catch.

**Reversal condition.** A third or fourth consumer needing materially different membership facts (e.g. role, not just guardian/self) — extend `HouseholdAccess`'s method set then, don't fork a second interface.

**Rationale.** This preserves a quick-commerce interaction without relying on a third-party SDK or presenting a decorative but non-functional camera screen.

---

## D-024 — Phase 4 scoring foundation: no fabricated health judgment, `scoring` reaches `household` only through `HouseholdAccess`, household fit is caller-scoped

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** Phase 4 asks for a deterministic `ProductHealthScore` and `PersonalFit`/`HouseholdFit` pipeline. The repository defines the *shape* of this pipeline in detail (`system-architecture.md` §5, `database-design.md` §8: `reference_standard`, `rule_set`, `scoring_rule`, `score_component`, `product_score`) but nowhere specifies the actual numeric thresholds a rule or score component should use (e.g. how many grams of added sugar per 100 g triggers `LIMIT`). Separately, implementing `HouseholdFit` requires enumerating a household's members, which only `household`'s published `HouseholdAccess` (D-023) can answer — but `ModuleBoundaryTest#repositoriesAreNotSharedAcrossModules` currently defines `Household` as a layer no other named layer (including the already-declared, still-empty `Scoring` layer) may access at all, with no carve-out for the published-interface package. `profile` already depends on `HouseholdAccess` today only because it was never added as a named layer to that same rule — an inconsistency this decision resolves for `scoring` explicitly rather than by the same accident.

**Decisions.**

1. **No invented numeric health thresholds.** Building a production `ProductHealthScore`/nutrient-based `scoring_rule` set requires numbers (e.g. "added sugar > X g/100 g ⇒ `LIMIT`") that no document in this repository specifies, and getting one wrong is exactly the kind of fabricated medical judgment the orchestrating brief for this phase forbids guessing on. The schema and rule engine are built in full and are production-grade; the `ACTIVE` `rule_set` ships with **zero** `scoring_rule` / `score_component` rows. `ProductHealthScore` therefore always reports `status=NOT_YET_SCORED` and `PersonalFit`'s nutrient-goal dimension always reports `verdict=INSUFFICIENT_DATA, reasonCodes=[NO_ACTIVE_RULES]` — honest, not a bug — until a nutrition-reviewed threshold set is supplied and activated as a new `rule_set` version. A second, `DRAFT` (never `ACTIVE`) rule_set with illustrative example rules exists solely so the engine itself (versioning, basis evaluation, most-severe-wins, reason codes) is unit-tested end-to-end without those numbers ever reaching a real user.
2. **Allergen match is real and ships now.** Matching a household member's recorded `AllergenCode`s against a product's declared allergens is a factual set-membership check, not a health judgment, so it is implemented as a genuine, active `PersonalFit` dimension from day one — surfaced as its own field (`allergenMatches`), never folded into the `verdict` enum (D-004: allergen is a separate hard flag, not a verdict).
3. **`scoring` reaches `household` only through `HouseholdAccess`, never `household.domain`/`infra`/`api`.** `ModuleBoundaryTest#repositoriesAreNotSharedAcrossModules`'s `Household` layer is redefined to cover only `com.familyfood.household.domain..`, `com.familyfood.household.infra..` and `com.familyfood.household.api..` — the previously-undifferentiated `com.familyfood.household..` layer definition is narrowed so that `household.application` (where `HouseholdAccess` lives) is reachable by a named layer while the module's actual persistence and HTTP surface stay walled off. This makes the exact extension D-023 anticipated ("a future module, e.g. scoring") a live, enforced rule rather than one more module quietly exempted from the check by never being named in it. `HouseholdAccess` itself gains one additive method, `listActiveMembers(UUID householdId)`, backed by the repository call `household`'s own controller already uses — no new persistence, no duplicated membership logic.
4. **Household fit is scoped to what the caller may see, not "everyone in the household."** D-002 means an adult's profile may only ever be read by that adult's own account — not even the household owner. `HouseholdFit` for a request therefore returns a personal-fit entry only for the caller's own membership and any child they are a guardian of; other adult members are silently absent from the response (never a 403 for the whole request), exactly mirroring how the frontend `ConsentPanel` already treats another adult's non-essential consent purposes. This is D-002 applied consistently, not a new restriction invented for scoring.

**Alternatives rejected.** (a) Guess plausible WHO/FSSAI numbers from general knowledge — rejected outright; a wrong number here is a trust-destroying, potentially harmful defect in a product whose entire premise is a citable reason for every claim (D-009). (b) Give `scoring` a copy of household membership data — rejected as exactly the duplicated-membership-logic D-023 exists to prevent. (c) Compute household fit for every member regardless of who is asking, relying on the API caller being "trusted enough" — rejected as a direct violation of D-002.

**Reversal condition.** Nutrient-based `scoring_rule`/`score_component` rows activate only when a task explicitly supplies a nutrition/regulatory-reviewed threshold set with citable sources — at that point they are added as a new versioned, `ACTIVE` `rule_set`, superseding the empty one; no historical `product_score`/verdict becomes wrong, since each stores the `rule_set` version it was computed under (D-014).

---

## D-025 — Scoring rule content is authored through a controlled evidence pipeline, never generated from Claude's general knowledge

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (at the user's explicit direction)

**Context.** D-024 shipped the scoring engine with zero active rules rather than let Claude guess a threshold. That resolves *this* session, but not the next one: without a standing rule, a future session under time pressure could reasonably read "build Phase 4 scoring rules" as an instruction to research thresholds and write them into a migration — recreating exactly the problem D-024 avoided. The user's own direction, verbatim: *"Don't ask Claude to 'research some healthy food thresholds and create rules.' That would recreate the exact problem you just avoided."*

**Decision.** Real `scoring_rule`/`score_component` content is produced only through this pipeline, never any shorter path:

```
domain source → reviewed specification → migration → automated tests → activate rule_set
```

1. **A human-owned specification precedes any migration.** Before a production `scoring_rule`/`score_component` migration is written, a specification must exist naming, per rule: rule id, nutrient/fact, unit, basis (per-100g/100ml/serving), applicable category, population/age scope, threshold, severity, score impact, reason code, source, source date/version, reviewer, rule_set version, effective date, and confidence/verification status. Claude may format, structure, or transcribe such a specification once a human supplies the source content; Claude does not originate the threshold values themselves.
2. **Source, interpretation, and implementation are recorded separately and never conflated.** For every rule: (a) what the authoritative body actually published, (b) how that was translated into this application's rule, (c) how `RuleEngine` executes it — three distinct, separately-reviewable statements, not one paragraph blending fact and judgment. This lives in `docs/data/scoring/` (see its `README.md` for the required layout: `scoring-methodology.md`, one `rules-vN.md` per rule_set version, an `evidence/` directory with one file per cited source, and a `review.md` recording who approved activation and when).
3. **A `rule_set` activation is a reviewed release, not a routine commit.** Treat it with the same weight as a schema migration touching production data: a named reviewer, a dated approval, and a migration that flips the new `rule_set` to `ACTIVE` while retiring the superseded one in the same transaction (the partial-unique-index invariant from D-024 means both must happen together).

**Alternatives rejected.** (a) Let a future session interpret "add scoring rules" as license to research and decide thresholds itself — rejected for the reason quoted above. (b) Keep the evidence pipeline as an unwritten convention rather than a decision-log entry — rejected because the whole point of this repository's decision log is that a convention which matters this much must be discoverable by a session that never saw the conversation where it was decided.

**Reversal condition.** None anticipated. If a future emergency genuinely requires bypassing this (it should not), that bypass is itself a new decision-log entry with an explicit owner and expiry, not a silent exception.

---

## D-026 — `PRODUCT_IMPROVEMENT` consent is not wired to the new contribution endpoint; flagged, not fixed

**Date:** 2026-09-06 · **Status:** OPEN (deliberately unresolved) · **Owner:** Orchestrator

**Context.** `V1__extensions_and_reference_data.sql` seeds a `PRODUCT_IMPROVEMENT` consent purpose ("Contributing a product to the catalogue") that reads as if it were meant to gate exactly the manual-entry contribution endpoint (`POST /api/v1/contributions`, new `capture` module) built this session. It does not gate it — the endpoint accepts any signed-in caller's submission unconditionally. This was caught by the implementing agent's own review, not missed silently.

**Why it wasn't wired anyway.** `ConsentGate.require(subjectMemberId, purposeCode)` is scoped to a household member, not an account — every existing purpose (`ACCOUNT`, `PERSONAL_FIT`, `CHILD_FOOD_SUITABILITY`, `LABEL_IMAGE_PROCESSING`) concerns processing *a specific person's* data. Contributing a product is an account-level action with no household-member dependency at all — a signed-in account with no household yet (the common case for someone who just registered to report a missing barcode) has no member id for `ConsentGate` to check against. Wiring it as-is would not gate the intended purpose; it would simply block every contribution from anyone without a household, which is very likely not what seeding this purpose was for.

**Decision.** Ship the contribution endpoint without a consent check now, exactly as built, rather than force-fit the existing member-scoped `ConsentGate` onto an account-level action under time pressure — the same reasoning D-024/D-025 apply to scoring thresholds applies here: a wrong fix to a real gap is worse than an honestly-labelled gap. This is recorded as `OPEN`, not resolved, so it cannot be mistaken for "consent was considered and correctly deemed unnecessary."

**What resolving this properly would need** (not decided, listed so a future session doesn't have to rediscover the options): (a) extend `ConsentGate`/`ConsentRecord` to support an account-scoped subject in addition to a household-member one, or (b) treat `PRODUCT_IMPROVEMENT` as implicitly granted by the act of submitting (a terms-of-contribution acknowledgment rather than a revocable consent record, which may not even belong in the same ledger as personal-data purposes), or (c) determine the purpose was seeded speculatively and retire it if a proper design review concludes contribution shouldn't be consent-gated at all. Any of these is a deliberate design decision, not an implementation detail.

**Reversal condition.** Resolved the moment a future task explicitly designs the account-vs-member consent-subject question and implements one of the options above (or a better one) — at which point this entry's status changes from `OPEN` to a normal `ACCEPTED`/superseded entry.

---

## D-027 — Phase 5 Compare and Outcome capture: client-side composition of existing endpoints, no fabricated backend, no false persistence claim

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** Phase 5's brief asks for a Compare experience and an Outcome-capture interaction (D-005). Neither has a backend: no `recommendation` module exists for a comparison API (FR-007/PX-2, `NOT_STARTED`), and no `activity` module exists to persist a bought/skipped decision (FR-012, `NOT_STARTED`). The brief is explicit that a missing backend capability must be documented and worked around, never faked with invented API responses or a UI that claims to save when it does not.

**Decisions.**

1. **Compare is a client-side composition of endpoints that already exist**, not a new backend contract. `features/compare/CompareScreen.tsx` loads two products the caller has actually navigated to or searched for (`getProduct`), and renders one row per dimension using the same endpoints `ProductScreen` already calls for a single product — `getProductHealth` for the product-level assessment, `getHouseholdFit`/`listMembers` for the per-person verdicts (caller-scoped exactly as D-024 pt. 4 requires), and the product's own `nutrients`/`allergens`/`ingredients` fields for the rest. No row is invented; a dimension with no real per-product value renders the same "not declared" honesty `ProductScreen`'s nutrition table already uses. The second product is chosen through an inline search picker (local component state, not a new route) rather than a dedicated "select products to compare" screen — consistent with the existing app's preference for local state over route proliferation (`ScanScreen`'s contribute toggle is the precedent).
2. **The product-level health assessment (`GET /products/{id}/assessment`) and the structured `Reason[]` explanation, both already implemented in Phase 4 but never called from the frontend, are wired up now.** `HealthAssessmentSection` (collapsed `<details>`, below the per-person verdict, never the screen's largest element — preserving D-003 exactly as `ProductScreen`'s existing information-order comment already commits to) and a new `MemberFitRow` "why" disclosure (`ReasonList`, `lib/reasons.ts`) render the same `Reason` shape `scoring/application/Reason.java` already emits. Since D-024 ships zero active rules, every real call today resolves to `NOT_YET_SCORED`/`NO_ACTIVE_RULES` — rendered as the honest, already-drafted `why.noRules`/`score.notYetScored` copy, not as an error.
3. **Outcome capture (`features/product/OutcomeCapture.tsx`) is a real, isolated UI with no network call at all.** Selecting an option never claims the choice was saved — it shows `outcome.notPersisted` ("this is a preview... not connected to your account"), a new locale key added specifically so the existing `outcome.thanks` copy (which reads as a persisted acknowledgment) is not reused dishonestly ahead of a real `activity` backend. Once that backend exists, only this component's `onChange` body needs to call it — the UI contract does not change.

**Rationale.** Every option that would have made Compare or Outcome look more finished right now — a mocked comparison-score API, a fabricated "Saved!" toast — is exactly the fabricated-data failure mode D-024's own precedent (ship the honest, incomplete mechanism rather than invent content) exists to prevent. Composing real, already-authorized endpoints client-side is not the same thing as inventing a response: every number shown is a real value from a real backend call for a real product the caller selected.

**Alternatives rejected.** (a) A mocked `/compare` backend response — rejected, indistinguishable from fabricated data once shipped. (b) Skipping Compare/Outcome entirely until backend modules exist — rejected; the brief calls for exactly this "build the reusable UI/model against a clearly isolated adapter, document the backend dependency" path, and the existing locale bundle already contains `compare.*`/`outcome.*` keys clearly prepared for this. (c) Reusing `outcome.thanks` for the unpersisted case — rejected as a false persistence claim.

**Reversal condition.** Once a `recommendation`/comparison backend exists, `CompareScreen`'s per-dimension data fetching can be replaced by a single comparison endpoint without changing its rendering; once an `activity` backend exists, `OutcomeCapture` gains a mutation and its confirmation copy changes from `outcome.notPersisted` to `outcome.thanks`. Neither is a rewrite.

---

## D-028 — Account erasure: two-phase (request + scheduled hard erasure), pseudonymise `user_account` rather than delete it, no cancellation endpoint

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (data-rights session)

**Context.** NFR-09/CH-008/FR-010 required closing the account/child-erasure gap the requirements-traceability audit (`e641dd3`) made explicit: no export or erasure endpoint existed anywhere, and `HouseholdService.removeMember` was only ever a soft "leave." Before writing any deletion code, the existing schema and identity module were inspected for intent already encoded there, per this session's own instruction not to invent a new deletion framework — and a real one was found: `user_account.status` already had a `DELETION_REQUESTED` value, `deleted_at`/`UserAccount.markDeletionRequested`/`isUsable()` already existed, `familyfood.privacy.erasure-grace-period` (`P30D`) was already configured, and `UserAccountRepository.findByDeletedAtBefore` was already commented "for the scheduled hard-delete job" — none of it wired to an endpoint or a scheduler. `AuditAction` already carried `ACCOUNT_DELETION_REQUESTED`, `ACCOUNT_DELETED`, `CHILD_DATA_DELETED`, `DATA_EXPORTED` and `DATA_ERASURE_COMPLETED`, unused. `V3__household_consent_profile.sql`'s own comments were explicit about the shape required: `household_member.user_account_id` has no `ON DELETE` action "because a membership referenced by a surviving `consent_record` must not disappear when an account row is finally hard-deleted. Erasure detaches the person from the household through `left_at` and a scrubbed label instead."

**Decisions.**

1. **Two phases, exactly as the schema already anticipated.** `POST /api/v1/account/deletion` (`AccountErasureService.requestDeletion`) is immediate: `markDeletionRequested` (blocks sign-in via `isUsable()`), every refresh token revoked (`RefreshTokenRevoker`), `ACCOUNT_DELETION_REQUESTED` audited. Actual erasure runs later, via `AccountErasureScheduler` (hourly `@Scheduled`, a fixed constant with no configuration knob — the same "reviewable constant, not a tunable" choice `HouseholdService.INVITATION_TTL` already makes), once `familyfood.privacy.erasure-grace-period` has elapsed. Tests trigger the hard-erasure step directly (`DataRightsService.performFullErasure`) rather than waiting 30 real days or manipulating the clock.
2. **`user_account` is never row-deleted — it is anonymised in place.** `UserAccount.eraseIdentity` overwrites `email` (to a synthetic, unique, lower-case `erased-{accountId}@erased.invalid` — freeing the real address for re-registration), `password_hash` (to a real Argon2id hash of random bytes, so `PasswordHasher.matches` never throws on a malformed value, even though `isUsable()`'s `status != ACTIVE` check would already block sign-in regardless of what the hash contains), and `phone_hash` (to `null`), and sets a new terminal `AccountStatus.ERASED` (added to the CHECK constraint in `V7__data_rights.sql`). This exactly mirrors `audit_event`'s own documented "pseudonymise the actor, don't remove the record" treatment, extended to the one other table every other table's `ON DELETE`-less foreign key ultimately points at. `household_member` rows the account held are similarly scrubbed in place (`HouseholdMember.scrubForErasure`: `leave()` + `displayLabel` overwritten to a fixed `"Erased member"` placeholder), never row-deleted, for the same referential reason.
3. **`consent_record` and `audit_event` are never touched by any erasure path in this session** (AC-9.3). `ProfileService.eraseAllVersions` and `HouseholdService.eraseAccountMemberships`/`eraseChildMember` reach only `person_profile`/`household_member`/`guardian_link` — none of them import `consent.infra` or attempt to write `audit_event` (which the database would refuse regardless — `trg_audit_event_no_update`/`no_delete`). This was verified in integration tests against a real Postgres, not asserted from reading the code: `AccountErasureIT`/`ChildErasureIT` query `consent_record`/`audit_event` row counts directly after erasure.
4. **No cancellation endpoint exists for a `DELETION_REQUESTED` account.** This is a real, deliberate gap, not an oversight: once `markDeletionRequested` runs, `isUsable()` is false, so the account cannot sign back in to ask for a reprieve even if a cancel endpoint existed — an anonymous "cancel my pending deletion" endpoint would itself be a new authentication problem (who may cancel someone else's deletion?), and building one was judged out of proportion for this session's scope (it is a UX/product feature, not a data-lifecycle correctness gap). The 30-day grace period today only delays when hard erasure runs; it does not yet give the account holder a way to stop it. Flagged for a future session, not solved here.
5. **An account that is the sole guardian of a child is not blocked from deleting itself, and the child's data is not touched.** A child is a separate data subject (D-001/D-002); an adult's unilateral account deletion does not carry authority to also erase someone else's data, symmetric with the equivalent household-safety requirement (deleting one adult must never affect another member). This can leave a child "orphaned" (no live guardian) in a single-guardian household — a real product-level gap, not resolved here, since reassigning or requiring a second guardian before deletion is a new workflow this session's scope did not ask for and the household module does not currently support.

**Alternatives rejected.** (a) Hard-delete the `user_account` row outright — rejected because `consent_record.granted_by_user_id`, `household_member.user_account_id`, `guardian_link` (transitively) and `audit_event.actor_user_id` all reference it with no `ON DELETE` action by deliberate prior design (V2/V3's own comments); a real `DELETE` would either violate those foreign keys or require cascading through rows this session's own brief says must survive. (b) Erase immediately on request, no grace period — rejected: the grace period was already a considered part of the schema (config property, repository query, column comment) before this session began, and immediate erasure removes any window for a compromised-account or accidental-click scenario to be caught by whoever operates the scheduled sweep, even without a self-service cancel path. (c) Block deletion while an account is a child's sole guardian — rejected as an unrequested new workflow (guardian reassignment/handoff) rather than a data-lifecycle fix; documented as a known limitation instead of guessed into existence.

**Reversal condition.** A future session may add a genuine cancellation flow (e.g., a separate, narrowly-scoped "reactivate" token emailed at request time, independent of normal sign-in) — that supersedes point 4 without changing points 1–3. A future session may also add guardian-handoff/reassignment to `household`, at which point point 5's "orphaned child" gap can be closed by requiring a handoff before the last guardian's account may be deleted.

---

## D-029 — Erased product contributions: anonymise the submitter link, never delete the submission's content

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (data-rights session)

**Context.** Account erasure needed a decision this repository had not yet made anywhere: is a `product_contribution` row (the manual-entry queue `capture` added this session's predecessor) the submitter's personal data (delete it on erasure), or catalogue-improvement content with independent value (retain it)? Per this session's own brief, this is exactly the kind of ambiguity to resolve deliberately rather than guess silently.

**Decision.** Retain the row; anonymise only `submitted_by_user_id` (`ProductContribution.anonymizeSubmitter`, called from `ContributionService.anonymizeSubmitter` during account erasure). `V7__data_rights.sql` drops the column's `NOT NULL` constraint for exactly this case. Reasoning: (a) `product_contribution` has no relationship into `catalog.domain` at all and every row is `PENDING` today (no reviewer console exists yet, per D-026's own context) — the content (product name, nutrients, ingredients) is a claim about a product, not a fact about the submitter; (b) this mirrors the repository's own established pattern for exactly this shape of problem — `audit_event.actor_user_id` and, per D-028, `user_account` itself are both pseudonymised rather than deleted so that a surviving record's evidentiary value is not destroyed by someone else's data-rights request; (c) deleting the row outright would be indistinguishable from silently discarding a real (if unreviewed) product-data submission, which conflicts with `NFR-12`/PX-10's "missing beats wrong, don't discard real signal" posture applied one level up.

**Alternatives rejected.** (a) Delete every `PENDING` contribution on account erasure — rejected: nothing about "this is unreviewed" makes the underlying product-data claim personal data belonging to the submitter, and discarding it destroys information a future ops-review console might still use. (b) Leave `submitted_by_user_id` pointing at the now-erased (anonymised) `user_account` row unchanged — rejected: the id would still resolve to a real (if now-anonymous) account row, which is a weaker privacy posture than explicitly severing the link, for no benefit once the account row itself carries no identifying information anyway.

**Reversal condition.** If a future ops-review console promotes a contribution into `catalog` with per-field provenance (D-015), that promoted product's `catalog.ProductSource` provenance is a separate, already-designed mechanism — this decision only ever governed the pre-promotion `product_contribution` row, and does not need to change when that console is built.

---

## D-030 — D-026 resolved: a terms-of-contribution acknowledgment, not a consent-ledger grant

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (feature-completion sprint)

**Context.** D-026 left `PRODUCT_IMPROVEMENT` `OPEN`, listing three options rather than choosing: (a) extend `ConsentGate`/`ConsentRecord` to an account-scoped subject in addition to the existing member-scoped one, (b) treat the purpose as an implicit terms-of-contribution acknowledgment rather than a revocable ledger grant, (c) retire the purpose. The *scope* question the decision asked ("is this account-level or member-scoped?") was already answered in D-026's own text — the purpose's seeded description is explicit that contribution "contains no personal data about you," and the action has no household-member id to gate against at all. What remained open was the *mechanism*, and this entry makes that choice.

**Decision.** Option (b). `product_contribution` gains a required `contributor_acknowledged` boolean (`V9__contribution_acknowledgment.sql`); `ContributionService.submit` rejects `false` with `VALIDATION_FAILED`. The frontend checkbox shown at submission renders the real, already-seeded `PRODUCT_IMPROVEMENT` policy text (`GET /consent/policies`, public) — not separately invented copy — so the words a contributor sees are the same words the purpose was seeded with in V1.

**Reasoning.** `ConsentRecord.subjectMemberId` is `NOT NULL` with no account-scoped path anywhere in the schema, and every existing query (`findActive`, `findHistory`, `findAllForSubjects`) is keyed on a member id — properly extending it (option a) means a real migration adding a nullable-subject/CHECK-constraint pair, a new `SubjectType`, new repository queries, and new `ConsentGate`/`ConsentService` branches, to a module the architecture explicitly calls privacy-sensitive, in service of a purpose whose own text says it concerns no personal data. That is disproportionate surgery for what the data protects. Option (c) — retiring the purpose — was rejected because the seeded purpose still does real work: it tells a contributor, honestly, that submitting is a deliberate, reviewable act, and a future *personal-data* contribution path (e.g. attaching a captured label photo to a household's own record) may still want the ledger extension option (a) describes — better to leave that generalisation for whenever it is actually needed than to foreclose it by deleting the purpose now.

**Alternatives rejected.** (a) Extend the consent ledger to an account-scoped subject — rejected as disproportionate for a no-personal-data purpose; the option remains open for a future purpose that genuinely needs it. (c) Retire the `PRODUCT_IMPROVEMENT` purpose — rejected; it still communicates something real to a contributor and costs nothing to keep seeded.

**Reversal condition.** If a future task adds a personal-data-bearing, account-level (not household-member-scoped) processing purpose, that is the point to build option (a) properly — at which point `PRODUCT_IMPROVEMENT` could migrate onto the same mechanism if a deliberate follow-up decision chooses to, but this entry does not require that.

---

## D-031 — Development catalogue import: Open Food Facts, via the API, honestly unverified

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (feature-completion sprint)

**Context.** The repository's own architecture diagram (`system-architecture.md` §2) already names Open Food Facts as the intended catalogue import source ("Open Food Facts (import, contribute)... ODbL: attribute + contribute back"), and the schema already carries `SourceType.OPEN_FOOD_FACTS`/`CaptureMethod.OFF_IMPORT` for exactly this — this was a designed-for, not improvised, integration. `post-mvp-backlog.md` had listed "Open Food Facts import + contribute-back pipeline" as blocked on "ODbL attribution... review (D-016)". This sprint's objective (a demonstrable, real, >=5000-product development catalogue) makes this the moment to build the import half of that pipeline (not the contribute-back half, which is separate, later work — nothing in this session writes back to Open Food Facts).

**Decisions.**
1. **Source and method.** `tools/data-import/fetch-openfoodfacts.mjs` pulls India-market products from the public Open Food Facts search API (`countries_tags=en:india`), with a descriptive User-Agent, field-limited requests, fixed inter-request delay, and retry-with-backoff — never a bulk/unthrottled scrape. `backend/.../catalog/seed/OpenFoodFactsSeedRunner.java` (gated behind the `seed-catalog` Spring profile, never active by default) reads the fetched JSONL snapshot and writes through `catalog.application.ProductIngestionService` — the same write path ops-review contribution approval uses — so every imported product gets a real `Product`/`ProductVersion`/`ProductSource`/`ProductNutrient`/`ProductIngredient`/`ProductAllergen` graph respecting D-014 versioning and D-015 per-field provenance. Idempotent: a barcode already present is skipped, so re-running the importer only ever adds new products.
2. **Attribution travels with the data, not just in a policy document.** Every imported `product_source.license_note` records the ODbL 1.0 + DbCL attribution text and a `source_ref` pointing at the exact Open Food Facts product page. The product UI must also carry a visible Open Food Facts attribution (footer/about) — tracked as a Workstream G frontend follow-up, not deferred indefinitely.
3. **Never marked verified.** Every imported `ProductVersion.status` is `NEEDS_VERIFICATION` (never `VERIFIED`) and `DataQuality` is `RECENT` (nutrients + ingredients both present) or `UNVERIFIED` (either absent) — never `VERIFIED` — because no human at this project has checked any of these rows. This is a deliberate, honest classification, not an oversight: Open Food Facts is a community-maintained open database (`docs/data/product-data-policy.md`'s "permitted public/open databases" source class), not an authoritative regulator or manufacturer feed.
4. **Category mapping is taxonomy, not a health judgment.** `OpenFoodFactsSeedRunner`'s `CATEGORY_KEYWORDS`/`ALLERGEN_TAGS` maps are keyword/tag correspondences between Open Food Facts' vocabulary and this repository's own closed, pre-existing category/allergen code lists (`V1__extensions_and_reference_data.sql`) — no new category or allergen concept is introduced, and a product with no confident match is imported with `category_id = NULL` rather than guessed.
5. **Real-time availability is an accepted external-service risk, not something this session can control.** Open Food Facts experienced a sustained service degradation partway through this session's import run (`HTTP 503`/`401` responses from `world.openfoodfacts.org` persisting across subdomains and endpoints, confirmed not specific to this session's request pattern). The importer treats a persistently failing page as skippable (never aborts the whole run) and is safely re-runnable (`--resume`) to top up the catalogue as the source recovers. The actual imported product count and its provenance breakdown are reported honestly in the session's final report, not padded or estimated.

**Alternatives rejected.** (a) Fabricate plausible-looking product records to hit the 5000 target regardless of source availability — rejected outright; explicitly forbidden by this sprint's brief and by `docs/release/release-process.md`'s no-fake-data gate. (b) Use a non-India-focused open dataset (e.g. USDA FoodData Central) as the primary source — rejected as primary because this product's search/alias/trigram tuning and category set are built around Indian packaged-goods brands and naming (`V4__catalog.sql`'s own brand-alias commentary); evaluated only as a possible supplementary source if Open Food Facts' availability did not recover within this session.

**Reversal condition.** None anticipated for the source choice. If Open Food Facts' ODbL share-alike obligation is ever triggered by a future *redistribution* of this catalogue (not just internal use, which this import is), that contribute-back design is separate, later work and should get its own decision entry.

---

## D-032 — Ops-review console authorization: a single operator role, checked server-side on every call

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (feature-completion sprint)

**Context.** Approving a contribution writes a real product into the catalogue — the exact "no OCR/contribution output enters the catalogue without ops review" gate `system-architecture.md` §6 already commits to. No role/permission concept existed anywhere in `identity` before this session (`grep -i 'role\|admin\|isAdmin\|hasRole'` across `backend/src` returned zero matches other than the new code this session adds), so this had to be built, not extended.

**Decision.** `UserAccount` gains one field, `role` (`OperatorRole`: `MEMBER` | `OPS_REVIEWER`, default `MEMBER`, `V8__ops_review_role.sql`) — not a general permissions/role-hierarchy system, deliberately as small as "the smallest useful review console" this was built for needs. `identity.application.OperatorAccess` is a published interface (`isOperator(accountId)`), mirroring `household.application.HouseholdAccess` (D-023) exactly: other modules depend on this interface, never on `identity.domain`/`infra` directly. `capture.application.ContributionService`'s new ops-review methods (`listQueue`/`getDetail`/`approve`/`reject`) each call `requireOperator` — a real database check against the caller's current role, on every request, exactly like every existing authorization check in this codebase (`AuthenticatedUser` deliberately excludes household id/role from the JWT for the same "a stale token must not assert a revoked permission" reason this mirrors). `capture`'s allowed-dependency list (`system-architecture.md` §3) gains `identity`, alongside `platform`/`catalog`/`ai`, for this one interface — the same shape `household`'s existing dependency on `identity` already has.

**Promotion path.** There is deliberately no self-service or API path to grant `OPS_REVIEWER` — it is a direct database action (`UPDATE user_account SET role = 'OPS_REVIEWER' WHERE email = '...'`), documented in `docs/operations/operations-guide.md`. Building an admin-management UI for a role that, at this stage, only ever needs to be granted to a handful of internal reviewers would be exactly the "giant admin platform" this sprint's brief says not to build.

**Frontend affordance, not the authorization boundary.** `GET /api/v1/ops/access` lets the frontend decide whether to show the ops nav entry — it is a UI convenience the caller learns about their own account, not a security control; every ops-review endpoint re-checks `OperatorAccess` itself regardless of what a client believes.

**Alternatives rejected.** (a) A client-side-only check (e.g. hide the nav link based on a locally-cached flag) with no server enforcement — rejected outright; explicitly forbidden by this sprint's brief ("do not invent insecure client-side admin checks"). (b) A full role/permission table (many roles, many resource-scoped permissions) — rejected as disproportionate; nothing in this product yet needs more than one binary distinction.

**Reversal condition.** If a second distinct operator capability emerges (e.g. a nutrition-rule reviewer, distinct from a catalogue-data reviewer), `OperatorRole` should grow additional values or a real permission model at that point — not before.

---

## D-033 — Recommendation module MVP: rank on real allergen-safety and verdict deltas only, never an invented nutrient-direction judgment

**Date:** 2026-09-06 · **Status:** ACCEPTED · **Owner:** Orchestrator (feature-completion sprint)

**Context.** `implementation-plan.md`'s own "P1 — Recommendation module, proposed starting shape" section is explicit that it documents "a proposed starting point for product sign-off, not a decision," ordering household compatibility → allergen conflicts → product health assessment → nutrient/component differences → price/availability (deferred). This sprint's brief is equally explicit: do not silently turn that proposal into a binding ranking formula, and do not invent the direction of a nutrient comparison ("lower sugar is better") without a reviewed source — that is exactly the fabrication D-024/D-025 already refused to do for scoring, one level up.

**Decision.** The `recommendation` module (`AlternativesServiceImpl`) ranks and gates candidates using only two dimensions, both already real and already active elsewhere in this codebase — nothing new is computed, only recombined:

1. **Allergen safety** (`SAFER_ALLERGENS` / `NEW_ALLERGEN_RISK`) — a factual set-membership comparison of `PersonalFitView.allergenMatches` between the current product and each candidate, per member the caller may see. This is real today: D-024 point 2 made allergen matching an active `PersonalFit` dimension from day one.
2. **Verdict severity** (`BETTER_VERDICT` / `WORSE_VERDICT`) — compares `PersonalFitView.verdict` severity (`EVERYDAY` < `OCCASIONAL` < `LIMIT` < `DOESNT_FIT`) when both products' fit is `ASSESSED` with a real (non-`INSUFFICIENT_DATA`) verdict. Structurally real and ready, but **honestly inert today**: D-024 ships zero active scoring rules, so every verdict is currently `INSUFFICIENT_DATA` and this comparison can never fire yet. It starts producing real signal the moment a reviewed rule set activates (D-025) — no code change required.

**What this explicitly does not do.** No nutrient value (sugar, sodium, saturated fat, protein, fibre, ...) is compared directly to produce a `betterOn`/`worseOn` entry. Doing so would require a "which direction is an improvement, for whom, in what category" judgment that no document in this repository has supplied with a citable source — precisely the general-knowledge threshold-guessing D-024's reversal condition and D-025's evidence pipeline exist to prevent. A candidate that offers neither a real allergen improvement nor a real verdict improvement is not returned at all (an honest empty/shorter list, per D-013's "no better option in our data yet" being a first-class result), rather than padded with a same-category product carrying no demonstrable difference.

**A candidate is never shown if it is worse.** Any same-category product that would introduce a *new* allergen conflict for someone the caller may see is excluded from the result entirely — not shown under `worseOn`. D-013 requires every *shown* alternative to publish trade-offs, not that every possible candidate be surfaced regardless of safety.

**Alternatives rejected.** (a) Implement the implementation-plan's full proposed order including a nutrient/component-difference dimension now — rejected: no reviewed source exists for "which direction is better" per nutrient/category, and guessing one from general nutrition knowledge is the exact failure mode D-024 already refused. (b) Rank by raw `ProductHealthScore` (0–100) once it exists — deferred for the same reason; today it is always `NOT_YET_SCORED` (D-024), so it would add nothing. (c) Show every same-category product regardless of differentiation, relying on the UI to convey "no real difference" — rejected as noisier and less honest than simply not listing it.

**Reversal condition.** The moment a reviewed `rule_set` activates (D-025), dimension 2 (`BETTER_VERDICT`/`WORSE_VERDICT`) starts producing real results with no code change. A future task may add a nutrient/component-difference dimension once it can cite the same kind of reviewed evidence D-025 requires for scoring rules — that is a new decision, not an automatic extension of this one.

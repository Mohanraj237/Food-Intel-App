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

---

## D-034 — Product scope expands to an individual + family wellness platform

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Product (requested), Orchestrator (recorded)

**Context.** Every decision from D-001 to D-033 was taken for a deliberately narrow product: a packaged-food decision tool. Several of them (notably D-005 and D-017) are phrased as prohibitions, and were correct for that scope. The product direction has now intentionally changed: the target is a consumer health and wellness platform with two modes — an individual managing their own health, and a household understanding and supporting its members — with the packaged-food decision capability retained as a differentiating part of the whole rather than the whole.

**Decision.** The scope change is accepted. The product now covers personal and household tracking of nutrition, activity, sleep, hydration, body metrics, goals and habits; health records, medications and appointments; and an insights layer over all of it. The existing catalogue, scoring, consent and household modules are extended, not replaced.

**What this decision explicitly does _not_ do.** It does not relax a single safety boundary. The following remain in force and are restated here so that a future reader cannot mistake a scope expansion for a safety relaxation:

- No diagnosis, treatment, prescription or medical triage (unchanged).
- No weight, body-fat or body-composition target for a minor — a child-safety commitment independent of law (unchanged; see D-036).
- No advertising, sponsored ranking, paid placement in health recommendations, or sale of consumption data (D-012, unchanged and still ArchUnit-enforced).
- No engagement mechanics or notifications directed at a child (CH-004/CH-005, unchanged; see D-036).
- AI never originates a number, threshold, verdict or authorization decision (D-011, unchanged).
- No health threshold or interpretation without a citable public standard (D-009/D-024/D-025, unchanged and now extended to body and clinical metrics — see D-037).

**Consequences.** `docs/product/product-policy.md` and `docs/engineering/implementation-plan.md` are superseded in scope by `docs/architecture/wellness-platform-architecture.md`. The "Do not build until validated" list in `docs/product/post-mvp-backlog.md` is superseded for nutrition logging, activity, sleep and hydration by this entry; its "Permanently excluded" section is **not** superseded and remains a boundary.

---

## D-035 — Individual Mode is a household of one, not a second person model

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** Individual Mode needs a person to track their own health without thinking about a household. The tempting implementation is a second, parallel "personal profile" attached directly to `user_account`, separate from `household_member`/`person_profile`.

**Decision.** Rejected. The health subject remains `household_member`, and Individual Mode is a household of one, created transparently on first use. Every tracking table therefore references exactly one subject type for the life of the product.

**Rationale.** Two person models would mean every downstream feature — observations, goals, habits, records, insights, permissions — either picks one or supports both. Supporting both doubles the permission surface on the most sensitive data in the product; picking one guarantees a migration later. The household-of-one framing is also already the product's own language ("A household of one works exactly the same way"), and it makes "I started alone, then added my family" a data-free transition rather than a migration.

**Consequences.** `household` gains an `is_individual` marker so the UI can suppress household framing for a solo user without a second data path. A solo user who later invites someone loses nothing. The cost accepted: a `household` row exists for every user, including those who never use Family Mode.

---

## D-036 — Age band is the safety gate for every metric, enforced in the domain

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** D-017 removed weight and body-composition goals for *everyone*, because in the narrow product there was no adult use case that justified the child-safety risk. The new scope has one: an adult managing their own weight is a core, legitimate, expected capability. The child-safety concern is unchanged.

**Decision.** D-017's blanket prohibition is **superseded for adults only**. Weight and body measurements become recordable, and weight management becomes an expressible goal, **for adult subjects only**. For a subject whose current `person_profile.age_band` is a child band (`C1_3` … `C13_17`):

- No weight, BMI, body-fat, body-composition or waist metric may be recorded, and no such goal may be created. The attempt is rejected in the domain with `METRIC_NOT_PERMITTED_FOR_AGE`, not merely hidden in the UI.
- Growth tracking (height/weight-for-age percentiles) is **not** a workaround and is out of scope until a paediatric standard and clinical review exist (see D-037's entry condition).
- No streak, badge, leaderboard, challenge ranking or push notification may target a child subject.

Every metric code carries an explicit `permittedFor` age-class set. A new metric cannot be added without stating it, because the enum constructor requires it.

**Rationale.** The boundary that matters is the *subject's* age, not the *actor's*. A guardian logging their own weight is fine; the same guardian logging a child's weight is the thing we refuse. Encoding this on the metric definition rather than in a service branch means the check cannot be forgotten by the next feature that writes an observation.

**Consequences.** `AC-3.7` is superseded in part: weight, body-composition and calorie-target capability now exists for adults. The clause forbidding these for a minor, and forbidding streak/badge/leaderboard/notification-to-child capability, is retained verbatim and is now enforced by `MetricCode.permittedFor` plus an ArchUnit-style test asserting no child band appears in a body-composition metric's permitted set.

---

## D-037 — Metrics are recorded freely; interpreting them requires a citable standard

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** The new scope invites blood pressure, blood glucose, SpO2 and heart rate. Each is a number a person can read off a home device, and each is one step away from "is this normal?" — which is diagnosis.

**Decision.** Capture and interpretation are separated, and they have different bars.

- **Capture** of a clinical metric is permitted for an adult subject: it is the person's own reading, and refusing to store it just sends them to a worse tool.
- **Interpretation** — any category ("elevated", "high"), any colour coding implying a category, any comparison to a reference range, any trend statement that implies clinical meaning — requires an active, citable, reviewed standard in the same way a scoring rule does (D-009/D-024/D-025). Until one exists the product shows the recorded value, its own history, and nothing else.
- No metric, in any combination, produces a suggestion to start, stop or change a medication. This is absolute.

**Rationale.** This is D-024 applied to a new data class: the product already ships an empty scoring ruleset and says `INSUFFICIENT_DATA` honestly rather than inventing thresholds. Clinical metrics deserve the same treatment, more strongly. Storing a number is a record; naming it "high" is a clinical claim.

**Entry condition for interpretation.** A reviewed evidence pack under `docs/data/` per D-025, plus clinical sign-off ⚖️, per metric family. Not per metric — a BP standard covers systolic and diastolic together.

**Consequences.** `health_observation` stores values and units with no derived category column. A `BMI` value is computed and stored as an observation because it is arithmetic on the subject's own data; a BMI *category* is not produced.

---

## D-038 — Health data visibility is per member, per category, and default-closed

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** D-002 establishes that adults are their own data principals. That settles consent. It does not settle what a household owner can *see*, which the new scope raises for the first time at scale: a household with body metrics, clinical readings, medications and lab reports in it.

**Decision.** Visibility is an explicit grant per (subject, category, audience), and the default is that no one but the subject can see anything. Concretely:

- An adult subject's observations, records and medications are visible to that adult alone until they share a category.
- A guardian has access to a child subject's permitted categories by virtue of the guardian link, because someone must — but that access is recorded, and the categories a child can have at all are limited by D-036.
- Household role grants nothing on its own. `OWNER` is not a health-data role.
- Every read of another person's clinical, body, medication or record data writes an `audit_event`. Aggregates that could identify a single member's value (a household average over two people, one of whom is the reader) are computed only from categories the reader may already see.

**Rationale.** The failure mode to design against is not a stranger, it is a family member — the person most likely to have both motive and access. Default-closed with explicit sharing is the only model where "my family can support me" does not silently mean "my family can read my glucose readings".

**Consequences.** A `metric_visibility` grant table exists from Phase 1, before any tracker writes data, so no feature ever ships with an implicit all-household default that later has to be taken away from users.

---

## D-039 — Meal logging is in scope; D-005 is superseded

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** D-005 ("no meal logging in MVP; outcome capture instead") is superseded. Nutrition logging — meals, portions, and a family meal logged once and apportioned per member — is core to the new scope. Outcome capture (bought this / an alternative / neither) is retained: it answers a different question (did our result change a decision?) and is the product's own feedback signal.

**Consequences.** D-027's note that outcome capture makes no network call and must not claim persistence stands until the Phase 2 nutrition work gives it somewhere to persist to.

---

## D-040 — A composite wellness score is deferred, not designed

**Date:** 2026-09-12 · **Status:** DEFERRED · **Owner:** Orchestrator

**Context.** The brief proposes a "health/wellness score where scientifically justified", and a family wellness score.

**Decision.** Not built in this phase, and not designed speculatively. A single number over sleep, steps, hydration and nutrition has no citable standard, and under D-009 that is disqualifying. Worse, a household-level score ranks family members against each other by construction, which is the comparison D-036 forbids for children and which the brief itself warns against.

**What ships instead.** Per-dimension progress against goals the user set themselves, and readiness/coverage indicators that say what the product does and does not know. If a composite score is wanted later it needs an evidence pack per D-025 and an explicit decision reversing this entry.

---

---

## D-041 — A family meal writes to another adult's log only when that adult confirms it

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Conflict.** The Phase 2 brief requires that "one family meal should be loggable once and apportioned to individual members", with each member's nutrition persisted to their own record. **D-002** says adults are their own data principals with no proxy, ever. Deriving adult B's nutrition observations from adult A logging dinner is exactly a proxy write into B's health record. The first implementation called `replaceDerived` for every portion, which correctly refused the other adult — and so rolled back the entire meal for any household with two adults, the most common family shape.

**Decision.** A meal and its portions are household facts; the nutrition derived from a portion is the member's health record. So:

1. Logging a meal derives observations immediately only for members the logger may already write for — **themselves, and children they guard**. This reuses `WellnessAccess.canWriteFor`, the same relationship check every other write uses; there is no second rule.
2. Another adult's portion is stored ("Priya had about half") but is **not** written to her log. The meal response marks it `inMemberLog: false`, and the UI says so plainly.
3. That adult may **confirm** their own portion (`POST /api/v1/nutrition/meals/{id}/confirm`), which derives it with themselves as the caller.
4. Only the member who logged a meal may edit or delete it. Editing or deleting **withdraws every observation derived from that meal**, including confirmed shares, because a derived share was only ever "my portion of meal X" — when X changes, the statement's basis is gone. Other adults' portions return to awaiting confirmation. Nothing an adult recorded independently is touched.

**Rationale.** This is the only arrangement that makes the brief's family meal work without weakening D-002. The alternative — letting household membership authorize writing another adult's health data — is precisely the "a family relationship implies access" failure D-038 was written to prevent.

**Consequences.** `meal_portion.derived_at` records whether a share is in that member's log; it states *that* a member confirmed, never what their values are. A two-adult household needs one extra tap per meal from the second adult. Accepted as the cost of the boundary.

---

## D-042 — The expanded individual + family brief: a full review of every prior decision

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Product (requested), Orchestrator (recorded)

**Context.** D-034 accepted the wellness pivot and D-035–D-041 made the first design calls. A second, broader brief arrived the same day ("HealthifyMe for the individual + family"). It adds scope D-034 never named: caregivers and elderly members, age-specific experiences, notifications, gamification and family challenges, recipes/meal planning/grocery lists, an AI intelligence layer with natural-language questions, cross-domain search, dashboards, integrations and premium capabilities. The brief is explicit that old decisions must not be bypassed silently. This entry is that review. The design itself is in `docs/architecture/wellness-platform-architecture.md` (v2), the scope authority for the pivot.

**Decisions that remain valid, unchanged.** D-001, D-002, D-003, D-004, D-006, D-007 (reversal condition unchanged), D-008, D-009, D-010, D-011, D-012, D-013, D-014, D-015, D-016, D-018, D-019, D-020, D-021, D-022, D-023, D-024, D-025, D-028, D-029, D-030, D-031, D-032, D-033, D-034, D-036, D-037, D-039, D-040, D-041. Two of them now carry more weight than when they were written:
- **D-002** (no proxy consent for an adult) governs every caregiver and elderly design. See D-045.
- **D-011** (AI never originates a number) governs the whole intelligence layer. See D-052.

**Decisions changed or extended by this review.**

| Decision | Change | By |
|---|---|---|
| D-035 (household of one) | Extended. The two modes are lenses, not accounts. A household of one is provisioned explicitly, and `is_individual` is maintained in code; before this, no code mapped or set it. | D-043 |
| D-038 (default-closed visibility) | Principle kept, mechanism superseded. One access policy with scopes and VIEW/MANAGE levels covers every health module, not only observations. | D-044 |
| D-005, D-017 | Already superseded (D-039, D-036). Nothing further. | — |
| D-027 | Its outcome-capture caveat is obsolete: the `activity` module persists outcomes. Historical note only. | — |

**Documentation conflicts resolved here, rather than left to be rediscovered.**

1. **AC-3.7 / post-mvp-backlog streak row.** The ban on streaks, badges, leaderboards and notifications is absolute for a child and does not extend to adults. Adult engagement mechanics are governed by D-051.
2. **AC-3.8 vs CH-010 / system-architecture §7.** The stricter AC-3.8 wins: no child data is sent to any external model provider (D-052).
3. **CH-005 ("no behavioural surveillance of children").** Its guarantee rested on a schema property of `activity_event` that `health_observation` does not share. It is re-stated as an enforced rule: child tracking is guardian-entered only, consent-gated, and never passive or automatic (D-048).
4. **CH-006 (no date of birth) vs vaccination records.** Records store what was administered and when. No date of birth is collected, and no schedule is computed (D-049).
5. **`privacy-and-safety.md` "not a medication management system".** Superseded for reminders and taken/missed logs. "Never recommend a medication change" is retained and absolute (D-037, D-049).
6. **`system-architecture.md` "no notification infrastructure".** Superseded by D-050.

**Features that can be built now** (no further decision needed; each still meets the definition of done):
- Individual and Family modes (D-043).
- The unified access policy, sharing and caregiver delegation (D-044, D-045).
- Age-adaptive experiences (D-046).
- Profile completion, including self-reported activity level.
- Nutrition, activity, sleep, hydration, body and wellbeing tracking for adults.
- Guardian-entered child tracking of permitted categories (D-048).
- Goals, within the D-047 guardrail, and habits.
- The individual dashboard and a permission-aware family dashboard.
- Coverage-aware insights over the user's own history.
- Family meals, portions and confirmation (D-041).
- Recipes and a household "my foods" library.
- Meal planning and grocery lists: no commerce, no retailer dependency (D-012, D-016).
- Permission-filtered search.
- The in-app notification framework (D-050).
- Appointments without documents.
- The access log ("who viewed my data").

**Features that still need a safety or privacy decision, or an external input, before they can be built or launched.**

| Feature | What it needs | Status |
|---|---|---|
| Health-record documents: lab reports, prescriptions, scans | Privacy counsel on sensitive-category storage; column-level encryption (NFR-06) | Buildable behind a release gate (D-049) ⚖️ |
| Medications with reminders | Clinical/privacy review of adherence data | Buildable behind a release gate (D-049) ⚖️ |
| Clinical interpretation ("is this normal?") | Reviewed standard plus clinical sign-off per metric family (D-037) | ⚖️ BLOCKED |
| Restrictive-goal safety floors (minimum energy, rate of loss) | Reviewed evidence pack (D-025) | ⚖️ BLOCKED; interim guardrail in D-047 |
| An adult without an account managed by a caregiver (DPDP s.9 lawful guardian, power of attorney) | Privacy counsel | ⚖️ BLOCKED (D-045) |
| DPDP-verifiable parental consent | Privacy counsel (D-001, unchanged) | ⚖️ PROVISIONAL |
| AI summaries and natural-language answers | AI provider credential; privacy review for health data sent to a model | MOCKED until then (D-052) |
| Push notifications | VAPID keys and a service-worker review; email and SMS need providers (D-008) | In-app ships first (D-050) |
| Generic Indian food-composition data (e.g. IFCT 2017) | Licensing review | Until then home-cooked food is custom, user-entered nutrition, shown honestly |
| Wearables: Health Connect, HealthKit, Google Fit | A native shell (D-006 reversal condition) plus OAuth credentials | BLOCKED |
| Composite wellness score | Evidence pack; reversal of D-040 | DEFERRED (D-040) |
| Paediatric growth tracking | Paediatric standard plus clinical review (D-036) | Out of scope |

**Features that remain excluded, permanently or until a new decision reverses them explicitly:**
- Diagnosis, triage and treatment recommendations.
- Starting, stopping or changing a medication, dose calculation, and drug-interaction advice.
- Any weight, BMI, body-fat, body-composition or calorie target for a minor.
- Any engagement mechanic or notification addressed to a child.
- Ranking or leaderboards between family members, for anyone.
- Weight-loss competitions.
- Very-low-calorie or rapid-loss plans.
- Numeric targets for the D-017 excluded populations: pregnancy, CKD, oncology, paediatric therapeutic diets.
- Advertising, sponsored ranking and data sale (D-012).
- Scraping (D-016).
- AI-originated numbers, thresholds or verdicts (D-011).
- Safety-relevant information behind a paywall. Premium may never gate allergen flags, medication reminders, privacy controls or data export.

**Defects found during the review, fixed as part of Phase 1** (listed so they are not mistaken for design):
1. Meal reads leaked another adult's solo meals and per-portion nutrition to any household member.
2. No consent purpose covered wellness tracking, and child tracking ran against the only child purpose's own text ("no consumption tracking").
3. A CHILD member could be given an adult age band, which opened the adult-only metric gate.
4. Account and child erasure left observations, goals, habits and meal portions behind, and export omitted them.
5. `is_individual` was never set.
6. Insight day counts used UTC boundaries (05:30 IST).
7. A client could label a manual observation `DERIVED`.
8. `confirmPortion` was not audited.
9. The ArchUnit boundaries did not cover the five new modules.

---

## D-043 — Individual and Family are two lenses on one account, not two products

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator · **Extends:** D-035

**Decision.**
1. **"Me" is the caller's own member, in any household.** Anyone can use it, including a person in a household of five.
2. **"Family" is the household overview**, filtered by the access policy (D-044).
3. **A new user chooses at onboarding: "Just me" or "Me and my family".**
   - "Just me" provisions a household of one with `is_individual = true` (`POST /api/v1/me/individual`).
   - "Me and my family" creates an ordinary household.
   - Neither is a separate data path.
4. **`is_individual` flips to false the moment a second member joins**, whether an invitation is accepted or a child is added. Nothing migrates, and nothing the person logged moves.
5. **A solo user is never nagged to add family (AC-2.7).** The Family lens offers the option once, quietly.

**Rationale.** A mode stored on the account forces "which mode is this record in?" onto every table. A lens asks only "whose data am I looking at, and may I?", and D-044 already answers that.

---

## D-044 — One health access policy for every health module: scopes and levels

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator · **Supersedes:** D-038's mechanism (its principle stands)

**Context.** D-038 introduced default-closed, per-category visibility for observations only. The rule "self, or guardian of a child" was then copy-pasted into `WellnessService`, `GoalService`, `HabitService` and `MealService`. Goals and habits ignored sharing grants entirely, and meals ignored D-038 altogether (D-042, defect 1). The brief adds caregivers who must *act*, not only view, and adds non-metric domains (medications, appointments, records). A fifth copy of the rule would be the one that drifts.

**Decision.** A new `access` module owns the only implementation, `HealthAccessPolicy`, and the grant table (`health_access_grant`, migrated in place from `metric_visibility` in V14, history preserved).

- **Scopes.** The seven metric categories (BODY, ACTIVITY, SLEEP, HYDRATION, NUTRITION, CLINICAL, WELLBEING) plus HABITS, MEDICATIONS, APPOINTMENTS, RECORDS and FOOD_PROFILE. FOOD_PROFILE covers allergens, dietary preference and food goals, which is what lets a caregiver "evaluate this product for Grandma". It never includes self-declared health conditions, which stay self- and guardian-only.
- **Levels.** `VIEW`, and `MANAGE` (record, log or act on the subject's behalf in that scope; implies VIEW).
- **Evaluation, in order.** The first answer is final.
  1. Unknown or departed subject: not found.
  2. The caller *is* the subject: SELF, MANAGE on every scope.
  3. The subject is a CHILD and the caller holds a live guardian link: GUARDIAN, MANAGE on every scope. The metric age gate still applies (D-036).
  4. Otherwise, the live grants from the subject to the caller's membership in the same household. CAREGIVER if any grant is MANAGE, SHARED if all are VIEW.
  5. Otherwise NONE.
- **Household role (`OWNER`) is never consulted.**
- **Only the subject grants.** The audience must be an active ADULT member of the same household. Grants cannot be re-delegated. A child's data is never grantable: it is reachable only through a guardian link (D-038, unchanged).
- **Goals inherit their metric's scope.** A steps goal is ACTIVITY data.
- **Habits use HABITS.** A habit name can be sensitive, so habits get their own scope.
- **Meals.** A meal's existence and who was served are facts among the people served. A portion's nutrition is that member's NUTRITION data: it is returned only to that member, their guardian, or a NUTRITION viewer, and is otherwise withheld. A meal the caller neither ate nor logged, and whose served members' nutrition they cannot see, is not returned.
- **Every cross-member read and write is audited with the subject's member id** (new `audit_event.subject_member_id`). The subject, or a child's guardian, can read that trail as an access log.

**Rationale.** The failure to design against is a family member, not a stranger (D-038). One policy, tested as a matrix of relationship × scope × level × age, is the only arrangement where a new module cannot quietly ship a sixth interpretation.

**Consequences.**
- `/api/v1/wellness/members/{id}/shares` moves to `/api/v1/members/{id}/access-grants`.
- `/wellness/members/{id}/visibility` keeps its shape.
- Other modules reach the policy only through its published interface. `ModuleBoundaryTest` gains rules for this.

---

## D-045 — Caregiver access is delegated by the adult, never assumed; no account-less adults yet

**Date:** 2026-09-12 · **Status:** ACCEPTED (delegation) / ⚖️ BLOCKED (account-less adult) · **Owner:** Orchestrator

**Context.** The brief asks for caregivers and elderly members. D-002 forbids proxy consent for an adult, and its own context names "an elderly parent". The cases that matter are an adult child supporting a parent aged 60 or over, and a spouse supporting a spouse.

**Decision.**
1. **A caregiver is any household adult to whom the subject has granted MANAGE on one or more scopes (D-044).** This is not proxy consent. It is the data principal's own, specific, revocable authorisation.
   - Caregiver actions are attributed (`recorded_by_user_id`), audited against the subject, and visible in the subject's access log.
   - Revocation takes effect on the next request.
   - A caregiver cannot grant onward, change the subject's sharing, export the subject's data, or delete the subject's account.
2. **There is no pathway for an adult without an account** (a caregiver-managed profile, a lawful guardian of a person with a disability under DPDP s.9, or power of attorney). It needs privacy counsel and is **BLOCKED**. Until then, an elder joins by accepting an invitation and granting access themselves, which may be done on the caregiver's phone with the elder signing in.
3. **The elderly experience is not a restriction tier (D-046).** Age 60+ never removes a capability. It changes what is shown first.

---

## D-046 — Experience tiers: CHILD, ADULT, OLDER_ADULT, UNKNOWN — derived, server-authoritative, fail-closed

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Decision.** The experience a member gets is derived on the server from their member type and current profile age band. No date of birth is used (CH-006).

- **CHILD.** The member type is CHILD, or the profile band is a child band. Either one is enough.
- **OLDER_ADULT.** An adult member with band `A60_PLUS`.
- **ADULT.** Any other adult band.
- **UNKNOWN.** An adult with no profile yet. Tracking waits for a profile (`PROFILE_REQUIRED_FOR_METRIC`, unchanged).

The server returns the tier with explicit capability flags (`GET /api/v1/me`, `GET /api/v1/members/{id}/experience`). The UI renders them and never infers them.

**Tier differences.**
- **Child ("not a smaller adult").** Nutrition, activity, sleep, hydration, wellbeing and habits, all guardian-entered.
  - No BODY or CLINICAL metrics, and no body goals.
  - No streaks, badges, challenges or notifications to the child.
  - Encouraging, non-evaluative copy.
- **Older adult.** Every adult capability.
  - The default home puts medications, appointments, reminders and caregiver support first.
  - It offers a larger-type option.
  - Nothing is removed on account of age.

**Also enforced.** A CHILD member's profile must carry a child band, and an adult account's profile an adult band. `ProfileService` now refuses the mismatch (D-042, defect 3). The age gate also treats member type CHILD as a child regardless of band, as defence in depth.

---

## D-047 — Restriction-framed goals need a reviewed safety floor before they exist

**Date:** 2026-09-12 · **Status:** ACCEPTED (interim guardrail) · **Owner:** Orchestrator

**Context.** The brief: "Avoid unsafe or extreme goals." The goals module accepted any target, including an `ENERGY_KCAL` `AT_MOST` of 300 kcal/day. Refusing it needs a number, and under D-025 that number cannot come from Claude's general knowledge.

**Decision for a child subject**, which is permanent and not interim: a goal may only target an ACTIVITY, SLEEP or HYDRATION metric, and only with `AT_LEAST` or `MAINTAIN`.
- No NUTRITION goal (no calorie, sugar, sodium or macro target), no BODY or CLINICAL goal, no WELLBEING target (a mood score is not something to aim at), and no `AT_MOST` on anything.
- A breach is refused in the domain with `GOAL_NOT_PERMITTED_FOR_CHILD` (422) and audited with the age-refusal action.
- Children's routines belong in habits ("fruit at breakfast"), which carry no numeric target.
- This implements D-036's retained minor clause (no calorie-target capability for a minor). Raised by the Phase 2 frontend session, which found that the metric age gate alone let a guardian create a calorie cap for a child.

**Decision for adults.** Until a reviewed evidence pack defines the floors:
1. `ENERGY_KCAL` `AT_MOST` goals (calorie caps) are refused in the domain (`GOAL_REQUIRES_REVIEWED_STANDARD`).
2. BODY goals stay adult-only (D-036). They carry no deadline, rate, deficit or projected date, and their screens carry a professional-guidance note.
3. The product never computes a calorie target or deficit for anyone.
4. D-017's excluded populations and the no-rapid-weight-loss framing remain in force.

**Reversal condition.** A reviewed floor per D-025. The guardrail then becomes a validated threshold rather than a refusal.

---

## D-048 — Wellness tracking is its own consent purpose, for adults and (guardian-granted) for children

**Date:** 2026-09-12 · **Status:** ACCEPTED (adult) / PROVISIONAL (child mechanism, as D-001) · **Owner:** Orchestrator

**Context.** No consent purpose covered wellness tracking (D-042, defect 2). The only child purpose, `CHILD_FOOD_SUITABILITY`, says "no consumption tracking, no behavioural profile", yet children's meals, activity and sleep were being recorded without any consent check. That runs against D-001, CH-002/CH-003 and D-018 (consent checked at processing time).

**Decision.**
1. **New purposes (V14).**
   - `HEALTH_TRACKING`: adult, self-granted, not essential.
   - `CHILD_WELLNESS_TRACKING`: guardian-granted, `requires_verifiable`, PROVISIONAL like D-001.
2. **Consent gates collection and disclosure.**
   - Every write requires the subject's purpose to be ACTIVE at processing time: observations, meal derivation, goals, habits and habit completions.
   - So does every cross-member read.
3. **A subject's own reads of already-collected data remain available after withdrawal**, so they can see, export and erase it.
4. **Child tracking is guardian-entered only.** No passive or automatic source (wearable import, location) may write child data. This is CH-005 restated as an enforced rule (D-042).
5. **A meal portion for a member whose tracking consent is not active** is stored as a household fact and not derived. It has the same shape as an unconfirmed adult portion (D-041).

---

## D-049 — Health records, medications and appointments are built behind consent and a release gate

**Date:** 2026-09-12 · **Status:** PROVISIONAL (becomes ACCEPTED when Phase 4 starts) · **Owner:** Orchestrator

**Decision.** The D-001 precedent applies: build the capability properly, gate its processing, and block production launch with real data until counsel signs off ⚖️.

- **Records.**
  - Document metadata, plus the file in a private bucket.
  - Magic-byte type check, size cap, no public URL, short-lived signed URLs.
  - No OCR or AI processing of medical documents until separately reviewed.
- **Medications.**
  - User-entered name, dose text, schedule, reminders, and a taken/missed log.
  - Never dose calculation, interaction checking, or a suggestion to change anything.
- **Appointments.** Who, where, when and notes, with reminders.
- **Vaccinations.** Administered vaccine plus date, with no date of birth and no computed schedule.
- **Consent purposes.** `HEALTH_RECORDS` and `CHILD_HEALTH_RECORDS`.
- **Encryption.** Column-level encryption (NFR-06) of record metadata and notes before production.
- **Access.** Scopes RECORDS, MEDICATIONS and APPOINTMENTS under D-044. Caregiver MANAGE is the elderly-support path.

---

## D-050 — Notifications: in-app first, opt-in, capped, never addressed to a child

**Date:** 2026-09-12 · **Status:** PROVISIONAL (becomes ACCEPTED when Phase 2's notification work starts) · **Owner:** Orchestrator · **Supersedes:** `system-architecture.md` "no notification infrastructure"

**Decision.**
- **Preferences.** Per member, per category: goals, habits, meals, hydration, activity, sleep, medication, appointments, family, insights. Plus quiet hours and a daily cap.
- **Channels.** The first channel is an in-app inbox. Web Push follows; email and SMS need providers (D-008).
- **Defaults.** Engagement categories default to off. Only reminders a person explicitly creates (medication, appointment) default to on.
- **Children.** No notification is ever addressed to a child subject. Reminders *about* a child go to the guardian.
- **Copy.** No fear, shame or guilt. No "you missed" or "you broke". A reminder states the thing and the time.

---

## D-051 — Engagement mechanics: adults only, opt-in, cooperative, never punitive

**Date:** 2026-09-12 · **Status:** PROVISIONAL (becomes ACCEPTED when Phase 6 starts) · **Owner:** Orchestrator

**Decision.**
- **Children.** The child clause of AC-3.7 stands verbatim: no streak, badge, leaderboard, challenge or notification for a child.
- **What adults may opt into:**
  - Consistency milestones ("30 days of water logged"), never "streak lost".
  - Personal bests.
  - Cooperative family challenges: one shared household target, with no per-member ranking.
- **Challenge data.** A challenge total counts only scopes each participant has shared (D-044). With two participants, a total that would reveal the other person's value is shown only if both share that scope.
- **Never.** No weight or body challenges, and children never take part or appear.
- **Leaderboards between family members are permanently excluded for everyone.**

---

## D-052 — Intelligence layer: deterministic facts, AI phrasing only, no child data to any model

**Date:** 2026-09-12 · **Status:** PROVISIONAL (becomes ACCEPTED when Phase 5 starts) · **Owner:** Orchestrator

**Decision.**
- **Summaries.** Weekly and monthly, individual and family. They are assembled deterministically from the insights engine's typed facts.
- **LLM phrasing** is an optional rephrasing step behind D-011's numeric-fidelity check and the banned-vocabulary scan. It falls back to the template on any failure.
- **Natural-language questions** map to a closed set of intents answered by deterministic queries ("how did I do this week?").
- **Refusals.** Medical, diagnostic and medication questions are refused with a professional-referral template.
- **Children.** Child data is never sent to an external model provider (AC-3.8 over CH-010). Children's parts of a family summary are templates only.
- **Recommendations** (meals, recipes, goals, habits) are rules over the user's own data and stated goals, and never sponsored (D-012).
- **Availability.** Status stays MOCKED until a provider credential and a privacy review of health data sent to a model both exist.

---

## D-053 — "Today" is local to the household

**Date:** 2026-09-12 · **Status:** ACCEPTED · **Owner:** Orchestrator

**Context.** The application clock is UTC and insights counted days in UTC, so an Indian user's "today" rolled over at 05:30 IST. A breakfast logged at 05:00 belonged to yesterday (D-042, defect 6).

**Decision.**
- `household.time_zone` (IANA, default `Asia/Kolkata`, V14) defines day boundaries for its members. This covers today summaries, daily goals, habit days and insight day counts.
- Storage stays UTC.
- Time-zone changes are rare and prospective. Past observations keep their instants; only how they are bucketed changes.

---

## D-054 — Family page: a household summary and weekly trends, from what you can already see

**Date:** 2026-09-13 · **Status:** ACCEPTED (product owner, 2026-09-12) · **Owner:** Orchestrator

**Context.** The Family page showed one number per category, for today only. The product owner asked for a family summary as well as per-person views. Spec §6.7 rendered no family totals in W1–W2 and deferred a family insight to W3 under architecture §11. This decision brings that insight forward, bounded by the same aggregate rule.

**Decision.**
- **One read, one request.** `GET /households/{id}/overview` returns each visible member's last 7 household-local days (D-053), their live goals and their habit days, plus a household summary. The screen still makes one overview request with no per-member fan-out (spec §7.2). Each member's week, day and goal progress come from one observation read, so a visit writes the same access-log rows as before.
- **The summary is computed from the caller's own cards, on the server** (`FamilySummaries`). A total therefore never contains a number the caller could not already read on someone's card (D-038's aggregate rule, architecture §11).
  - Included: members with a visible HYDRATION, ACTIVITY, SLEEP, NUTRITION or HABITS scope.
  - Hidden when fewer than two people are included.
- **What it combines.**
  - Water and steps: a family total.
  - Sleep: an average per person who logged it.
  - Meals: a count of people, never kcal, so a child's energy intake is never a family headline (UXD-08).
  - Habits: done of due.
  - Every figure states its coverage ("for 3 of 4 people"), and the summary names whose records it uses.
- **What it never combines.** Health readings, body measurements and mood (D-036, D-037).
- **Still excluded, unchanged.** No per-person figure inside the summary. No "most" or "least". No ordering people by a value. No comparison of a person with the family. Leaderboards stay permanently excluded (D-051). The updates feed is ordered by time logged, never by amount.
- **Card changes** (amending UXD-18).
  - Members who share nothing with the viewer appear as one quiet list in server order, not as full identity cards. A child you don't guard keeps the guardian line.
  - The Family date is navigable (`?date=`), where W1 had it read-only.

**Consequences.** Spec §4.4 and §6.7 are updated to match. The per-person chart rules still hold: one hue, gaps for unlogged days, dots only for readings, and no goal line on a reading.

---

## D-055 — Consumer identity and information architecture ("Fresh Kitchen")

**Date:** 2026-09-13 · **Status:** ACCEPTED (product owner brief, 2026-09-13) · **Owner:** Orchestrator

**Context.** The product owner reviewed the rendered application and the e2e screenshots and asked for it to be rethought as a launchable consumer food product rather than an engineering template: a food-consumer visual identity, a coherent customer-facing information architecture, a reusable design system, and better feature UX on every major screen. The v1 identity (teal on white, system font, Unicode glyphs, hairline cards everywhere), the Today-first navigation, the "What's next" hub that showed developer status badges ("Not built yet · C4", "D-049 gated", "MOCKED") on consumer routes, and copy-heavy screens all read as an admin console.

**Decision.**
- **Identity.** `docs/design/design-system.md` v2: warm cream ground, white cards, terracotta brand (`#C2410C`), fresh-green accent for positive moments only, warm charcoal text, self-hosted Plus Jakarta Sans, an inline SVG icon set, pill buttons and chips, soft warm elevation. The verdict ramp, the allergen flag, "nothing is red", "colour never carries meaning alone" and every contrast floor are unchanged. Dark is a warm dark, still explicit opt-in.
- **Information architecture.** Home (food discovery) is the landing screen for everyone, at `/`. Signed-in navigation is Home · Today · Log · Family · You; signed-out is Home · Search · Scan · Sign in. `/food` redirects to `/`. Onboarding finishes on Home.
- **New consumer routes.** `/saved` — a shortlist kept on this device (localStorage, keyed by account, snapshot of card data; every surface says "on this device"; nothing is sent anywhere). `/history` (was `/food/recent`) and `/insights` (was `/next/insights`) are first-class; `/next` redirects to `/insights`. The `/next` hub screen and the `NotBuiltYet` placeholder are deleted.
- **Flag-gated previews** (health shelf, ask, notifications) keep their routes and flags but their gated state is consumer copy ("Coming soon", what it will and will not do) — no decision numbers, workstream codes or "MOCKED" on a route a user can reach.
- **Home** carries the promise ("Is this a good choice for your family?"), search and scan in thumb reach, "Your day so far" (four numbers from the same `/today` read), "Shopping for {household}" (readiness, what the household is watching, allergens to avoid, what's missing behind a disclosure), categories, saved, curated rails, recently viewed, and a trust strip.
- **Product** keeps the fixed information order (D-003) and adds Save / Compare / Share actions, per-person fit rows with avatars, a six-tile "At a glance" per-100 g nutrient block before the full label (which is collapsed), and a provenance block with a data-quality badge.
- **Today** tiles carry an icon and a goal bar; the goals card renders the shared `GoalMeter`; insights link to `/insights`.
- **You** opens with an identity card (avatar, name, household), then Your records (profile, family, household, saved, recently viewed, insights), sharing, appearance, privacy choices (self and the children in the household only — another adult's choices are theirs, D-002, and a child the viewer does not guard says so quietly instead of raising an error), your data, policies, sign out.
- **Session restore** no longer clears the stored refresh token on a transport failure; only a rejection the server actually returned (4xx) does. Found because a burst of requests under load signed the audit session out mid-run.

**Alternatives considered.** A green primary (collides with the EVERYDAY verdict), a blue primary (collides with OCCASIONAL and reads as SaaS), keeping teal (the reason the app read as clinical), Google Fonts by link (a third-party request on a health product, D-018), a server-side saved list (no backend exists; a device-local shortlist is honest and useful now — server sync stays on the backlog).

**Consequences.** Frontend only; no API or schema change. `npm run typecheck`, `npm test` (75, vocabulary gate included) and `npm run build` are green. Verified by rendering every major route in Chromium at 390px and 1440px, signed in as the demo father and signed out, light and dark (`frontend/e2e/screenshots-redesign/` beside the previous set; `E2E_SHOTS` overrides the folder). `NotificationsPanel`/`NotificationPreferencesSheet` are no longer reachable from any route (they were only reachable from the deleted hub) and stay in the tree behind their flag.

---

## D-056 — Redis is an optional shared cache for public catalogue data; supersedes D-007 for caching

**Date:** 2026-09-13 · **Status:** ACCEPTED (product owner direction, 2026-09-13) · **Owner:** Orchestrator

**Context.** D-007 kept caching in-process (Caffeine) until "more than one application instance". The product owner has asked for Redis caching. The Caffeine cache manager set up under D-007 had six cache names and no cached method, so nothing was actually cached.

**Decision.**
- **Supersedes D-007 for caching only.** Rate limiting stays database-backed; D-007 still governs it.
- **One provider at a time,** chosen by `familyfood.cache.type`:
  - `caffeine`: in-process. This is the default, so single-instance behaviour is unchanged.
  - `redis`: shared by every instance.
  - `none`: caching off.

  There is no in-process tier in front of Redis, because an entry evicted on one instance would survive in another's heap.
- **Redis is optional at runtime.**
  - Startup never connects to it.
  - A failed cache read is a miss; a failed write is "not cached".
  - After one failure, the instance skips Redis for `retry-after` (30 s), so an outage costs one request a timeout rather than every request.
  - Lettuce rejects commands while disconnected instead of queueing them.
  - Health reports `DEGRADED` (HTTP 200), never DOWN. Liveness and readiness never include Redis.
- **Only public catalogue data is cached.**
  - `catalog.product`: product detail by id. TTL 30 min, evicted when the product's image changes.
  - `catalog.categories`: TTL 1 h.

  Nothing about a person is cached: no profile, verdict, consent state, health data or search text (D-018). Those are read fresh, and they never go to a second store with its own access control, retention and erasure duties. `CachingBoundaryTest` confines caching annotations and cache declarations to `catalog`, and direct cache access to `platform.cache`.
- **Key convention:** `ffi:<module>.<what>:<version>::<id>`. The version segment hashes the cached type's structure and the applied schema version. A changed DTO or a new migration therefore starts a fresh keyspace instead of serving entries in an old shape, or entries read before a data fix.
- **Storage format:** typed JSON per cache. Entries never name a class to instantiate.
- **Transactions:** cache advice runs outside transaction advice. A hit borrows no database connection, and an eviction runs after its write commits.

**Security.** Cached product detail feeds allergen matching, so write access to Redis amounts to write access to what users are shown about allergens. In any shared network, Redis requires AUTH (`REDIS_PASSWORD`) and TLS (`REDIS_SSL=true`), and it is reachable only from the application's network.

**Consequences.**
- One more optional service: compose profile `redis`, and `CACHE_TYPE` / `REDIS_*` in the environment.
- Two staleness windows remain, each bounded by the TTL:
  - evictions lost during an outage;
  - changes made outside the process, such as a separate seed run or a SQL correction.
- `/actuator/prometheus` is still denied by `SecurityConfig`. Scraping the new cache, Lettuce and virtual-thread metrics needs its own decision: a management port or an authenticated scrape.

**Reversal condition.** A measured hit ratio below ~50%, or Redis incidents costing more than the database load they save. Reverting is a configuration change (`CACHE_TYPE=caffeine`).

---

## D-057 — Virtual threads for blocking I/O; CPU-bound work on bounded pools

**Date:** 2026-09-13 · **Status:** ACCEPTED (product owner direction, 2026-09-13) · **Owner:** Orchestrator

**Context.** `spring.threads.virtual.enabled=true` was already set, with no recorded decision. The product owner asked for virtual threads to be integrated deliberately, and not to be treated as the fix for the JVM crashes seen on the development machine.

**Decision.**
- **Virtual threads:** request handling, `@Scheduled` jobs, JDBC, Lettuce and S3 calls. All of these block on I/O.
- **Never virtual threads:** CPU-bound work. It runs on a bounded pool of platform threads instead. Today that means Argon2id hashing (`PasswordHasher`, max(2, cores/2) threads). Without the pool, a burst of sign-ins would occupy most carrier threads, and every other request would wait behind them.
- **The database pool bounds database concurrency.** `DB_POOL_MAX`, default 10, is sized to what Postgres can serve and never to request concurrency.
- **Pinning is measured, not assumed away:**
  - `jvm.threads.virtual.pinned` (micrometer-java21);
  - JFR in `mvn spring-boot:run`.
- **Not a crash fix.** The relation between virtual threads and the 21.0.7 access violations is unproven; see the operations guide, "JVM runtime".

**Known gaps (flagged, not changed).**
- `AuthenticationService.register` and `login` hash inside their transactions, so each holds a pooled connection for the duration of a hash. Changing authentication's transaction boundaries is security-sensitive and needs its own review.
- `ProductImageService.attachImage` uploads to object storage inside its transaction. Only the seed runner calls it today.

---

## D-058 — Rule set v2 evidence pack: accept the FSSAI INR draft as a citable standard, add WHO SEARO NPM 2017, and record the scoring-content review process

**Date:** 2026-09-14 · **Status:** ACCEPTED, pending a separate content review · **Owner:** Orchestrator (Food Intel App build plan, Increment 0)

**Context.** D-024/D-025 shipped the scoring engine with zero active rules because no document in this repository specified a real, citable numeric nutrient threshold, and D-025 established the mandatory pipeline — `domain source → reviewed specification → migration → automated tests → activate rule_set` — for ever changing that. This decision is the first step of that pipeline for a "rule set v2": it records that the two sources chosen (FSSAI's draft Indian Nutrition Rating and the WHO Nutrient Profile Model for the South-East Asia Region) are the sources the evidence pack transcribes from, and what their own status is, so a future reader does not need to re-derive that context.

**Decision.**
1. **`WHO_SEARO_NPM_2017` is added as a new `reference_standard` row** (publisher WHO SEARO, jurisdiction GLOBAL, evidence level A, document URI `https://www.who.int/publications/i/item/9789290225447`) — this is a new citation, not previously seeded.
2. **The existing `FSSAI_INR` `reference_standard` row is updated**, not replaced: `document_uri` becomes the specific draft notification (`https://comments.fssai.gov.in/Bestviewwl.aspx?NOTIFICATION_ID=4123`, notification F. No. Std./SP-08/T(FoPNL-N-01), 13 September 2022), `published_on` becomes the notification date, and the `scope_note` is rewritten to state plainly that this is a **draft regulation, not a final one** — comment period closed 28 November 2022, and (per contemporaneous reporting, not independently verified against a court record) the Supreme Court of India asked FSSAI for a front-of-pack-labelling timeline on 10 September 2026. The row's original scope note said "thresholds pending final [text cut off in the original]" — the numbers are in fact fully published in the draft's own Schedule III; what is pending is *finalisation of the regulation*, not the numbers, and the corrected scope note says so.
3. **The draft's status is accepted as a citable evidence-level-B source for this project's scoring content**, explicitly *not* elevated to "the regulator's rating" anywhere in the product. Every place the derived score is shown states plainly that it is based on the *draft* method (`ratingScheme: "FSSAI_INR_DRAFT_2022_DERIVED"`), never presented as FSSAI's own official rating (D-003's separation between this project's independent assessment and any regulatory rating is unchanged and, if anything, sharpened by this decision — a draft regulation is one step further from "official" than a finalised one would have been).
4. **This decision does not itself activate any rule.** `docs/data/scoring/rules-v2.md` is the actual proposed content, transcribed from these two sources plus the four already-seeded standards (`WHO_FREE_SUGARS_2015`, `WHO_SODIUM_2012`, `ICMR_NIN_DGI_2024`, `ICMR_NIN_RDA_2020`) and the NOVA classification (Monteiro et al., FAO 2019, cited as a classification, not a threshold). `docs/data/scoring/review.md` records the named reviewer's line-by-line approval; nothing in `rules-v2.md` is active until that approval exists and a subsequent migration (`V17`/`V18`) is written and reviewed on its own merits, per D-025 — this decision does not shortcut that process, it starts it.

**What was deliberately left unresolved, not silently decided:** `rules-v2.md` itself flags nine open interpretive questions (category-to-standard mappings for heterogeneous catalogue buckets, an unresolved `MALT_DRINK` categorisation, a footnote-based sugar-threshold substitution, an acknowledged absence of saturated-fat and trans-fat rules for most categories, two un-sourced display-convention cut-offs, and one unverified secondary citation) — see `rules-v2.md` Part D and `review.md`'s checklist. None of these are resolved by this decision; they are the reviewer's to decide.

**Alternatives rejected.** (a) Wait for FSSAI's regulation to be finalised before building anything — rejected: the draft's numbers are fully published and citable now, D-025's pipeline does not require a *finalised* government standard, only a *citable* one with a named source and date, and the product can (and per D-003 must) always label a draft as a draft. (b) Treat the FSSAI system as fully authoritative and skip the WHO SEARO layer — rejected: FSSAI's INR produces a single product-level score, not a per-nutrient, per-goal verdict, and this project's `PersonalFit`/`HouseholdFit` model needs the latter; WHO SEARO's per-category nutrient thresholds are the closest available fit for that, once reused with the interpretation flagged in `rules-v2.md`.

**Reversal condition.** If FSSAI's regulation is finalised with different numbers than the draft, or is withdrawn, a new evidence source and a new decision supersede this one and `rules-v2.md`'s FSSAI-derived rows specifically — the WHO SEARO-derived rows are unaffected by that outcome, since they cite a separate, independently finalised WHO publication.

---

## D-059 — Scoring engine v2 and catalogue enrichment: points-net algorithm, derived facts, persisted scores, null → value enrichment

**Date:** 2026-09-15 · **Status:** ACCEPTED (engine and schema); rule content still PENDING review (D-058) · **Owner:** Product owner (requested, build plan Increment 1), Orchestrator (recorded)

**Context.** D-058 assembled the evidence for rule set v2. Before any reviewed content can be activated, the engine has to be able to run it: the FSSAI draft INR method is a points table with capping and star bands, not the `{min,max}` weighted mean V5 shipped with; the verdict rules proposed in `rules-v2.md` reference the product's star rating and processing level, which were not rule inputs; and nothing persisted a score, so search could not filter or sort on one. The catalogue also lacked the facts the method needs (processing level, the estimated fruit/veg/nut content, per-100 ml basis for drinks, additive marks).

**Decision.**
1. **Two algorithms, chosen per rule set** (`rule_set.algorithm`, V16). `WEIGHTED_MEAN_V1` is the original aggregation, unchanged. `POINTS_NET_V2` (`PointsNetAlgorithm`) places each per-100 value in a transcribed band table (`score_component.thresholds` of shape `{"type":"BANDS",...}`), sums BASELINE points, subtracts MODIFYING points capped per the standard's own table, and maps the net to a star band from `rule_set.algorithm_config`. Star bands are matched in list order, first match wins; that is how the source's printed overlap at 18 liquid points resolves unless the reviewer orders V17 differently.
2. **Missing data is never assumed on the risk side.** Any undeclared BASELINE factor makes the score `INSUFFICIENT_DATA` with the missing inputs listed in `coverage`. An undeclared MODIFYING factor scores 0 and is reported as `ASSUMED_ZERO`, which is the methodology's flagged interpretation (§2.4) and stays the reviewer's call. Only per-100 values count. Fats and oils (`FAT_OIL`) get `INSUFFICIENT_DATA` with `SCORE_NOT_APPLICABLE`, never a number, because the source exempts them.
3. **The 0–100 number is a display convention.** It rescales net points linearly between the star table's own top and bottom edges and is always shown after the star rating, never above it (D-003). The letter grade keeps V1's 20-point bands.
4. **Derived facts are rule inputs.** `AssessmentOrchestrator` resolves the food type (category map default, overridden to LIQUID when facts are per 100 ml, FAT_OIL never overridden), gets the score, then adds `INR_STARS` (only when scored) and `NOVA_GROUP` (only when classified) to the input. `RuleEngineImpl` treats them like nutrients on `PER_100G`; a declared nutrient of the same code wins; a rule that fires on one reports `DERIVED_FACT_THRESHOLD`. Every assessing path (product page, household fit, alternatives, rescore) goes through the orchestrator and the one shared `AssessmentInputMapper`; the duplicate mapping in `recommendation` is gone.
5. **Scores are persisted as derived product facts.** `ProductScoreService` stores `SCORED` and `INSUFFICIENT_DATA` results in `product_score` with an input hash and serves the row while the hash matches. `NOT_YET_SCORED` is never stored. Catalog publishes `ProductVersionCreated` after ingestion or enrichment; scoring recomputes after commit. `RescoreService` walks the catalogue page by page and records a `rescore_run`; `RescoreOnStartupRunner` starts one on the first boot where the ACTIVE set has components but no scores. This is not a cache of personal data (D-056 unchanged); `product_search_facts` exposes the ACTIVE set's persisted score to search.
6. **Enrichment fills null → value only.** `ProductEnrichmentService` is the one way to add facts to an existing current version. It never overwrites, writes one new `product_source` row per pass for every filled fact, logs a non-material `product_change_log` entry (`OFF_ENRICHMENT`), evicts the cached detail and publishes the event. A pass with nothing new writes nothing. `OpenFoodFactsEnrichmentRunner` (profile `seed-catalog-enrich`) drives it from a snapshot topped up by `tools/data-import/refetch-openfoodfacts.mjs --merge`.
7. **What an Open Food Facts row may contribute.** A NOVA group only when it is 1–4; the snapshot's junk values are dropped, never clamped. A diet flag only from `ingredients_analysis_tags`, always marked estimated. `en:non-vegetarian` wins; `en:non-vegan` only rules out VEGAN, because Open Food Facts sets it for any product with milk. The fruit/veg/nut figure only as an estimate (`FVNL_PCT`, `is_estimated = true`). Nutrients declared per 100 ml keep `PER_100ML`.
8. **Additives are marked, not judged.** `AdditiveDetector` recognises INS/E numbers in label tokens and bracketed lists after a class name; bare quantities never match. The `additive` table (V15) holds only the Codex CXG 36-1989 name and functional class. It carries no safety or health statement and must never be used to imply one.
9. **Schema details.** `product_version.nova_source_id`/`diet_source_id` foreign keys are `DEFERRABLE INITIALLY DEFERRED`, because a version and its source row reference each other. A replaced catalogue photo of the same view becomes `SUPERSEDED`, not `REJECTED`, and is never deleted.

**What does not change.** The ACTIVE rule set is still `v1` with zero rows: the public assessment answers `NOT_YET_SCORED`, every verdict is `INSUFFICIENT_DATA`, and nothing is persisted. V17/V18 remain unwritten until `docs/data/scoring/review.md` carries the product owner's approval line. `scoring` still does not depend on `ai`.

**Known data gap.** The committed 893-row snapshot has no `ingredients_analysis_tags` or `serving_size`, so diet flags and serving sizes stay `UNKNOWN`/absent until the re-fetch runs against Open Food Facts.

**Alternatives rejected.** (a) Encode the INR tables in Java — rejected: the numbers are reviewed content and must live in migrations with a `standard_ref`, not in code (D-025). (b) Treat a missing baseline nutrient as zero so more products score — rejected: an undeclared value is not a declared zero (`ck_nutrient_has_value`). (c) Compute scores on every read — rejected: search needs to filter and sort on a stored value.

**Reversal condition.** The reviewer rejecting `ASSUMED_ZERO` (§2.4) changes decision point 2 for modifying factors; a finalised FSSAI regulation with a different formula would need a new algorithm value, not an edit to this one.

---

## D-061 — Search v2: server-side filters and sort, a deterministic query parser, suggestions, related rows and batch fit

**Date:** 2026-09-15 · **Status:** ACCEPTED (deterministic parts); LLM intent extraction DEFERRED to the `ai` module (Increment 5) · **Owner:** Product owner (requested, build plan Increment 2), Orchestrator (recorded)

**Context.** Search matched text and one category, then the client re-sorted whatever pages it had loaded. People could not ask for "under 10 g sugar", "no nuts" or "vegetarian"; the box offered no suggestions; typing "Amul" showed Amul products but nothing around them. D-059 added the facts (`product_search_facts`) this needs.

**Decision.**
1. **Filters and sort run in SQL** over `product_search_facts`: maximum added sugar, total sugar, sodium, saturated fat and energy; minimum protein and fibre; allergens to exclude (CONTAINS ∪ MAY_CONTAIN, never a free-from claim); VEG (matches VEG and VEGAN) or VEGAN; maximum NOVA group. Nine sorts; undeclared values always sort last. Minimum filters and the protein/fibre sorts ignore physically impossible values (over 100 g per 100 g), mirroring the frontend plausibility rule.
2. **Missing data is counted, not hidden.** A product that passes every filter it has data for but lacks a filtered fact is excluded and counted in `excludedForMissingData`; the UI says how many.
3. **Every filter value is the person's own.** The parser and the sheet only carry numbers someone typed. The one constant is salt → sodium (÷ 2.5, Regulation (EU) No 1169/2011 Annex I), a unit conversion.
4. **Deterministic parser first.** `SearchQueryParser` reads numeric limits, a closed phrase table (sorts, diet, allergens, processing) and category aliases, and echoes each as an interpreted term. A category word becomes a filter only when nothing else remains ("Amul butter" stays a name search). "non veg" and "non-vegan" never read as the opposite. Explicit parameters win; allergen exclusions union. Dismissing an interpreted chip removes that phrase from the words, so the URL never rewrites itself while someone types. LLM extraction, when built, must yield the same `SearchFilters` shape, pass the same validation, and never affect ranking.
5. **Suggestions** (`/search/suggest`): up to 10 brands (by alias too), categories and products; prefix before trigram; public catalogue data cached per normalized query (`catalog.suggest`, 10 min). **Related rows**: for a brand query, the best-rated products in its main category from other brands; for one clear product, more from its brand and higher-rated products in its category. Empty lists stay empty. **Did you mean** only when a real name is close.
6. **Batch fit** (`POST /households/{id}/products/fit:batch`, ≤ 50): membership, consent and profile read once per member; the same scope as single fit (the caller plus guarded children, never another adult); unknown products listed in `notFound`. An ACTIVE rule set's rules are held in a plain in-process map (never a Spring cache; DRAFT sets always read fresh).
7. **Unknown barcodes** are recorded on a scan miss with GTIN, first/last seen and a count only.
8. **Product page order**: allergen flag, per-person verdicts and better options (signed out: the rating sentence if rated, then the sign-in strip), then how the product was read, then the key facts, provenance, label, ingredients with additive names and the estimated diet flag, then the outcome capture.

**Known limits.** "Better rated" rows and the score sort stay empty or neutral until rule set v2 is activated (D-058). Diet filters return little until the Open Food Facts re-fetch fills diet flags. Some savoury snacks sit in the Biscuits category because of the Open Food Facts category mapping; that is catalogue data, not search.

**Alternatives rejected.** (a) Keep sorting on the client — rejected: it only sorts loaded pages and cannot filter. (b) Rewrite the URL with the parsed filters — rejected: the search box would change under the person's fingers while typing. (c) Treat an undeclared nutrient as passing a "max" filter — rejected: an absent value is not a low value.

**Reversal condition.** If people rely on the LLM fallback enough that the phrase table falls behind, the table is extended from logged unrecognised phrases (hashed, D-018), not replaced.

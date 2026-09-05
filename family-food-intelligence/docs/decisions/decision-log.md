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

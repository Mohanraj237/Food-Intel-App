# Decisions — Quick Index

This is a lookup index only. **The authoritative record is `docs/decisions/decision-log.md`** — full context, alternatives, rationale, and consequences for each entry live there. Check the full entry before relying on a decision for anything non-trivial; this file exists so a session doesn't have to load the full log just to remember which ID covers what.

| ID | One-line summary |
|---|---|
| D-001 | Child profiles stay in MVP, consent-gated; verifiable-consent mechanism is PROVISIONAL pending legal signoff |
| D-002 | Adults are their own data principals — no proxy consent, ever; adding an adult is an invitation |
| D-003 | A numeric score exists and is versioned/explainable; the per-person verdict chip is the headline, not the score |
| D-004 | Fixed verdict enum (`EVERYDAY/OCCASIONAL/LIMIT/DOESNT_FIT/INSUFFICIENT_DATA`); allergen is a separate hard flag |
| D-005 | No meal logging in MVP; outcome capture (bought this/alternative/neither/skip) instead |
| D-006 | Stack: Java 21 + Spring Boot modular monolith, PostgreSQL, React/TypeScript PWA |
| D-007 | No Redis in MVP — Caffeine + DB-backed rate limiting |
| D-008 | Email+password auth for MVP; phone/OTP is a deferred, BLOCKED adapter (no SMS provider) |
| D-009 | Every scoring rule requires a citable public standard (`standard_ref`) or it cannot activate |
| D-010 | Every rule evaluates both per-serving and per-100g; the more severe verdict wins |
| D-011 | AI never produces a number/threshold/verdict/authorization decision — structurally enforced |
| D-012 | No commercial influence on scoring/ranking — absent from the codebase, not just policy (ArchUnit-enforced) |
| D-013 | Alternatives must publish both `better_on` and `worse_on` |
| D-014 | Product facts are versioned and never overwritten; every verdict stores its 3 version references |
| D-015 | Provenance is recorded per field, not per product |
| D-016 | No automated e-commerce scraping; no US-centric commercial nutrition APIs |
| D-017 | Excluded populations (pregnancy, paediatric therapeutic, CKD, oncology, weight-loss framing) enforced in code, not just docs |
| D-018 | Search text and phone numbers hashed; no third-party analytics SDK; consent checked at processing time |
| D-019 | MVP is English (en-IN) only, fully externalised strings |
| D-020 | Registration required only at the personal-fit strip; anonymous browsing and product-level facts allowed |
| D-021 | One Maven module; boundaries enforced by ArchUnit, not by POM graph |
| D-022 | Barcode scan progressive enhancement — native `BarcodeDetector` where available, manual GTIN entry universal fallback |
| D-023 | Cross-module membership facts (guardian/self) flow through one published `HouseholdAccess` interface, never a shared repository or entity |
| D-024 | Phase 4 scoring: no invented health thresholds (active ruleset ships empty, honestly `INSUFFICIENT_DATA`); allergen match is real from day one; `scoring` reaches `household` only via `HouseholdAccess`; household fit is caller-scoped, not all-members |
| D-025 | Scoring rule content is authored via domain source → reviewed spec → migration → tests → activation (`docs/data/scoring/`); Claude never originates threshold values from general knowledge |
| D-026 | **OPEN.** `PRODUCT_IMPROVEMENT` consent purpose exists but isn't wired to `POST /api/v1/contributions` — `ConsentGate` is member-scoped, contribution is account-level; needs a deliberate design decision, not a rushed fix |
| D-027 | Phase 5 Compare/Outcome capture: Compare composes existing endpoints client-side (no comparison backend); Outcome capture UI makes no network call and never claims persistence (`outcome.notPersisted`, not `outcome.thanks`) |
| D-028 | Account erasure: two-phase (request → 30-day grace → scheduled hard erasure); `user_account` pseudonymised in place, never row-deleted; no cancellation endpoint; sole-guardian account deletion does not touch the child (known limitation) |
| D-029 | Erased account's `product_contribution` rows: anonymise `submitted_by_user_id`, retain the submission content — it is catalogue-improvement data, not the submitter's personal data |

To add a new decision: append it to `docs/decisions/decision-log.md` (never edit history — supersede with a new entry), then add its one-liner here.

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
| D-030 | D-026 resolved: `PRODUCT_IMPROVEMENT` is a required terms-of-contribution acknowledgment on the contribution row, not a consent-ledger grant — extending the ledger to an account-scoped subject was disproportionate for a purpose with no personal data |
| D-031 | Dev catalogue import: Open Food Facts via the API, through `ProductIngestionService`, never marked `VERIFIED`, idempotent, ODbL attribution recorded per source row |
| D-032 | Ops-review console: a single `UserAccount.role` (`MEMBER`/`OPS_REVIEWER`), checked server-side via `identity.application.OperatorAccess` on every call — no client-side admin assumption, no general permissions system |
| D-033 | Recommendation module MVP scope: ranks by real allergen-safety deltas and (currently inert, D-024-ready) fired-rule deltas only — never an invented nutrient-direction judgment; empty `better_on`/`worse_on` is a first-class honest result until real scoring rules exist |
| D-034 | **Product pivot to individual + family wellness platform.** Safety boundaries explicitly NOT relaxed (no diagnosis, no child weight targets, no child engagement mechanics, D-011/D-012 intact) |
| D-035 | Individual Mode is a household of one (`household.is_individual`) — one person model, not two |
| D-036 | **Supersedes D-017 for adults only.** Age band is the safety gate; weight/body metrics permitted for adults, refused in the domain for child bands (`METRIC_NOT_PERMITTED_FOR_AGE`) |
| D-037 | Metric *capture* is permitted for adults; *interpretation* (categories, reference ranges) needs a citable standard + clinical signoff ⚖️ |
| D-038 | Health visibility is per (subject, category, audience), default-closed; household role grants nothing; cross-member reads are audited |
| D-039 | Supersedes D-005 — meal logging is in scope; outcome capture retained |
| D-040 | **DEFERRED** — composite wellness score not built: no citable standard (D-009), and a family score ranks members by construction |
| D-041 | Family meal vs D-002: meals derive only for self + guarded children; another adult's share enters their log only when they confirm it; creator-only edit/delete withdraws all derived shares |
| D-042 | **Expanded individual+family brief — full review of D-001..D-041**: what stays valid, what changes, what's buildable now, what needs a ⚖️ decision, what stays excluded; lists 9 defects fixed in Phase 1 |
| D-043 | Individual and Family are lenses on one account; "Just me" provisions a household of one (`POST /me/individual`); `is_individual` flips false when a 2nd member joins |
| D-044 | **One `HealthAccessPolicy` for every health module** (supersedes D-038's mechanism): scopes = 7 metric categories + HABITS/MEDICATIONS/APPOINTMENTS/RECORDS/FOOD_PROFILE (never conditions); levels VIEW/MANAGE; SELF → GUARDIAN → CAREGIVER/SHARED → NONE; role never consulted; meal nutrition withheld without NUTRITION view; access log per subject |
| D-045 | Caregiver = adult-granted MANAGE delegation (not proxy consent); no account-less managed adult until counsel ⚖️ BLOCKED; 60+ never removes capability |
| D-046 | Experience tiers CHILD / ADULT / OLDER_ADULT / UNKNOWN derived server-side from member type + age band, fail-closed; CHILD member must hold a child band |
| D-047 | Child goals: only ACTIVITY/SLEEP/HYDRATION with AT_LEAST/MAINTAIN (`GOAL_NOT_PERMITTED_FOR_CHILD`), permanent. Adult interim guardrail: ENERGY_KCAL AT_MOST refused until a reviewed floor exists; body goals adult-only, no deadline/rate/deficit; product never computes a calorie target |
| D-048 | New consent purposes HEALTH_TRACKING (adult) + CHILD_WELLNESS_TRACKING (guardian, PROVISIONAL); consent gates collection and cross-member disclosure; child tracking guardian-entered only, never passive |
| D-049 | PROVISIONAL — records/medications/appointments built behind consent purposes + production release gate ⚖️; no dose/interaction/change advice; vaccinations without DOB or computed schedule |
| D-050 | PROVISIONAL — notifications: in-app first, per-category opt-in, quiet hours + cap, never addressed to a child, no fear/shame copy |
| D-051 | PROVISIONAL — engagement mechanics adults-only, opt-in, cooperative challenges, milestones not "streak lost"; family leaderboards excluded for everyone |
| D-052 | PROVISIONAL — intelligence: deterministic facts, AI phrasing only behind D-011 fidelity check, closed-intent NL, no child data to any model (AC-3.8 wins), MOCKED until credential |
| D-053 | Day boundaries use `household.time_zone` (default Asia/Kolkata); storage stays UTC |
| D-054 | Family page: household summary and weekly trends from the caller's own cards; one overview request; never a ranking |
| D-055 | Consumer identity ("Fresh Kitchen": cream, terracotta, self-hosted Plus Jakarta Sans, SVG icons) and IA: Home lands at `/`, Saved (device-local) / History / Insights routes, `/next` hub removed, gated previews get consumer copy |
| D-056 | **Supersedes D-007 for caching** (rate limiting stays DB): `familyfood.cache.type` caffeine (default) / redis / none; Redis optional (fallback + circuit breaker, health DEGRADED not DOWN); only public catalogue data cached (`CachingBoundaryTest`); keys `ffi:<module>.<what>:<version>::<id>` |
| D-057 | Virtual threads for blocking I/O only; CPU-bound work (Argon2) on bounded platform pools; DB pool sized to Postgres, not request concurrency; pinning measured; not a crash fix |
| D-058 | Rule set v2 evidence pack: adds `WHO_SEARO_NPM_2017`, updates `FSSAI_INR`'s scope note to name the specific draft notification and its draft status; the draft is accepted as a citable evidence-level-B source, never shown as the regulator's own rating; `rules-v2.md`/`review.md` carry the actual proposed content and nine open questions — nothing is active until the named reviewer signs `review.md` and a migration is written |
| D-059 | Scoring engine v2: `POINTS_NET_V2` per rule set (band tables, capping, star bands in config), missing baseline → INSUFFICIENT_DATA, missing modifying → ASSUMED_ZERO; derived facts `INR_STARS`/`NOVA_GROUP` as rule inputs via `AssessmentOrchestrator`; persisted `product_score` + event-driven rescoring; enrichment fills null → value only with provenance; additives marked (Codex names only), never judged; ACTIVE set still empty until V17/V18 |
| D-061 | Search v2: filters and nine sorts in SQL over `product_search_facts`, undeclared values last and counted in `excludedForMissingData`; deterministic query parser (numbers are the person's own; category word only when nothing else remains; chips edit the words); suggestions and related rows; batch fit ≤ 50 with the single-fit scope; unknown barcodes recorded without a person; LLM intent extraction deferred to `ai` |

To add a new decision: append it to `docs/decisions/decision-log.md` (never edit history — supersede with a new entry), then add its one-liner here.

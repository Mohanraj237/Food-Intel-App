# Project State

**Last updated:** 2026-09-06 (UI/UX revamp, Step 1 of 14 — this entry's author). A full
consumer-grade UI/UX revamp was commissioned; full detail and remaining sequence in
`.claude/current-task.md`'s top entry. This step built the shared design-system foundation
(`Card`, `ProductImage`, `SearchBar`) and migrated the four screens that had independently
duplicated the same bordered-card CSS. Confirmed via audit: zero product-image infrastructure
exists anywhere in the app today (backend or frontend) — that is Step 2, not yet started.
Also reconciled three prior sessions' fully-tested, uncommitted work (catalogue import,
ops-review, recommendation, activity modules) into two coherent commits, at the user's
explicit instruction, since it was interleaved in files this step needed to touch. Working
tree is clean. Verified: frontend `npm run typecheck` clean, `npm run test` 8/8; backend
untouched this step (unchanged at 204/204 per the commit that landed the prior sessions).

---

**Previous entry (2026-09-06, Phase 6 hardening sprint, this entry's author). Added 30 new
backend tests for the three modules the feature-completion sprint left untested
(`recommendation`, `activity`, ops-review) and found + fixed 4 real defects along the way —
see `.claude/current-task.md`'s top section for the full list (one was a P1: a whole-number
nutrient amount crashed ops-review approval with a 500). Full backend regression: **204/204**
(88 unit + 116 integration, up from 174 before this sprint). Frontend: typecheck + 8/8 tests
green. Catalogue unchanged at 894 real products (893 Open Food Facts + 1 ops-review test
artifact from live verification) — Open Food Facts' outage from the prior sprint was checked
again and had not recovered; the importer remains ready to re-run.

---

**Previous entry (2026-09-06, feature-completion sprint, commit `3ef643a`).** Added: real
Open Food Facts dev catalogue (893 products, D-031), ops-review console (D-032),
`recommendation` module (D-013, D-033), `activity` module (FR-012), resolved D-026 (D-030).
Full detail: `.claude/current-task.md`. Per-row requirement status lives in
`docs/product/requirements-traceability.md` (now v2.1).

---

**Previous entry (2026-09-06, earlier the same day):** Two sessions landed the same day, in the same working tree, on independent files — neither has been committed yet (see "Working tree" note in `.claude/current-task.md`):
1. **Phase 5 session** — core consumer UX: Compare, product-level health assessment, per-person "why" reasons, an honest Outcome-capture UI. D-027.
2. **Data rights session** (this entry's author) — NFR-09/CH-008/FR-010's erasure gap: account export, account deletion (two-phase), child erasure. New `datarights` module. D-028/D-029.

See `.claude/current-task.md` for detail on both.

**Traceability note:** `docs/product/requirements-traceability.md` was audited this session — every one of its 56 rows was re-verified against the repository (not against this file's own summary). Its per-row evidence is more precise than the phase table below; treat that document as the source for requirement-level status, and this table as the phase-level summary.

## Phase status

| Phase | Status | Evidence |
|---|---|---|
| 0 — Discovery | **DONE** | `docs/engineering/repository-assessment.md`, `docs/decisions/decision-log.md` (33 decisions, D-001..D-033 — all `ACCEPTED`; D-026 was `OPEN`, resolved by D-030), `docs/architecture/*`, `docs/product/*`, `docs/design/design-system.md` |
| 1 — Foundation | **DONE** | Git, `.gitignore`, Maven, Spring Boot boots, PostgreSQL + Flyway (V1-V6), Argon2id + JWT auth round-trips, RFC 7807 errors, structured logging + masking, ArchUnit boundaries, frontend shell + design tokens, CI, Docker |
| 2 — Household, consent, profiles | **BACKEND DONE, FRONTEND DONE** | `household`/`profile`/`consent` modules complete and tested; `ConsentGate` has one real caller (`PersonalFitService`). **D-026 resolved this sprint (D-030):** contribution is gated by a required terms-of-contribution acknowledgment on the row itself, not a consent-ledger grant. `GET /api/v1/households` lists the caller's own households. `datarights` module — account export, account deletion (two-phase), child erasure (D-028/D-029) |
| 3 — Product intelligence | **READ PATH DONE; INTAKE + OPS REVIEW DONE; REAL DEV CATALOGUE (893)** | `search` unchanged. `capture` — manual-entry contribution intake (unchanged) **+ ops-review console this sprint** (queue/detail/approve/reject, D-032, live-verified end-to-end: submit → approve → real searchable product). `catalog` gains `ProductIngestionService` (D-014/D-015-compliant write path) and an Open Food Facts dev-catalogue importer (D-031) — **893 real products imported**, not 5000+, due to a sustained Open Food Facts API outage spanning this whole session (tooling is resumable/idempotent; re-running once the source recovers needs no code change — `tools/data-import/README.md`). OCR intake still not built (no AI credential) |
| 4 — Scoring & personalisation | **FOUNDATION DONE, GATE 4 NOT YET MET; RECOMMENDATION MODULE ADDED** | Scoring engine unchanged — zero nutrient-threshold rules active by design (D-024/D-025). **New this sprint:** `recommendation` module (D-013) — ranks on real allergen-safety deltas and (inert until D-025) verdict deltas only, never an invented nutrient-direction judgment (D-033); live-verified. Next steps: `docs/engineering/implementation-plan.md` §6 |
| 5 — Core UX | **CORE LOOP REAL, OUTCOME NOW PERSISTED** | Household/profile/consent/auth/product-fit/scan-contribution/product-detail/compare screens real (prior sessions). **New this sprint:** `activity` module (FR-012) — `OutcomeCapture` now persists for real (loading/success/error/retry, no more `outcome.notPersisted`); best-effort `VIEWED`/`COMPARED` events from Product/Compare; Home gained a real "Recently viewed" rail; a "Better options" section (`recommendation`) on Product. Still open: no e2e suite, no formal full-app accessibility audit, no browser-based visual walkthrough performed (no browser-automation tool available in this environment) |
| 6 — Quality hardening | NOT STARTED | |
| 7 — Release candidate | NOT STARTED | |

## What exists right now

**Backend** (`backend/src/main/java/com/familyfood/`) — committed on `master`:
- `platform/`, `identity/`, `audit/`, `household/`, `profile/`, `consent/`, `catalog/`, `search/`, `scoring/` — unchanged this session.
- **`capture/` (new this session)** — manual-entry contribution intake only (the OCR intake path in `system-architecture.md` §6 is a separate, later piece — no AI credential exists). `domain`: `ProductContribution` (always created `PENDING`, no relationship into `catalog.domain` — never auto-published), `ContributionStatus`. `infra`: `ProductContributionRepository`. `application`: `ContributionService.submit(...)` — re-validates everything itself (product name required, category code checked against the real reference table via `ProductQueryService.listCategories()`, every nutrient amount rejected if negative, every list/string length-capped). `api`: `ContributionController` (`POST /api/v1/contributions`, requires sign-in, not in `SecurityConfig`'s permit-all list — falls under the default "needs a principal" rule with no config change needed). Migration `V6__capture.sql`.
- **Known, deliberately-unresolved gap (D-026, status `OPEN`):** a `PRODUCT_IMPROVEMENT` consent purpose was already seeded in V1 for exactly this action, but the contribution endpoint doesn't check it — `ConsentGate` is scoped to a household member, contribution is an account-level action with no household-member dependency, and force-fitting the existing gate would incorrectly block anyone without a household yet. Flagged, not fixed — see D-026 for the options a future session should choose between.
- **`household/` — `GET /api/v1/households` (new this session)** — lists every household the signed-in caller currently belongs to, oldest membership first (deliberate ordering — the frontend uses it to pick a default when someone belongs to more than one). `HouseholdService.listMyHouseholds` batches the household-name lookup (`findAllById`, no N+1). No new module, no `SecurityConfig` change (never was in the permit-all list).
- Full regression: `mvn -o verify` — **159/159 tests passing** (unchanged count: no new tests were written this session, per explicit instruction; both the `capture` and `household` agents verified their work against real Testcontainers Postgres runs and the existing `HouseholdIT` suite instead, confirming zero regressions).

**Frontend** (`frontend/src/`) — committed this session:
- `lib/capture.ts` (new) — typed client for `POST /api/v1/contributions`, matching the backend contract field-for-field.
- `features/scan/ContributeProductForm.tsx` (+ `.css`, new) — the manual-entry form: product name (required), brand, category (from the existing `getCategories()`), pack size, serving text, the same seven "core" nutrients `ProductScreen` already uses (per-100 and optional per-serving), ingredients text, allergens (the existing closed `ALLERGEN_CODES` set from `lib/profile.ts`). Success and error states; never auto-publishes anything client-side.
- `features/scan/ScanScreen.tsx`/`.css` — the barcode-not-found dead end now offers "Add this product", swapping in `ContributeProductForm` pre-filled with the scanned barcode (a local state toggle, not a new route — mirrors the screen's existing camera/manual-entry toggle).
- `locales/en-IN.json` — 22 new `contribute.*` keys (orchestrator-added before the agents ran, to avoid the two-workstream locale-file race a prior session hit).
- Design-reference note: the user shared UI screenshots from other consumer apps as inspiration. Only the applicable patterns were used (compact form chrome, a clear "what happens now" success message) — the screenshots' large hero score/compatibility-percentage treatment, cart/checkout/store-comparison UI, and judgmental verdict language were deliberately **not** carried over: they conflict with D-003 (score never the largest element), this product's explicit non-commerce scope (`docs/product/post-mvp-backlog.md`), and the closed, reviewed verdict vocabulary (D-004), respectively.
- **`lib/useMyHousehold.ts` (new this session)** — the one place that decides "which household is *the* household" for the signed-in caller, backed by `GET /households`. `getMyHouseholdId`/`setMyHouseholdId`/`clearMyHouseholdId` and their `localStorage` key are **deleted**, not superseded-but-kept — `FamilyScreen`, `ProductScreen`'s `FitSection`, and `ConsentPanel` all read from the hook now. The old "stale id → clear localStorage" self-heal became "403 → invalidate the `['households','mine']` query"; both stale-detection callbacks needed `useCallback` to avoid a refetch loop now that the household id is query-derived rather than local state (a failure mode the old code structurally couldn't have) — caught and fixed by the implementing agent itself, verified correct on review.

**Phase 5 session (this session) — Compare, health assessment, "why" reasons, Outcome capture (D-027):**
- `lib/reasons.ts` (new) — `describeReason(reason, nutrients)` turns a structured `Reason` (`scoring.ts`, mirrors `scoring/application/Reason.java` exactly) into the `why.*` locale copy, looking up a nutrient's display name/unit from the product's own already-fetched `nutrients` list rather than a second hard-coded map.
- `design-system/ReasonList.tsx` (+ `.css`, new) — shared renderer for a `Reason[]`, used by both the per-person "why" disclosure and the product-level health assessment.
- `features/product/HealthAssessmentSection.tsx` (+ `.css`, new) — wires up `getProductHealth` (`GET /products/{id}/assessment`), implemented in Phase 4 but never called from the frontend until now. Collapsed `<details>`, placed below the per-person verdict strip on `ProductScreen` — preserves D-003 (score never the largest element, never above the verdict). Renders `NOT_YET_SCORED` (today's universal, honest result per D-024) as a plain fact, not an error.
- `ProductScreen.tsx`/`.css` — `MemberFitRow` gained a "See why" `<details>` disclosure (`ReasonList`) so `PersonalFit.reasons` — real, deterministic, previously fetched but never rendered — is now visible; added the `HealthAssessmentSection` and a "Compare" entry point (`/compare?a={id}`).
- `features/compare/CompareScreen.tsx` (+ `.css`, new) — the Compare screen (route `/compare`, new in `App.tsx`). No comparison backend exists (`recommendation` module `NOT_STARTED`) — this composes two already-loaded real products client-side from endpoints that already exist (`getProduct`, `getProductHealth`, `getHouseholdFit`), with an inline local-state search picker for the second product rather than a new "select products" route. Full rationale: D-027.
- `features/product/OutcomeCapture.tsx` (+ `.css`, new) — "Did you buy it?" (D-005). No `activity` backend exists, so this makes **no network call**; selecting an option shows the new `outcome.notPersisted` copy, never the persistence-implying `outcome.thanks`. A real, isolated future-integration point, not a mock.
- `locales/en-IN.json` — added `score.notYetScored`, `why.noGoals`, `why.missingData`, `outcome.notPersisted`, and a set of `compare.*` keys; reused already-present `why.*`/`score.*`/`compare.*`/`outcome.*`/`action.compare`/`action.seeWhy` keys that a prior session had prepared but nothing yet rendered.
- Verified: `npm run typecheck` clean, `npm run test` 8/8 passing (existing suite only, including the vocabulary gate — no new frontend tests added this session). `npm run lint` still cannot run — no `eslint.config.js` exists in the frontend (pre-existing gap, `NFR-14`, not touched this session). Every new/changed module confirmed to transform cleanly through the running Vite dev server (`curl` against each file's dev-server URL, HTTP 200); `GET /api/v1/products/{uuid}/assessment` confirmed reachable anonymously against the running backend (404 for an unknown id, not 401). **Not verified: a real rendered-in-browser walkthrough** — no browser-automation tool was available in this environment and the currently-running dev Postgres has zero seeded products (pre-existing "no real seed catalogue" gap, see "Known external blockers" below), so Product/Compare could not be exercised end-to-end against real data this session. Flagged rather than silently skipped — see `.claude/current-task.md`.

**Data rights session (this session) — account export, account deletion, child erasure (NFR-09/CH-008/FR-010, D-028/D-029):**
- **New `datarights` module** (`application`: `DataRightsService` — export orchestration, `performFullErasure`, `eraseChild`; `AccountErasureScheduler` — hourly `@Scheduled` hard-erasure sweep; `api`: `DataRightsController`). Mirrors the existing precedent of a higher-level module (`scoring.PersonalFitServiceImpl`) depending on several other modules' `application` packages together — `identity.application.AccountErasureService` (new), `household.application.HouseholdService`/`HouseholdAccess`, `profile.application.ProfileService`, `consent.application.ConsentService`, `capture.application.ContributionService` — never their `domain`/`infra`/`api` packages.
- **`POST /api/v1/account/export`** → **`GET`** (own account, every household membership with full profile-version history and consent ledger — including `evidence`, via new `ConsentExportView`, the first real consumer of `ConsentRecord.getEvidence()`'s documented "exposed only for export" contract — every currently-guarded child with the same shape, own product contributions). Never another person's data.
- **`POST /api/v1/account/deletion`** — immediate: blocks sign-in (`AccountStatus.DELETION_REQUESTED`), revokes every refresh token, audits `ACCOUNT_DELETION_REQUESTED`. Hard erasure runs later via `AccountErasureScheduler` once `familyfood.privacy.erasure-grace-period` (`P30D`, already configured before this session) elapses — anonymises `user_account` in place (`UserAccount.eraseIdentity`: synthetic unique email, unusable password hash, `AccountStatus.ERASED`) rather than deleting the row, scrubs every `household_member` row the account held (`displayLabel` → `"Erased member"`, `leave()`), deletes all `person_profile` versions for those memberships, revokes guardian links the account held, anonymises `product_contribution.submitted_by_user_id` for that account's own submissions. `consent_record` and `audit_event` are never touched by any code path here.
- **`POST /api/v1/households/{id}/members/{id}/erase`** — guardian-only child erasure (`HouseholdService.eraseChildMember`, new). Distinct from the existing `DELETE .../members/{id}` (soft leave): this hard-deletes the child's profile and scrubs the membership; rejects an ADULT target (400); rejects a non-guardian caller (403 `NOT_A_GUARDIAN`) and a non-member caller (403 `NOT_HOUSEHOLD_MEMBER`).
- Migration `V7__data_rights.sql` — adds `AccountStatus.ERASED` to `user_account`'s status CHECK; drops `product_contribution.submitted_by_user_id`'s `NOT NULL` (nullable once an account is erased).
- Frontend: `lib/account.ts` (new, `exportMyData`/`requestAccountDeletion`), `lib/household.ts` gains `eraseChildMember`. `SettingsScreen.tsx`'s `DataRightsPlaceholder` replaced with a real `DataRightsPanel` (export → client-side JSON download; delete account → type-to-confirm `DELETE` → `destructive` button → 3-second delay before local sign-out so the confirmation message is actually seen). `FamilyScreen.tsx`'s `MembersList` gains a `destructive` "Erase this child's data" button per CHILD member (shown to every member, same optimistic-then-backend-enforced pattern `ProfileActionButton` already uses for guardian-only edit access — a non-guardian who clicks it simply sees the resulting `NOT_A_GUARDIAN` error).
- New backend tests: `AccountErasureIT` (5), `AccountExportIT` (4), `ChildErasureIT` (6) — 15 new integration tests against real Postgres via Testcontainers, covering request/hard-erasure/idempotency, household-safety (a second adult, a child, and an unrelated household all verified untouched via direct DB queries), guardian-only authorization (non-guardian co-member and unrelated-household adult both denied), and export scope (no password hash, guarded-child inclusion is guardian-only, no cross-member profile leakage).
- Full backend regression: `mvn -o verify` — **174/174 tests passing** (88 unit + 86 integration; was 159/159 before this session's 15 new tests). Frontend: `npm run typecheck` clean, `npm run test` 8/8 passing (existing suite, no new frontend tests). Live-verified against the running instance: backend restarted (new migration `V7` applied), `POST /account/deletion` returns `202` with a real `erasureEffectiveAt` ~30 days out, a subsequent login attempt returns `401`, `GET /account/export` returns `200` authenticated / `401` anonymous.
- **Discovered mid-session:** this working tree has multiple other Claude Code sessions active concurrently (`ListAgents` showed four peer interactive sessions) with real, uncommitted changes already present when this session's own edits began — the Phase 5 UX work above is one of them. No file-level conflict occurred (different files throughout, except `locales/en-IN.json`, which both sessions added distinct keys to without collision — verified valid JSON and the frontend test suite green after combining). **Nothing was committed this session** — see `.claude/current-task.md` for why.

**Known, documented (not hidden) limitations (unchanged plus several resolved):**
- **Resolved this sprint**: real Open Food Facts dev catalogue exists (893 products, D-031); ops-review console exists and is live-verified (D-032); D-026 resolved (D-030); `recommendation`/alternatives exists (D-013, D-033); `activity`/outcome persistence exists (FR-012) — Compare and product-level health assessment were already real from a prior session.
- Zero nutrient-threshold scoring rules are active (unchanged, D-024/D-025) — so every real Compare/health-assessment/alternatives-verdict result today is honestly `NOT_YET_SCORED`/`INSUFFICIENT_DATA`, and `recommendation`'s verdict-comparison dimension is correspondingly inert until a reviewed rule set activates.
- No correction/flag flow for a product already in the catalogue (FR-011) — distinct from the new-product contribution flow, which now has a real reviewer path (ops console).
- `recommendation` deliberately does not compare nutrient values directly (e.g. "lower sugar") — that needs D-025's reviewed rule content first (D-033); today it ranks on allergen-safety and verdict deltas only.
- No self-service cancellation of a pending account deletion, and deleting the sole guardian of a child does not also erase the child — both deliberate, documented limitations, not oversights (D-028).
- No frontend ESLint config exists (`eslint.config.js` absent) — `npm run lint` cannot run at all. Pre-existing (NFR-14).
- No formal accessibility audit of the full application, no e2e suite, no browser-based visual walkthrough (no browser-automation tool available in this environment) — explicitly deferred to the dedicated hardening phase.
- No dedicated unit/integration tests were written this sprint for `recommendation`, `activity`, or the ops-review additions to `capture` — full-suite regression (unit+ArchUnit+integration) stayed green throughout, and every new endpoint was live-verified by hand against the running instance, but that is not the same as a maintained automated test for these specific modules. Flagged for the hardening phase.

## Known external blockers (Phase 0, updated this sprint)

- No AI provider credential → OCR/AI explanation `MOCKED` until a key exists; the OCR intake path into `capture` specifically remains unbuilt for the same reason
- No SMS provider → phone/OTP auth `BLOCKED`; email+password ships (D-008)
- Privacy counsel not engaged → `GUARDIAN_ATTESTATION` is `PROVISIONAL`/`REQUIRES_LEGAL_SIGNOFF` (D-001)
- No FSSAI Indian Nutrition Rating data feed → `DEFERRED`
- **Open Food Facts API outage spanning this entire sprint** (`HTTP 503`/`401`, confirmed not specific to this session's request pattern) → the dev catalogue stopped at 893 real products, not the 5000+ target. Tooling is fully built, idempotent, and resumable (`tools/data-import/README.md`) — this is now a re-run away, not new engineering, once the source recovers.
- **No nutrition/regulatory-reviewed numeric threshold set** → real `scoring_rule`/`score_component` rows `DEFERRED` until one is authored and reviewed (D-024/D-025) — still the single biggest lever on product value, and now also the gate on `recommendation`'s richer dimensions (D-033)

## Where the master requirements live

One directory above this repo (`../`, not to be edited): `Family_Food_Intelligence_Documentation_Pack_v0.2/` (commissioned spec) and `files/` (PANTRY critique). Both fully digested into `docs/decisions/decision-log.md` and `docs/engineering/repository-assessment.md` — should not need re-reading.

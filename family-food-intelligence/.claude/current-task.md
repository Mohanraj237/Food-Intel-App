# Current Task

**As of:** 2026-09-06. Two independent sessions landed in this same working tree
today, on disjoint files, both uncommitted: a Phase 5 frontend session (below,
D-027) and a data-rights session (immediately below this line, D-028/D-029).
**Neither session found the other's changes surprising in content — both were
scoped correctly and don't conflict — but this is a multi-writer working tree
with no commit yet reconciling the two.** See "Multi-session note" under the
data-rights section for what was checked before proceeding, and "Working tree"
near the end of this file for the combined `git status`.

## Data rights session (NFR-09 / CH-008 / FR-010 erasure gap, D-028/D-029)

**What was built** (see `.claude/state.md` for the full file-by-file list):
new `datarights` backend module — `GET /api/v1/account/export`, `POST
/api/v1/account/deletion` (two-phase: immediate request + scheduled hard
erasure after a 30-day grace period), `POST /households/{id}/members/{id}
/erase` (guardian-only child erasure); `identity.application
.AccountErasureService` (new); additive methods on `HouseholdService`
(`eraseAccountMemberships`, `eraseChildMember`), `ProfileService`
(`eraseAllVersions`, `getVersionHistory`), `ConsentService`
(`listForExport`), `ContributionService` (`anonymizeSubmitter`, `listMine`);
migration `V7__data_rights.sql`; frontend `lib/account.ts` (new), a real
`DataRightsPanel` in `SettingsScreen.tsx`, and a per-child "Erase this
child's data" button in `FamilyScreen.tsx`.

**Design work done before any code was written** (per this session's own
instruction not to guess at a destructive-data policy): inspected every
table that can hold account/member-associated data, and found the schema
already anticipated most of this — `user_account.status` already had a
`DELETION_REQUESTED` value, `deleted_at`/`markDeletionRequested`/`isUsable()`
already existed, `familyfood.privacy.erasure-grace-period` was already
configured, `UserAccountRepository.findByDeletedAtBefore` was already
commented "for the scheduled hard-delete job," and `AuditAction` already had
`ACCOUNT_DELETION_REQUESTED`/`ACCOUNT_DELETED`/`CHILD_DATA_DELETED`/
`DATA_EXPORTED`/`DATA_ERASURE_COMPLETED` — all unused until this session. The
retention matrix landed on: delete `person_profile` (all versions); scrub
`household_member`/`guardian_link` in place (never row-delete — `V3`'s own
comments explain why: surviving `consent_record` rows reference them with no
`ON DELETE` action); anonymise `user_account` in place for the same reason;
anonymise (never delete) `product_contribution.submitted_by_user_id`; never
touch `consent_record` or `audit_event`. Full reasoning: D-028 (account
erasure design) and D-029 (contribution anonymisation) in
`docs/decisions/decision-log.md`.

**Multi-session note.** Partway through this session, `git status` showed
`.claude/state.md`, `.claude/current-task.md`, `.claude/decisions.md`,
`docs/decisions/decision-log.md` and `docs/product/requirements-
traceability.md` already modified on disk in ways this session had not made
— the Phase 5 UX session below. `ListAgents` confirmed four other peer
interactive sessions active on this machine. Before touching any of those
shared files, each was re-read fresh and diffed against git HEAD to find
exactly what the other session had changed, so this session's edits are
additive to that content, not overwritten on top of it (renumbered D-027 →
D-028/D-029 to avoid the collision; requirements-traceability.md rows
NFR-09/CH-008/FR-010 don't overlap the other session's FR-007/FR-008/FR-012
edits; `locales/en-IN.json` — the one file both sessions add keys to — was
checked for valid JSON and a green frontend test run after combining). **No
code file was overwritten or reverted.** Per standing instruction, nothing
was committed — see "Working tree" below.

**Tests:** backend `mvn -o verify` — **174/174** (88 unit + 86 integration;
15 new: `AccountErasureIT`, `AccountExportIT`, `ChildErasureIT`, all against
real Postgres via Testcontainers). Frontend `npm run typecheck` clean, `npm
run test` 8/8 (existing suite, unchanged — no new frontend tests written).
Live-verified against the running instance (backend restarted to pick up the
new migration/code): `POST /account/deletion` → `202` with a real 30-day-out
`erasureEffectiveAt`; a login attempt afterward → `401`; `GET
/account/export` → `200` authenticated, `401` anonymous.

**Known, deliberate limitations** (documented, not hidden — D-028): no
self-service cancellation of a pending account deletion once requested (the
account cannot sign back in to ask for one); deleting the sole guardian of a
child does not also erase that child (a real orphaned-child product gap, not
solved here — would need a new guardian-handoff workflow this session's
scope did not ask for).

**Not touched, per this session's explicit hard scope:** scoring rules,
recommendation, activity, OCR, AI, product ingestion, the ops-review
console, invitation email, D-026 (`PRODUCT_IMPROVEMENT` consent wiring), and
none of the Phase 5 Compare/Outcome-capture work described below.

---

## Previous session (Phase 5 frontend — Compare, health assessment, Outcome capture)

**As of:** 2026-09-06. Phase 5 frontend session — core consumer UX. Frontend-only
scope (backend untouched). Working tree has real, uncommitted changes — see
"Working tree" below; nothing has been committed yet this session pending the
user's go-ahead.

### What just happened

The Phase 5 brief asked for Home/Search/Product/Family/Settings/Scan to be
reviewed and, where genuinely missing, built out — plus two explicitly-named
missing features, Compare and Outcome capture. On inspection, Home, Search,
Family, Settings and Scan were already real, tested, and matched the brief's
own bar (loading/empty/error states, i18n, accessibility markup) — rebuilding
any of them would have been unjustified churn. The actual gaps, confirmed
against `docs/product/requirements-traceability.md`'s Phase 5 row and FR-006/
FR-008/FR-012 rows, were narrower and more specific than the brief's full
scope implied:

1. **`GET /products/{id}/assessment` (product-level health assessment) was
   fully implemented in Phase 4 (`scoring/api/ProductHealthController.java`)
   but the frontend never called it.** Same for `PersonalFit.reasons` — Phase
   4's `Reason[]` was fetched (`getHouseholdFit`) but never rendered.
   `locales/en-IN.json` already had `why.*`/`score.*` keys sitting unused,
   apparently prepared for exactly this in an earlier session.
2. **Compare did not exist at all.** No route, no screen, no backend either
   (`recommendation` module `NOT_STARTED`).
3. **Outcome capture did not exist at all.** No UI, no `activity` backend.
   `locales/en-IN.json` already had `outcome.*` keys sitting unused too.

## What was built (all frontend, `frontend/src/`)

- `lib/reasons.ts`, `design-system/ReasonList.tsx`/`.css` — shared rendering
  for the closed, structured `Reason` shape (D-011: never free text).
- `features/product/HealthAssessmentSection.tsx`/`.css` — the product-level
  assessment, wired to the existing endpoint. Collapsed, below the per-person
  verdict, never the largest element (preserves D-003 exactly as
  `ProductScreen`'s own information-order comment commits to).
- `ProductScreen.tsx`/`.css` — added a "See why" disclosure per member
  (renders real `PersonalFit.reasons`), the health assessment section, and a
  "Compare" entry point.
- `features/compare/CompareScreen.tsx`/`.css`, new route `/compare` in
  `App.tsx` — a real Compare screen. No comparison backend exists, so this is
  a client-side composition of two already-loaded products through endpoints
  that already exist (`getProduct`, `getProductHealth`, `getHouseholdFit`).
  Second product picked via an inline local-state search, not a new route.
- `features/product/OutcomeCapture.tsx`/`.css` — "Did you buy it?" (D-005).
  No `activity` backend exists, so this makes **no network call** and never
  claims the choice was saved (`outcome.notPersisted`, deliberately not the
  persistence-implying `outcome.thanks` that was already sitting in the
  locale bundle).
- `locales/en-IN.json` — a handful of new keys (`score.notYetScored`,
  `why.noGoals`, `why.missingData`, `outcome.notPersisted`, several
  `compare.dimension.*`); everything else reused what was already there.
- **Full rationale for the Compare/Outcome architectural choices: D-027**
  (`docs/decisions/decision-log.md`).

## What was deliberately not touched / not built

- **Alternatives/ranking (FR-007, PX-2)** — still `NOT_STARTED`. Needs a
  `recommendation` module and real product data; out of scope for a
  frontend-only session with no backend to call.
- **Real persistence for Outcome capture (FR-012)** — needs an `activity`
  module. The frontend UI is real and ready; only the `activity` backend is
  missing.
- **Backend** — nothing under `backend/` was touched, per this session's hard
  scope. `GET /products/{id}/assessment` and `GET /households/{id}/products/
  {id}/fit` already existed; nothing new was needed on that side to build
  Compare/health-assessment/why.
- **A frontend ESLint config** — `npm run lint` still cannot run at all (no
  `eslint.config.js` exists anywhere in `frontend/`). Pre-existing gap
  (NFR-14), not introduced or fixed this session; flagged, not silently
  ignored.
- **A full-application accessibility audit** — only the screens this session
  touched were reviewed by hand (see below). The rest of the app was not
  re-audited.

## Verification performed this session

- `npm run typecheck` — clean.
- `npm run test` — 8/8 passing, unchanged suite (including the banned-
  vocabulary/i18n-coverage gate — the new locale keys were checked against it
  and pass).
- `npm run lint` — cannot run (see above, pre-existing).
- Every new/changed frontend module confirmed to transform without error
  through the already-running Vite dev server (`localhost:5173`) — a `curl`
  against each file's dev-server URL returned 200, which a syntax/transform
  error would not.
- `GET /api/v1/products/{random-uuid}/assessment` against the already-running
  backend (`localhost:8090`) returned 404, not 401 — confirms the anonymous
  `permitAll` path this session's new `HealthAssessmentSection` relies on is
  correctly configured (unchanged backend code, just confirming the existing
  contract).
- Manual, hand review of the new/changed markup for accessibility: semantic
  `<details>`/`<summary>` for both disclosures (native keyboard support, no
  custom JS toggle needed), a `<fieldset>`/`<legend>`/native radio inputs for
  Outcome capture with a `role="status"` note, a proper `<table>` with
  `<caption>`/`scope="col"`/`scope="row"` for the comparison table,
  `aria-hidden="true"` on every decorative glyph, and removal of an initial
  draft's duplicate-adjacent-link pattern (the whole product card and its
  "View product" text both linking to the same place) once noticed on review.

## What could not be verified this session, and why

- **No real, rendered-in-a-browser walkthrough of Product/Compare/Outcome.**
  No browser-automation tool (Playwright, chromium-cli, etc.) was available
  in this Windows environment, and installing one would have meant adding a
  new heavy devDependency not requested by the brief. Typecheck and tests
  verify code correctness, not what a user actually sees — this is flagged
  explicitly rather than claimed as done, per this session's own instruction
  not to claim a UI feature works without seeing it in a browser.
- **The currently-running dev Postgres has zero seeded products**
  (`GET /api/v1/search` returns `{"items":[],"total":0}` right now) — a
  pre-existing environment condition (`.claude/state.md`'s "Known external
  blockers": "No real seed catalogue → dev seed only"), not something this
  session caused or should fix (seeding/reseeding a database is backend/data
  territory, out of this session's hard scope). This means Product Detail and
  Compare could not be exercised end-to-end against a real product even if a
  browser tool had been available. A future session with backend access
  should seed a small dev catalogue specifically to make Phase 5/6 frontend
  work visually verifiable.

## Running instance

Unchanged from before this session — still up, still not restarted (no
backend code changed):
- Postgres: Docker container `ffi-postgres`, healthy
- Backend: `http://localhost:8090` (Spring Boot, `local` profile) — catalogue
  is empty (see above)
- Frontend: `http://localhost:5173` (Vite dev server) — picked up this
  session's changes via HMR automatically; no restart performed or needed

## Working tree

Not committed. `git status` at the end of this session shows the same
pre-existing modified/untracked files from before this session started, plus
this session's edits layered on top (`ProductScreen.tsx`/`.css`, `App.tsx`,
`locales/en-IN.json`, `docs/decisions/decision-log.md`, `.claude/*.md`, and
the new files listed above). Per standing instruction, commits are only made
when the user explicitly asks.

## Recommended next step

**Update (data rights session): item 2 below is now done** — see the
"Data rights session" heading at the top of this file. Remaining options:
1. **Seed a small real dev catalogue** (a handful of real products with real
   nutrient panels) so Phase 5's frontend — and any future frontend session —
   can actually be exercised end-to-end in a browser instead of only
   typechecked and unit-tested. This is the single biggest lever on being
   able to *verify* frontend work going forward, not just write it.
2. ~~Data rights (export, account/child deletion)~~ — **done**, see above.
3. **Reconcile the multiple uncommitted sessions sitting in this working
   tree** (see the "Multi-session note" at the top of this file) into
   deliberate, reviewed commits — this is now more pressing than either
   remaining backend gap, since several sessions' worth of real, tested work
   is sitting uncommitted at once.

Everything else of substance either needs a human input this session can't
supply (scoring content, email provider, an accessibility specialist for a
full audit) or is explicitly sequenced after something else (recommendation
needs real scoring + real data; a formal e2e suite is more valuable once
there is real data to run it against).

# Current Task

**As of:** 2026-09-06 (UI/UX revamp, Step 1 of 14 — this entry's author).

## UI/UX revamp — Step 1: design-system foundation

**Objective:** a full consumer-grade UI/UX revamp was commissioned (referencing Zomato/
Blinkit/Flipkart/HealthifyMe interaction patterns only, never their branding/layout), to be
done in 14 sequenced steps, each a coherent commit, never a big-bang rewrite. Full sequence
lives in the session transcript; this file tracks progress step by step.

**Before writing any code:** a full read-only audit of every frontend screen, design-system
component, the app shell, and the design docs was run (a general-purpose subagent). Key
findings that reshaped the plan: (1) a "Zomato/Blinkit-grade rework" of cards/rails/categories
already happened in a prior session (`tokens.css`'s own doc-comment says so) — Home/Search/
Product/Compare are real, tested, and already implement the documented information order
(D-003), not blank screens; (2) **zero product-image infrastructure exists anywhere** —
`imageUrl` is unused plumbing on every DTO/type, `AppProperties.Storage` is dead S3-shaped
config with no client/consumer anywhere, `product_contribution` has no image column — this is
the one genuinely new piece of work in the whole revamp, not a redesign; (3) no shared `Card`/
`Panel` component existed — the bordered-card block was copy-pasted per feature.

**What was built (Step 1 of the sequence):**
- `design-system/Card.tsx`/`.css` — the one bordered-surface primitive, replacing four
  independent copies of the same rule (`family__panel`/`family__section`,
  `settings__section`, `scanpage__panel`, `alternatives__card`).
- `design-system/ProductImage.tsx` — the real-image/fallback branch, previously duplicated
  inline (with no `onError` handling) in both `ProductCard` and `ProductScreen`. Deliberately
  owns no sizing — the caller's existing box decides that via a `classNames` prop — so this is
  a zero-visual-diff refactor today (`imageUrl` is still always `null` from the API).
- `design-system/SearchBar.tsx`/`.css` — extracted from `AppShell`'s inline JSX, which
  rendered identical markup twice (mobile header, desktop top bar). Incidentally fixed a
  pre-existing duplicate-id/duplicate-label defect (both renders shared the literal id
  `global-search` simultaneously in the DOM).
- Migrated onto `Card`: `FamilyScreen`, `SettingsScreen`, `ScanScreen`, `AlternativesSection`.
  Migrated onto `ProductImage`: `ProductCard`, `ProductScreen`.
- Deliberately not built yet, no consumer needs them: `Panel`/`Section` (Card already covers
  the role), `PageHeader`, `Badge`, `Avatar`, `Tabs`, `Drawer`, `Modal`, `BottomSheet` — build
  each when its screen's own step in the sequence reaches it, not speculatively now.

**Verified:** `npm run typecheck` clean, `npm run test` 8/8 passing. No backend touched this
step, so no backend test run was needed.

**Git note (read before your next commit):** the working tree already held several prior
sessions' fully-tested, uncommitted work (catalogue import/ops-review/recommendation/activity
backend modules, and their frontend wiring) layered into the very files this step needed to
touch. Per explicit user decision, that work was committed alongside this step's own changes
rather than held back further — three commits, in order: `feat: catalog import, ops-review,
recommendation and activity modules` (backend+tooling+docs, 100% prior-session content),
`feat(ui): wire ops console, recommendation, and activity into the frontend` (prior-session
frontend, untouched by this step), `feat(ui): establish shared design system (...)` (this
step's actual work). The working tree is clean as of this entry.

**Steps 2, 4, 9, 10, 12, 13 — done, committed (2026-09-07).** Ran as six parallel agents on
disjoint file sets (backend image infra touches only `backend/`/`tools/`; each frontend
agent owned exactly one screen's files), each reviewed and verified before merging:
- **Step 2 — product image infrastructure**: new `product_image` table (V11, separate from
  `label_image` exactly as planned), `platform.storage.ObjectStorageClient` (real S3/MinIO
  client, credentials never leave the class), `ProductImageService` (idempotent-by-hash,
  server-generated storage keys, reuses existing `AppProperties.Limits` MIME/size config),
  `ProductQueryService` now batch-resolves real `imageUrl`, `OpenFoodFactsImageBackfillRunner`
  (opt-in `seed-catalog-images` profile, store-and-attribute not hotlink, resumable). **One
  real defect found and fixed on review**: the backfill runner's `User-Agent` header embedded
  a real personal email address that would have been sent to Open Food Facts' servers on
  every request — replaced with a project-level contact reference before merging.
- **Step 4 — ProductCard/Rails**: `object-fit: contain` for real photos (was `cover`, would
  have cropped a non-square pack), a fixed 320px viewport bug in `.pgrid` (two columns
  silently collapsed to one), allergen-badge shadow for legibility over a photo.
- **Step 9 — Family**: roster rows as nested `Card`s, one-letter avatar badges (no
  photo-upload feature exists), self/child visual distinction via border-style + accent bar
  (never a verdict/allergen color), equal-weight create-vs-join first-run choice.
- **Step 10 — Scan/Contribution**: manual-entry button de-emphasized to secondary (camera is
  the one primary CTA), the "reviewed before publication" promise promoted to a real trust
  callout, contribution form sectioned.
- **Step 12 — Settings**: regrouped into Account vs. "Privacy & data"; fixed the known
  incomplete ARIA tablist pattern on the auth switcher by mirroring `OpsQueueScreen`'s
  already-correct implementation.
- **Step 13 — Ops**: queue rows onto `Card`, added a status badge reusing `ProductScreen`'s
  provenance-badge color convention, surfaced `submittedAt`/`reviewedAt`/`reviewNotes` (were
  on the wire, never rendered), densified spacing (deliberately, per the brief — this is the
  one screen where that's correct).

Seven commits total for this batch (one per step, plus the backend one) — see git log.
Verified per-step: `npm run typecheck` clean, `npm run test` 8/8, and for the backend piece,
`mvn -o verify` 100 unit + 116 integration passing (Testcontainers, real Postgres, Flyway V11
applied cleanly).

**Steps 5–8 — done, committed (2026-09-07).** Home (utility vs. browsing zones, tinted
quick-actions so they don't read as product cards; deliberately no dashboard element, no
fake "recommended for you", no invented greeting — no backend/data-model support exists for
any of those three). Search (a real, honestly-labeled client-side-only "best data quality"
sort — the backend has no `sort` param, so this reorders the already-fetched page in memory
only; no "family fit" filter was built since no such field/param exists anywhere). Product
Detail (the first real ≥1024px two-column desktop layout — sticky image/identity left,
scrolling facts right — a pure wrapping change, the fixed information order and every aria
attribute are byte-identical to before). Compare (real `ProductImage` instead of raw
`CategoryArt` calls, centered per-column identity headers, clearer table lanes; deliberately
no value-vs-value "winner" highlighting — this app's tokens explicitly reject that judgment
ramp, D-004).

**Steps 3 and 11 — done, committed (2026-09-07).** App shell: found and fixed a real bug
(`.toplinks__item:hover` outranked `--active` on specificity, so hovering the current desktop
nav link silently lost its "you are here" styling), plus a touch-target and glyph-alignment
fix — the architecture itself was already correct, so this was targeted polish, not a rebuild.
Activity: a brand-new `/activity` screen and route, reached via a link from Settings (not a
6th bottom-nav slot, by design — see above), a plain grouped history (Viewed/Compared/
Decisions) with zero gamification, reusing `HomeScreen`'s exact query pattern and
`OutcomeCapture`'s own decision-label vocabulary rather than inventing a second one. One
inconsistency fixed on review: its image tile used `object-fit: cover`, missed the `contain`
convention established earlier in the sequence.

**Step 14 — done, committed (2026-09-07). All 14 steps of the UI/UX revamp are complete.**
The final pass found and fixed real, provable defects rather than rubber-stamping: an
undefined CSS custom property (`--text-primary`, never defined — only `--text` exists) in two
files silently falling back to an inherited value; a real WCAG AA contrast failure introduced
by this same revamp (`HomeScreen`'s `.qaction__sub` text on its own new tinted fills, computed
at ~4.3:1 against a 4.5:1 floor); three touch targets under the app's own 44px floor (the
shared `.chip` component used everywhere, `SearchScreen`'s sort-chip override, `SearchBar`'s
scan shortcut — the last one shared across the whole app via the mobile header and desktop
top bar); and an incomplete ARIA tablist on `ScanScreen` (styled like the pattern used
elsewhere but pointing at real route navigations with no shared tabpanel — replaced with the
correct `<nav>` + `aria-current="page"` pattern). No browser-automation tool exists in this
environment, so verification throughout was code-level (real box-width and contrast-ratio
arithmetic against `tokens.css`'s actual values), not a rendered check — stated plainly in
every report rather than implied otherwise.

## UI/UX revamp — final summary

20 commits total (`38201a5`..`37e4a96` — `git log` has the full list), spanning: reconciling
three prior sessions' uncommitted backend/frontend work, a shared design-system foundation
(`Card`/`ProductImage`/`SearchBar`), a full backend product-image pipeline (new table, real
S3/MinIO client, OFF image backfill), and a deliberate visual/structural pass over every
screen (Home, Search, Product Detail, Compare, Family, Scan/Contribution, Settings, Ops, a
brand-new Activity screen, the app shell), finishing with a global responsive/accessibility
audit. Every step was executed by a scoped, disjoint-file-set subagent, reviewed for
correctness/security/accessibility, verified (`npm run typecheck`/`test`, and for the backend
piece `mvn -o verify`), and committed individually — nothing was accepted on a subagent's
self-report alone. Three real defects were found and fixed during review that no agent
reported on its own accord: a personal email address embedded in an outbound HTTP header
(image-infra step), an `object-fit` inconsistency in the new Activity screen, and everything
listed above from the final pass.

**What did not change, by design, throughout:** no price/cart/checkout ever existed or was
added; the closed verdict vocabulary and its non-traffic-light color ramp (D-004); the score
never became the largest element or moved above the per-person verdict (D-003); Home never
became a dashboard (D-005); no fabricated data of any kind (images, reviews, ratings,
recommendations, personalization) was ever introduced — every "not built" gap in the original
brief (a "for your family" home-page recommendation section, server-side search filters
beyond category, a display-name greeting) was left unbuilt specifically because no real
backend/data-model support exists for it yet, not overlooked.

**Genuinely deferred, not started:** multi-image product galleries and an image verify/reject
review UI (schema already supports both — `is_primary`/`status`/`verified_by_user_id` exist
unused); running the `seed-catalog-images` backfill against live Open Food Facts (the
documented, ongoing OFF outage blocks this, same as the base catalogue import); a formal e2e
suite and an actual rendered-in-browser visual walkthrough (still no browser-automation tool
in this environment — every screen in this revamp was verified by code-level typecheck/test/
review only, never seen rendered).

---

**Previous entry (2026-09-06, Phase 6 hardening sprint).**

## Phase 6 hardening — tests for the three untested modules, 4 real defects found and fixed

**Objective:** the prior feature-completion sprint explicitly deferred dedicated tests for
`recommendation`, `activity`, and the ops-review additions to `capture`. This session wrote
that coverage and treated every failure as a real defect to investigate, not a test to
weaken — exactly per this session's own brief ("the goal is to discover real defects").

**Tests added** (30 new, all integration-level against real Postgres + the real HTTP
security chain, except where noted):
- `AlternativesServiceIT` (9) — cross-household denial, unknown product, the uncategorised-
  product authorization bypass (see defect below), no-candidates honest-empty-result, real
  allergen-safety differentiation (safer candidate surfaced, equally-unsafe one is not, a
  new-risk one is excluded outright), D-033's no-fabricated-nutrient-judgment guarantee,
  and deterministic ordering.
- `OpsReviewControllerIT` (11) — D-026 acknowledgment enforcement, allergen/nutrient code
  validation at submission (see defect below), anonymous and non-operator denial on every
  ops endpoint, the full submit→queue→approve lifecycle producing a real searchable
  provenance-correct product, double-approval/double-rejection rejected, reject requires a
  reason and creates nothing.
- `ActivityControllerIT` (10) — anonymous denial, account isolation (one account can never
  read another's recent activity), all 7 event types persist, invalid event type and
  missing/unknown product ids rejected, newest-first ordering, append-only semantics
  (duplicate submissions are not deduplicated — verified as the intended contract, not a bug),
  `BOUGHT_ALTERNATIVE` carrying both product ids through to the response.

**Defects found and fixed** (all four confirmed by a failing test before the fix, a passing
one after):

1. **P1 — a whole-number nutrient amount crashed ops-review approval.** A contribution's
   `nutrients` jsonb column round-trips a bare JSON integer (e.g. `500`, not `500.0`) back as
   a Java `Integer`/`Long`, not a `BigDecimal` — Jackson's untyped-`Object` deserialization
   only produces `BigDecimal` for a value with a decimal point. `ContributionService
   .toIngestCommand`'s hard `(BigDecimal)` cast threw `ClassCastException` on approval for
   any such contribution, an entirely ordinary submission (typing "500 kcal", not "500.0
   kcal"), permanently stuck at 500 with no recovery but a code fix. Fixed with a
   `toBigDecimal(Object)` helper that accepts any `Number`.
2. **P2 — an uncategorised product bypassed household-membership authorization in
   `recommendation`.** `AlternativesServiceImpl.findAlternatives` returned its "nothing to
   compare within" empty result *before* calling `HouseholdFitService.evaluate` — the one
   call that enforces the caller is actually a live member of the target household. A
   product with no category let anyone, member or not, get a (trivially empty, but
   incorrectly-authorized) `200` instead of the `403 NOT_HOUSEHOLD_MEMBER` every other
   household-scoped endpoint guarantees. Fixed by reordering: the authorization check now
   always runs first.
3. **P2 — contribution submission accepted allergen/nutrient codes with no reference-table
   check.** `categoryCode` was already validated against the real `category` table;
   `allergenCodes`/nutrient codes were not, so a bad code (typo or malicious) would sit in a
   `PENDING` contribution looking valid, then fail ops-review approval with an opaque
   `500` when the write hit the real foreign-key constraint (`product_allergen.allergen_code
   REFERENCES allergen(code)`) — permanently blocking that one contribution's approval.
   Fixed by adding `ProductQueryService.listAllergens()` (mirroring the existing
   `listCategories()`/`listNutrients()`) and validating both fields at submission time,
   the same way `categoryCode` already was.
4. **P2 — stale cross-account data in the frontend query cache.** `signOutLocally()` never
   cleared the TanStack Query cache. With `staleTime: 60_000`, a different account signing
   in on the same tab right after a sign-out (or after account deletion) could see the
   *previous* account's cached "recently viewed" products, household list, or ops-access
   check for up to a minute — the backend re-checks authorization on every request
   regardless, but the client must not display a previous identity's cached data to a new
   one. Fixed in `App.tsx`: the query cache is cleared whenever the signed-in account id
   changes (including to/from signed-out); a token refresh that keeps the same account id
   deliberately does not clear it.

**Also fixed, incidental to the review:** a misplaced Javadoc comment on `ProductQueryService`
(landed on the wrong method during the prior sprint's editing) was corrected while adding
`listAllergens()` next to it.

**Assessed and deliberately not built:** FR-011 (a correction/flag flow for products already
in the catalogue, distinct from the new-product contribution flow). No defect discovered
during this hardening pass makes it necessary, and this session's own brief is explicit that
new features are out of scope unless a defect requires one — left as a known, documented gap,
not silently dropped.

**Verification:** `mvn -o verify` — **204/204** (88 unit + 116 integration, up from 174).
`npm run typecheck` and `npm run test` (8/8) green. A `security-review`-style grep sweep
(`console.log`, `dangerouslySetInnerHTML`, `eval(`, `System.out`, hardcoded secrets) found
nothing. The pre-existing barcode-conflict mechanism (`trg_barcode_conflict`, already tested
in `ProductControllerIT`) was inspected, not re-tested — `ProductIngestionService` writes
through the exact same `ProductBarcode`/repository path that existing test already covers,
with no new code in between that could bypass the trigger.

**One accessibility defect found and fixed, one found and documented, not fixed:** the
ops-queue status filter used an incomplete ARIA tablist pattern (`role="tablist"`/`"tab"`
with no `role="tabpanel"`/`aria-controls"` linkage) — fixed in the new `OpsQueueScreen`.
The identical pre-existing pattern in `SettingsScreen.tsx`'s sign-in/sign-up switcher has
the same gap; left as a documented P3 rather than risk destabilising an already-shipped,
untouched component under this session's time budget.

---

**Previous entry (2026-09-06, feature-completion sprint).** Commit `3ef643a`.

## Feature-completion sprint — catalogue import, ops review, recommendation, activity

**Objective:** complete the major product features end-to-end and populate a real
development catalogue, per an explicit sprint brief (not a normal incremental task). Ran
solo (no parallel subagents) to avoid the multi-writer working-tree problem prior sessions
hit — see the historical note below, which this sprint's own git history confirms was
already resolved by the time this session started (`git status` was clean at the start).

**What was built** (full detail: `docs/decisions/decision-log.md` D-030..D-033; commit
`3ef643a`'s message):
1. **Catalogue import (P0).** `catalog.application.ProductIngestionService` — the single
   write path into the catalogue (D-014/D-015-compliant), used by both the importer and
   ops-review approval. `tools/data-import/fetch-openfoodfacts.mjs` +
   `catalog.seed.OpenFoodFactsSeedRunner` (gated behind a `seed-catalog` Spring profile)
   import real Open Food Facts India-market products (D-031). **893 products imported**,
   not the 5000+ target — Open Food Facts' public API experienced a sustained outage
   (`HTTP 503`/`401` across subdomains and endpoints, confirmed not specific to this
   session's request pattern) for essentially this entire session. The tooling is
   resumable (`--resume=true --start-page=N`) and the import is idempotent — re-running
   once the source recovers will top up toward 5000+ with no code change. See
   `tools/data-import/README.md` for the exact re-run steps.
2. **Ops-review console (P1).** `UserAccount.role` (`OperatorRole`), `identity.application
   .OperatorAccess` (published interface, mirrors `HouseholdAccess`), `ContributionService`
   gains `listQueue`/`getDetail`/`approve`/`reject`, `capture.api.OpsReviewController`.
   Frontend `features/ops/` (queue + detail/review screens), a Settings link shown only to
   operators. Live-verified end-to-end against the running instance: submit → approve →
   real searchable catalogue product with `dataQuality=VERIFIED`.
3. **D-026 resolved (D-030).** `PRODUCT_IMPROVEMENT` is a required terms-of-contribution
   acknowledgment on the contribution row (`contributor_acknowledged`), not a
   consent-ledger grant — extending the ledger to an account-scoped subject was judged
   disproportionate for a purpose with no personal data. Live-verified: submission without
   acknowledgment now `400`s.
4. **`recommendation` module (P1, new).** `AlternativesServiceImpl` ranks same-category
   candidates on real allergen-safety deltas and (today inert, D-024-ready) verdict deltas
   only — never an invented nutrient-direction judgment (D-033).
   `GET /households/{id}/products/{id}/alternatives`, frontend `AlternativesSection` on
   Product. Live-verified (200, honest empty result with no differentiating member/allergen
   data yet).
5. **`activity` module (P2, new).** `VIEWED`/`SCANNED`/`COMPARED`/`BOUGHT`/
   `BOUGHT_ALTERNATIVE`/`BOUGHT_NEITHER`/`SKIPPED`, scoped to the caller's own account only.
   `OutcomeCapture.tsx` now persists for real (loading/success/error/retry); `ProductScreen`/
   `CompareScreen` fire best-effort `VIEWED`/`COMPARED`; Home gained a real "Recently
   viewed" rail. Live-verified end-to-end (POST + GET /recent).

**Verification performed:** backend `mvn -o verify` (174 tests, unit+ArchUnit+Testcontainers
integration) green; frontend `npm run typecheck` and `npm run test` (vitest, banned-
vocabulary/i18n gate) green. Live-verified against the running dev instance (`curl`): auth,
contribution submit/reject-without-ack/approve, ops access/queue, activity record/recent,
household create, alternatives. **Not verified:** no browser-automation tool was available
in this environment, so no rendered-in-browser walkthrough was performed — flagged, not
claimed. No dedicated unit/integration tests were written for the three new modules
(`recommendation`, `activity`, ops-review additions to `capture`) — deferred to the
hardening phase per this sprint's own explicit instruction not to make comprehensive
testing the focus.

**Known, deliberate gaps, not hidden:**
- Catalogue is at 893 products, not 5000+, purely due to the Open Food Facts outage above.
- No correction/flag flow for already-catalogued products (FR-011) — distinct from the
  contribution-for-new-products flow, still `NOT_STARTED`.
- `recommendation`'s nutrient/component-difference dimension is deliberately not built —
  needs D-025's reviewed rule content first (D-033).
- No formal accessibility audit, no e2e suite, no load/performance testing — explicitly
  deferred to the dedicated hardening phase per this sprint's brief.

**Recommended next session:** if Open Food Facts has recovered, re-run the importer
(`tools/data-import/README.md`) to top up the catalogue toward 5000+ — that is now a
5-minute operation, not new engineering. Otherwise, this is a reasonable point to begin the
dedicated testing/hardening phase (unit/integration/E2E/security/accessibility/performance),
since the major product-feature gate is now substantially satisfied.

---

## Historical: two independent sessions landed in this same working tree
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

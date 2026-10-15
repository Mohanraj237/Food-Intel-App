# Current task

## Dev environment state (2026-09-20, ops-only session food-intel-app-82)

No feature work. This session's job is starting/stopping the local stack (frontend, backend,
Redis, Postgres) on request. Findings, for whichever session picks this up next:

- **Postgres (`ffi-postgres`) and MinIO (`ffi-minio`) are already up via Docker Compose** —
  `docker compose -f infrastructure/docker-compose.yml up -d` was not re-run, they were already
  healthy. Redis is opt-in (`--profile redis`) and was not started; `CACHE_TYPE` defaults to
  `caffeine`, so this is not required for a normal local run.
- **A stray backend jar is already running on `:8091`** (PID 23152, Temurin 21.0.12,
  `--spring.profiles.active=local --server.port=8091`) — leftover from an earlier side-by-side
  branch run (the exact scenario `docs/operations/operations-guide.md` describes for a second
  stack). Ports `8090` (standard backend) and `5173` (standard frontend) were confirmed free.
  This process was left running, not stopped, pending the user's direction.
- **A `mvn package` rebuild was attempted and stopped.** `backend/target/family-food-intelligence-0.1.0-SNAPSHOT.jar`
  on disk predates several backend commits since 2026-09-15 (search, recommendation, scoring
  packages all changed) — `spring-boot-maven-plugin:repackage` failed because the `:8091` process
  above holds that same jar file open (`Unable to rename ... .jar to ....jar.original`). The
  build was not retried against a different output path; no jar was rebuilt this session.
- No `.env` exists in the repo root, and none is needed for local dev — `application-local.yml`
  (the default Spring profile) already hardcodes matching DB/JWT/storage values for the Docker
  Compose services.

**Next step for starting the app cleanly:** decide what to do with the `:8091` leftover process,
then either rebuild the backend jar (recommended, since it's 5+ days stale) or run the existing
jar as-is on `:8090`, and start the frontend with `npm run dev` (`frontend/`, port 5173).

**Addendum, same session:** added `scripts/start-dev.ps1` / `scripts/stop-dev.ps1` so Redis is a
single explicit flag (`-WithRedis`) instead of two things to remember (the Compose `redis`
profile and `CACHE_TYPE=redis`) — see README.md "Running it". Backend/frontend PIDs are tracked
in git-ignored `.dev-stack.json` at the repo root so `stop-dev.ps1` can find and stop them (and
their child processes) without guessing. `docs/operations/operations-guide.md` and
`.env.example` cross-reference the new flag; no other file needed changing.

---

## Food Intel App build — Pre-flight IMPLEMENTED (2026-09-13/14, plan `you-are-a-senior-jolly-lemur.md`)

A full product-owner-approved implementation plan exists for the "Food Intelligence platform"
build (search filters/suggestions, real scoring via a transcribed rule set, compare, category
pages, watchlists + family cart + Fix My Cart, label OCR + receipt import, a grounded assistant,
My Foods/history/insights, a family hub with private nicknames, admin + scoring console,
food-first navigation, image galleries, and a set of visible UI fixes). The full plan — every
increment's backend/frontend design, hard guardrails, decision templates, environment commands,
and a reference appendix — is written at `C:\Users\mohan\.claude\plans\you-are-a-senior-jolly-lemur.md`
and should be read in full before continuing this work in a new session; it is written to be
followable by any model without re-deriving product decisions.

**Pre-flight complete, this session:**
1. Artifact cleanup — deleted untracked `frontend/e2e/screenshots{,-redesign}/` (57 MB),
   `frontend/e2e/results/`, `frontend/test-results/`, `backend/hs_err_pid*.log`/`replay_pid*.log`,
   `tools/data-import/fetch.log`; fixed `.gitignore`'s Playwright section (was anchored at
   `/e2e/...`, repo root, but the real directory is `frontend/e2e/...` — never matched; now
   correct, plus explicit ignores for screenshot dirs, results, and image-pipeline logs).
2. **Baseline commit** of the four prior sessions' uncommitted work (343 files, D-054 family page
   trends, D-055 consumer redesign, D-056 Redis cache, D-057 virtual threads, V14 access module).
3. **Two pre-existing test bugs found and fixed** while establishing the baseline (not caused by
   this session): `AccountErasureIT` used the shared adult `setProfile()` helper on a CHILD
   member, which `ProfileService` correctly rejects (`AGE_BAND_NOT_VALID_FOR_MEMBER`, 422) — added
   a `setChildProfile()` helper; `ConsentServiceIT.policiesArePublic` hardcoded "5 seeded
   policies" and an index-2 check, stale since V14 added two more consent purposes (7 total,
   `CHILD_FOOD_SUITABILITY` moved to index 3). Both were test-fixture bugs; the product behaviour
   in both cases was already correct.
4. **Locale bundle split (D-070)** — `locales/en-IN.json` (1317 keys) → eight files under
   `locales/en-IN/` (`search`, `product`, `compare`, `scan`, `ask`, `ops`, `family`, `home`,
   `common`) merged by a new `index.ts`; purely mechanical, key count verified equal before/after.

**Verified:** `mvn test` 420/420, `mvn verify` (failsafe integration-test) 128/128, `npm run
typecheck` clean, `npm test` 75/75 (vocabulary gate included), `npm run build` clean — all after
every change in this list, not just at the end.

**Migration ledger (reserved numbers, do not renumber — plan Appendix F):**

| # | Name | Increment | Status |
|---|---|---|---|
| V15 | `catalog_enrichment` (nova, diet flag, additive, category_alias, unknown_barcode, nutrient rows, brand_alias seeds, product_image gallery columns) | 1 (+1b) | not started |
| V16 | `scoring_v2_engine` (algorithm, roles, bands, category_standard_map, product_score columns, reference_standard, rescore_run, product_search_facts view) | 1 | not started |
| V17 | `rule_set_v2_draft` — **blocked until the product owner signs `docs/data/scoring/review.md`** | 1 | not started |
| V18 | `rule_set_v2_activate` (retire v1, activate v2 — one migration) | 1 | not started |
| V19 | `lists_family_cart_aliases` (+ `CART_SWAP`) | 4 | not started |
| V20 | `ai_capture` (rate_limit_bucket shape, receipt tables, RECEIPT_PROCESSING purpose, limits) | 5 | not started |
| V21 | `admin_role_and_rule_immutability` | 8 | not started |

**Increment 0b — IMPLEMENTED (2026-09-14).** Visible UI fixes, frontend-only:
- **Fluid desktop width (D-074):** `tokens.css` `--shell-max: 1240px` → `min(100%, 1800px)`. The
  shell (`.shell__main`, `.shell__topbar-inner`, and `.layout-dashboard` via
  `--layout-dashboard-max: var(--shell-max)`) now fills the window minus its gutter up to 1800px,
  instead of sitting in a fixed 1240px column with large dead margins on a 1440–1920px monitor.
  `--layout-form-max`/`--layout-reading-max`/`--layout-list-max` deliberately untouched — forms
  and prose keep their own readable measure.
- **Search focus ring:** `SearchBar.css` — the brand halo (`0 0 0 3px var(--brand-subtle)`) now
  only applies on `.searchbar:has(:focus-visible)`, not unconditional `:focus-within`; a plain
  border-colour change happens on any focus. Verified via computed-style check (not just visual
  inspection — see the design-system doc v2.1 §8 note): the field never shows any focus styling
  while genuinely untouched (no autofocus exists anywhere in the app), which was the literal
  complaint. Caveat found and documented: Chromium's own `:focus-visible` heuristic treats a text
  input's focus as "visible" on *either* pointer click or keyboard tab (by design, for typing
  fields), so the search pill specifically still shows the full halo on a tap — correct,
  accessible behaviour, not a bug; the gated pattern is written for reuse where pointer/keyboard
  focus genuinely should differ (buttons, cards, chips).
- **`CategoryGrid`** (`design-system/Rails.tsx`/`.css`) replaces `CategoryRail` (deleted, sole
  caller was `HomeScreen`): a wrapped `repeat(auto-fit, minmax(…))` grid, every category visible,
  no horizontal scroll. `auto-fit` (not `auto-fill`) so the fixed 13-category set never leaves a
  dead trailing gap at very wide viewports.
- **`ProductCarousel`** (new, used inside `ProductRail` — the public API every caller keeps):
  previous/next arrow buttons shown only at ≥1024px (CSS-hidden below that; native swipe stays
  the only and sufficient control on touch), `scroll-snap-type: x mandatory` (was `proximity`),
  `scroll-behavior: smooth` dropped to `auto` under `prefers-reduced-motion`, `ResizeObserver`/
  `matchMedia` feature-detected so an older browser degrades to plain scroll rather than crashing.
- Added `action.previous`/`action.next` locale keys (`common.json`).
- New `design-system/Rails.test.tsx` (7 tests): CategoryGrid tile rendering + link targets,
  ProductRail/carousel item rendering, arrow enabled/disabled state at each scroll extreme
  (dimensions stubbed via `Object.defineProperty` since jsdom does no real layout), click-to-scroll
  amount, loading-skeleton state.
- `docs/design/design-system.md` bumped to v2.1 (§7 layout, §8 accessibility) recording all of
  the above and the focus-ring nuance discovered.

Verified: `npm run typecheck` clean, `npm test` 82/82 (75 baseline + 7 new, vocabulary gate
included), `npm run build` clean; visually confirmed with a real Playwright run against the
dev server at 390/1280/1440/1920px, light and dark — no dead margins beyond the gutter, all 13
categories visible without scrolling, carousel arrows render and respond correctly (verified via
computed style, not just screenshot) at each scroll extreme.

**Increment 0 — evidence pack written, IMPLEMENTED, awaiting review (2026-09-14, D-058).**
Docs only, no code, no migration. `docs/data/scoring/`:
- `evidence/source-001-who-searo-npm-2017.md` — the complete WHO SEARO Nutrient Profile Model
  2017 threshold table, all 18 categories, transcribed by rendering the source PDF's own pages
  as images (pdftotext badly garbles this table's multi-column layout) and reading them
  directly — not from the garbled text extraction. Cross-checked against Pandav et al. 2021's
  independent reproduction of the same table structure.
- `evidence/source-002-fssai-inr-draft-2022.md` — the complete FSSAI draft Indian Nutrition
  Rating Schedule III (all six tables: baseline reference values, solid/liquid point bands,
  capping rules, the score formula, the star bands) plus the HFSS definition and a Schedule-IV
  exemption excerpt, transcribed the same way from the USDA GAIN report's reproduction of the
  scanned Gazette notification (the notification's own appendix is a scanned image, not
  extractable text). **The regulation is a draft** — comment period closed Nov 2022, not
  finalised; the evidence file states this plainly and flags the implication for `EDIBLE_OIL`
  and parts of `STAPLES`, which the draft's own Schedule IV exempts from a rating entirely.
- `evidence/source-003..006` — the four already-seeded standards' quoted scope (WHO free
  sugars 2015, WHO sodium 2012) reused from the V1 migration's own citations; ICMR-NIN DGI
  2024 **flagged unverified** (its PDF would not render as readable text through the tools
  available this session — nothing from it is used in a proposed rule); the NOVA classification
  (Monteiro et al., FAO 2019) fetched and quoted directly for its four group definitions.
- `scoring-methodology.md` — the engineering model: the INR points algorithm as a `POINTS_NET_V2`
  strategy (band lookups, not linear interpolation, distinct from today's `WEIGHTED_MEAN_V1`),
  how missing baseline vs. modifying data is handled, and which numbers are cited thresholds vs.
  this project's own display conventions (the 0–100 rescale, the basket mean — same status as
  today's `gradeFor` letter bands).
- `rules-v2.md` — the actual proposed rule content: a 13-category → WHO-SEARO/FSSAI-INR mapping
  (several buckets flagged heterogeneous — `BEVERAGES`, `DAIRY`, `PROTEIN_FOODS`,
  `SAUCES_SPREADS`, `STAPLES`, `BREAKFAST_CEREAL`), the INR score-component bands for solid and
  liquid foods, and ~25 proposed `scoring_rule` rows across `REDUCE_ADDED_SUGAR`,
  `REDUCE_SODIUM`, `REDUCE_SATURATED_FAT`, `INCREASE_FIBRE`, `INCREASE_PROTEIN` and
  `GENERAL_BALANCED_EATING`. **No rule is proposed for `AVOID_TRANS_FAT`** (neither source has a
  trans-fat column) **or for `MALT_DRINK`** (the draft's own liquid/solid split doesn't fit a
  malt-drink powder sold per 100 g) — left honestly blank rather than guessed, per D-025.
  Every non-quoted judgment call (a category mapping, a footnote-based substitution, a
  display-convention cut-off) is marked `[interpretation]` inline.
- `review.md` — the gate itself: an explicit checklist of eleven decisions the reviewer must
  make, and the exact approval line (`APPROVED by <name> on <YYYY-MM-DD>`) this session or any
  future one must see before writing `V17`/`V18`.

Decision **D-058** records accepting the draft FSSAI standard and adding `WHO_SEARO_NPM_2017`.

**Hard stop:** nothing in Increment 1's `V17`/`V18` may be written until the product owner has
read `docs/data/scoring/review.md`'s checklist and signed the approval line. **Next step:**
either (a) the product owner reviews and signs `review.md`, after which Increment 1 (engine v2 +
catalogue enrichment + persisted scores) begins, or (b) proceed with Increment 1's non-blocked
parts now — the V15/V16 schema, the `PointsNetAlgorithm`/`RescoreService` engine code, and the
catalogue enrichment tooling all stand on their own and do not need `rules-v2.md` approved first;
only `V17`/`V18` (seeding and activating the actual rule content) are gated.

---

## Redis caching + virtual threads — IMPLEMENTED, committed 2026-09-13/14 in the Food Intel App build's baseline commit (D-056, D-057)

Product owner asked for Redis caching and deliberate virtual-thread use. The request conflicts with
D-007, so **D-056** supersedes D-007 for caching only; rate limiting stays DB-backed. **D-057**
records the virtual-thread model. Both were first written as D-055/D-056, then renumbered after
the consumer-redesign session claimed D-055 at the same time.

**Backend.**
- New `platform.cache`: provider chosen by `familyfood.cache.type` (caffeine by default, redis,
  none); Redis failures fall back to the database and trip a 30 s bypass; health reports
  DEGRADED, never DOWN; keys are `ffi:<cache>:<version>::<id>`, where the version hashes the
  DTO shape and the applied Flyway version.
- `catalog`: caches `catalog.product` (30 min, evicted by `attachImage`) and `catalog.categories`
  (1 h). `getProductByBarcode` is deliberately uncached (AC-4.4).
- `CachingBoundaryTest` keeps caching to the catalogue: nothing about a person is cached.
- `PasswordHasher` runs Argon2 on a max(2, cores/2) platform pool, off the virtual threads.
- `micrometer-java21` adds virtual-thread pinning metrics.
- Dev JVM diagnostics come from the pom property `spring-boot.run.jvmArguments`.

**Verified.**
- `mvn test`: 420 green (387 before, plus 33 new).
- `RedisCachingIT` (6) and `RedisUnavailableIT` (2) green; the full IT suite is in the session log.

**Open, flagged, not changed.**
- Sign-in and registration hash inside their transactions, holding a pooled connection. This is
  auth-sensitive and needs its own review.
- `/actuator/prometheus` is `denyAll` in `SecurityConfig`, so the new metrics can't be scraped yet.
- Whether virtual threads are involved in the 21.0.7 `0xC0000005` crashes is unproven. The
  experiment that settles it is a burst of ~15 requests on 21.0.7 with
  `spring.threads.virtual.enabled=false`.

**Machine.** It runs at about 92% of its commit limit, and C: is 100% full, so the page file
cannot grow. All seven `hs_err` logs are native-memory exhaustion (see the operations guide,
"JVM runtime"). Run Maven with small heaps.

## Consumer redesign — IMPLEMENTED, committed 2026-09-13/14 in the Food Intel App build's baseline commit (D-055)

Product owner brief: rethink the app as a launchable consumer food product (identity, IA,
design system, feature UX, states, responsiveness), audit the e2e screenshots first, then
implement across the whole application.

**Direction.** `docs/design/design-system.md` v2 and D-055. Verdict/allergen semantics, the
honesty rules and every accessibility floor are unchanged; the skin, the navigation and the
consumer surfaces are new.

**What changed (frontend only).**
- Foundation: `tokens.css` (new palette, radii, shadows, avatar tints, large-text scale),
  `base.css` (self-hosted font faces, page header, segmented control, form primitives),
  `index.html` (font preload, theme colour), `public/favicon.svg` + `public/fonts/`.
- New primitives: `Icon`, `Logo`, `PageHeader`, `Avatar`, `Badge`, `Toast` (+ `useToast`).
  Restyled: `Button` (pills, `.icon-btn`), `Card`, `SearchBar` (pill with clear + scan),
  `States` (icon empty/error), `RowGroup` (leading icons), `BottomSheet`, `ProductCard`
  (stretched link + sibling Save), `Rails`, `ProgressTile` (icon + goal bar), `LogSheet` (icon
  tiles, toast on save), `VerdictChip`, `AllergenFlag`, `RecommendationCard`,
  `FamilyMemberCard`/`HouseholdMemberCard` (Avatar), `DayNavigator`.
- Shell: `AppShell` — brand row + search pill (hero search on Home), bottom nav with SVG icons and
  a raised Log disc, desktop top bar with icon links, account avatar, `ToastProvider`.
- Routes (`App.tsx`): `/` = Home (`/food` redirects), `/saved`, `/history` (`/food/recent`,
  `/activity` redirect), `/insights` (`/next`, `/next/insights` redirect); `NextScreen` and
  `NotBuiltYet` deleted. Onboarding finishes on `/`.
- Screens rebuilt: Home (+ `TodayGlance`, compact `FoodReadinessCard`), Search, Product (Save /
  Compare / Share, avatar fit rows, "At a glance" per-100 tiles, collapsed full label,
  provenance badge), Today, Scan (viewfinder), You (identity card, records, sharing, consent
  for self + children only), History, Insights, Saved (new; `lib/saved.ts`).
- Screens restyled/adopting `PageHeader`/`Avatar`: Family hub, Member view, Compare, Goals,
  Habits, Trackers, Meals (history/log/detail), Sharing, Household settings, Profile,
  Onboarding, Consent, Legal, 404. Gated Health shelf / Ask: consumer "Coming soon" copy.
- Locale: ~80 new/changed keys; `next.*` dev keys removed; vocabulary gate green.
- Fixes: `lib/auth.ts` `restoreSession` keeps the token on transport failure;
  `ConsentPanel` shows self + children, quiet note on a child's 403.
- Tooling: `e2e/helpers.ts` honours `E2E_SHOTS` so a run can capture beside the old set.

**Verified.** `npm run typecheck`, `npm test` (75/75), `npm run build`. Chromium renders at
390×844 and 1440×900 of `/`, `/search`, `/scan`, `/product/:id`, `/compare`, `/saved`,
`/history`, `/today`, `/goals`, `/habits`, `/trackers/WATER_ML`, `/meals`, `/meals/new`,
`/family`, `/family/:id`, `/insights`, `/you`, `/you/sharing`, `/you/household`,
`/you/profile`, `/you/appearance`, `/legal/privacy`, a 404, signed in as Raghav and signed out,
plus dark theme on Home/Product/Today; no horizontal overflow on any route. Journey suite
captured into `frontend/e2e/screenshots-redesign/`.

**Open / not done.**
- `npm run lint` still has no ESLint config (pre-existing).
- `NotificationsPanel` and `NotificationPreferencesSheet` are unreachable now (their only entry
  was the deleted hub); kept behind their flag for a future notifications route.
- Saved products are device-local; account sync is on the backlog.
- Product images from Open Food Facts are sometimes a photo of the barcode rather than the pack
  (data, not UI); an image review flow stays on the backlog.
- The e2e audit specs (`journeys.spec.ts` etc.) still list `/food` and `/next`, which now
  redirect; they run but could be pointed at the new routes.

## Family page: trends, goals, family summary — IMPLEMENTED, committed 2026-09-13/14 in the Food Intel App build's baseline commit (session food-intel-app-43)

Product owner asked for an insightful, visual Family page, family summary included. That is recorded as **D-054**, and spec §4.4 and §6.7 have been amended.

**Backend.** `GET /households/{id}/overview` now adds:
- per card: `week` (7 household days per metric), `goals`, `habitsWeek` and `manageScopes`;
- top level: `week`, `mealsAwaitingConfirmation` and `summary`.

It still makes one observation read per member:
- `GoalService.progressFrom` measures goals from that read.
- `HabitService.daySummaries` returns the habit days, and `daySummary` now delegates to it.
- `FamilySummaries` builds the summary from the finished cards only.

The access log gets the same rows per visit as before (measured: 8 old, 8 new).

**Frontend.**
- New components: `WeekStrip`, `DayNavigator`, `GoalMeter` (shared with the goals list), `FamilySummaryPanel` and `LatestUpdates`.
- `FamilyMemberCard` redesigned, and `MemberView` gets weekly charts and goals.
- Fixes: tracker links now use metric codes, units read "2.2 L" rather than "2200 ml", blood pressure shows as "132/84", habits are no longer dropped, no duplicate chips or bullets, a child's meals show as a count (UXD-08), and "2 of 1 done" can no longer appear.

**Seed.** Habits are now created 21 days back, because a habit born at seed time disowned its week of completions.

**Verified.**
- Backend: `mvn test` (387, ArchUnit included) and `FamilyOverviewIT`.
- Frontend: `npm test` (75, with the vocabulary gate), typecheck, `npm run build`.
- End to end: `e2e/family-insights.spec.ts` passes 10/10 on desktop and mobile against a re-seeded DB.

**Open.**
- WEEKLY habits count as due every day, in both `daySummary` and `daySummaries`. This predates this work.
- LATEST ties on the same `effectiveAt` are resolved differently by `DashboardDays` (it picks the later `recordedAt`) and by `latestByMetric` (database order).
- `npm run lint` still has no ESLint config.

## W1 — Wellness Foundation, in progress (2026-09-12, orchestrator session food-intel-app-65)

The expanded "individual + family" brief was reviewed against every prior decision:
**D-042** (full review), **D-043..D-053**, architecture **v2** (`docs/architecture/wellness-platform-architecture.md`
— feature map §12, dependencies §13, roadmap W1–W6 §14). Wellness phases are called **W1–W6** to avoid the
collision with the food product's Phase 0–7.

W1 code is written and (as of 2026-09-13/14, the Food Intel App build's baseline commit) committed: V14, new `access` module (one HealthAccessPolicy,
grants with VIEW/MANAGE, caregiver delegation, access log), `experience` module (tiers/capabilities),
wellness/goals/habits/nutrition/insights refactored onto the policy, meal-read leak fixed, tracking consent
purposes enforced, D-047 goal guardrails, household time zone, AuditService transaction fix. Being finished
by parallel agents with disjoint file ownership (brief: session scratchpad `W1-BRIEF.md`):
backend-core (compile + unit tests + ArchUnit), experience/dashboard (`/me`, `/today`, `/overview`),
datarights (health data in export/erasure), QA (privacy ITs + security review), frontend (new files only:
sharing, access log, onboarding, lib clients), and three design docs (UX spec, insights engine W3/W5, W2 tracking).

**Coordination rules that matter for any session in this tree:** every Maven run takes the mutex dir
`C:\Users\mohan\AppData\Local\Temp\ffi-mvn.lock`; V12/V13/V14 are frozen once applied; the peer session
`food-intel-app-3b` owns the existing frontend files and the running backend jar on :8090; V14 must be
applied to the dev DB together with a jar swap (the old jar queries `metric_visibility`).

**W1 exit gate (§14):** policy matrix tests green; meal-leak regression IT; consent enforced on every
tracking write; child band mismatch refused; erasure/export include all health data; every dashboard number
traceable to observations; full unit + IT regression green.

---

## Where the previous session stopped (2026-09-12, earlier)

**Product scope changed deliberately** to an individual + family wellness platform.
Recorded as **D-034**–**D-040** in `docs/decisions/decision-log.md`; target architecture in
`docs/architecture/wellness-platform-architecture.md`. Safety boundaries were explicitly
*not* relaxed — D-034 restates them.

**Wellness Phase 1 is delivered and green:**

- `V12__wellness_foundation.sql` — `household.is_individual` (D-035), `health_observation`
  (one time-series table for every future tracker), `metric_visibility` (default-closed,
  partial unique index on live grants).
- `com.familyfood.wellness` — `MetricCode` (28 metrics, each declaring category, canonical
  unit and age class), `WellnessService` (age gate + visibility + audit), REST surface at
  `/api/v1/wellness`.
- Tests: 34 taxonomy + 13 service (backend suite 149 green); 25 live-API checks in
  `scratchpad/audit/verify-phase1.js`.

## Wellness Phase 2 (Tracking) — delivered 2026-09-12 (session food-intel-app-3b)

Backend (then handed to food-intel-app-65, now refactored onto the W1 access policy):
`V13__nutrition_goals_habits.sql`; modules `nutrition` (meal, items with snapshot nutrition,
portions as fractions), `goals` (SUM/LATEST aggregation, `dataAvailable`, no score),
`habits` (N-of-M consistency only, no streaks), `insights` (derived on read, sufficiency-gated,
evidence on every insight). **D-041** resolves family meals vs D-002: derive only for self + a
guarded child; another adult confirms their own share; creator-only edit/delete withdraws every
derived share.

Frontend (all under `frontend/src`):
- `/today` — totals (only accumulating metrics are summed; "Not logged", never 0), quick log,
  meals today (pending-share prompt), goals, habits, weekly insights.
- `/meals/new`, `/meals/:id` — log once, apportion by fraction; D-041 shown before and after
  saving; per-share nutrition gated on `visibleCategories` (other adults and non-guardians see
  "private"); creator-only delete.
- `/goals` — goals + habits for self or a child. Child: water/steps/sleep only, never AT_MOST
  (D-017/D-036, enforced server-side as D-047). Adult energy: no AT_MOST (D-047). Habit history
  grid with backfill; no streak/badge vocabulary anywhere.
- `/family` — `FamilyWellness`: meals this week, shares waiting for you, one card per member
  (numbers for self/guarded child; "private" or shared categories for other adults), never ranked.

Verification: live Playwright suites in the session scratchpad `audit/` — `verify-p2ui.js`
(54/54), `verify-p2family.js` (32/32, two real adult accounts + child); backend unit suite 348
green; scoped typecheck + production bundle green.

**Next frontend step, once the W1 jar is deployed (V14 + jar swap together):** switch Today to
`GET /members/{id}/today` and FamilyWellness to `GET /households/{id}/overview` (let `canLogNutrition`
drive MealLogScreen's portion note), show `TrackingConsentPrompt` on `consent_required` in QuickLog,
GoalForm, HabitForm and MealLogScreen, and render `nutritionWithheld` with ShareNutrition's "private" line.
A planned handover of these files to food-intel-app-65 was cancelled when that session stopped. Its
partial frontend files (`lib/{access,me,dashboard}.ts`, `features/{sharing,onboarding,trackers,log,you}/**`)
are unintegrated and make full `tsc -b` fail.

**Environment:** HotSpot 21.0.7 (Oracle jdk-21 and IntelliJ JBR) crashes with 0xC0000005 and no
hs_err under ~15 concurrent requests on this machine; Temurin 21.0.12 (bundled with SonarLint in
`~/.vscode/extensions/sonarsource.sonarlint-vscode-*/jre/`) does not. Run the backend on 21.0.12.

Open: the server clock is UTC, so goal/habit "today" runs 05:30–05:30 IST and ticking a habit
00:00–05:30 IST returns 400 (household time zone is part of W1). `npm run lint` has never had an
ESLint config. `ProductScreen` links to `/product/:id/report`, which has no route.

# Frontend Implementation Workstreams — Ownership Matrix

- **Version:** 1.0 · **Date:** 2026-09-12
- **Authority:** this is the authoritative ownership matrix for the parallel implementation of `docs/plans/frontend-wellness-platform-plan.md` ("the plan"). Every later agent working on `family-food-intelligence/frontend/src` is pointed at this document, not at the plan's §18 alone, to know exactly which files it may touch.
- **Grounding:** every file path, component name, and current-behavior claim below was verified by reading the actual source in `frontend/src` on 2026-09-12 (not inferred from the plan). Where the plan's own text disagrees with what the code actually does, that is called out explicitly rather than silently corrected.
- **Run order:** Foundation (a separate, earlier phase not detailed here beyond §2–§3) lands first and is the *only* agent that may touch the frozen files in §2. C1–C5 below run in parallel after Foundation lands, each strictly inside its own file list in §1.

---

## 0. Corrections against the plan's starting point

The task that produced this document proposed five workstreams as a starting draft. Reading the actual code surfaced six corrections, applied throughout §1:

1. **`features/today/InsightList.tsx` is physically inside C1's `features/today/**` glob but is explicitly C4's to rebuild** (into the `RecommendationCard` feed). C1's glob is therefore `features/today/**` **minus** `InsightList.tsx`/`InsightList.css`. Stated as a hard file-level exception in the C1 row, not left implicit.
2. **`GoalProgressList.tsx` and `HabitList.tsx` are, today, single shared components already consumed by both Today (`compact` mode) and Goals/Habits (full mode)** — `TodayScreen.tsx` imports both directly from `features/goals/`. The starting draft's split ("C1 owns a read-only summary, C4 owns the full screens") does not match this reality without a concrete change. §4.1 below resolves this: C1 stops importing `features/goals/*` from Today at all and gets a new, C1-owned, zero-query summary component instead. This is the most important boundary resolution in this document — see §4.1.
3. **Two folders that exist in the repo were not assigned to any of the five workstreams**: `features/consent/ConsentPanel.tsx` and `features/settings/SettingsScreen.tsx` (the current `/settings` screen, target `/you`), plus `features/activity/ActivityScreen.tsx` (target `/food/recent`). All three are assigned below (§1) with rationale — they are not left ambiguous.
4. **The plan's sitemap (§3) states `/family/:memberId/profile` and `/you/profile` are "exists (`ProfilePanel`), routed."** This is not accurate against the current code: `ProfilePanel.tsx` exists and is fully built, but it is reached **only** via `FamilyScreen.tsx`'s internal `activeProfileMember` component state (`app/../features/household/FamilyScreen.tsx:292,307-315`), not via a URL route. Neither path is currently routed at all. C1 must make `ProfilePanel` directly routable (read `memberId` from `useParams`, not only from a parent-supplied prop) before either route can work. See §3.
5. **`FamilyScreen.tsx` today contains both** the household-setup UI (`NoHouseholdView`, `MembersList`, `InviteAdultPanel`, `AddChildPanel` — invite/add-child/leave/remove/erase) **and** the wellness overview/roster (`HouseholdOverview`, `FamilyWellness`) in one file, routed at `/family`. The plan wants `/family` to become the redesigned warm Family hub (§18 Journey 3.1) and the household-setup UI to move to `/you/household` (§18 Journey 3.5). This is a real extraction task, not a rename — assigned to C3, with a new stub file Foundation must pre-create (§3).
6. **`useHouseholdOverview.ts`'s per-member fan-out is a different domain than `FamilyWellness.tsx`'s fan-out**, even though the plan's gap list (G-01) mentions both under one "fix." `FamilyWellness.tsx`'s `OwnRecords`/`SharedCategories` fan out to *wellness* endpoints (`listObservations`, `goalProgress`, `listHabits`, `visibleCategories`) and **are** fixable by switching to the single `GET /households/{id}/overview` call, per §11 of the plan. `useHouseholdOverview.ts` fans out to *profile and consent* endpoints (`getProfile`, `listGrants`) to build a "household readiness" card (shared goals, household allergens) — a different domain the overview endpoint does not carry fields for. There is no confirmed single-call replacement for that second fan-out. This is flagged as an **open question for C3**, not silently resolved — see §5 (C3 checklist) and the note in §1.

---

## 1. The five parallel workstreams

| | C1 — Today, Individual Mode, Profile, Log | C2 — Food discovery, product decisions, meals & apportioning | C3 — Family Mode, member views, sharing/caregiving | C4 — Goals, Habits, tracker history, insights/recommendations | C5 — Backend-dependent shells (health shelf, notifications, Ask) |
|---|---|---|---|---|---|
| **Plan journey** | §18 Journey 1, §7 | §18 Journey 2 | §18 Journey 3 | §18 Journeys 1.3/1.4, 4.1 | §18 Journey 4.2–4.4, §10.3 |
| **Owns (exact paths)** | `features/today/**` **except** `InsightList.tsx`, `InsightList.css` (see §0.1); `features/household/ProfilePanel.tsx`; `features/settings/SettingsScreen.tsx` and its `.css` (**correction §0.3** — the `/you` hub rebuild; owned here because Profile/Individual-identity is C1's domain and `/you/profile` lives inside it); `features/consent/ConsentPanel.tsx` (**correction §0.3** — the in-context `ConsentMoment` consumer for Journey 1.5); `lib/dashboard.ts` (verify/extend types only — see note below); `lib/wellness.ts` (**delete** the dead `listShares`/`share`/`unshare`/`VisibilityGrant` exports at lines 47-53, 107-127 — confirmed dead, target `/wellness/members/{id}/shares` does not exist) | `features/home/**`, `features/search/**`, `features/product/**`, `features/compare/**`, `features/scan/**`, `features/meals/**`; `features/activity/ActivityScreen.tsx`/`.css` (**correction §0.3** — target route `/food/recent`, food-discovery-adjacent); `lib/nutritionLog.ts` (integration only — do not change its exported contract, other workstreams read meal data through it) | `features/household/FamilyScreen.tsx`, `FamilyWellness.tsx`/`.css`, `HouseholdOverview.tsx`/`.css`, `useHouseholdOverview.ts`; `features/sharing/**` (styling/copy pass only — see below); new `features/family/MemberView.tsx`; new `features/household/HouseholdSettingsScreen.tsx` (**new**, extraction target — see §3) | `features/goals/GoalsScreen.tsx`, `HabitList.tsx`, `GoalProgressList.tsx`, `goalOptions.ts`, `Goals.css`; `features/today/InsightList.tsx`/`.css` (**exception carve-out from C1's glob**, rebuilt into the `RecommendationCard` feed); new `features/habits/**`; new `features/trackers/**`; new `lib/recommendations.ts` (**added — see §0.3/§4.5**, the plan's §10.2 composition layer, not in the original draft's file list); `lib/goals.ts`, `lib/insights.ts` (integration only) | new `features/health-shelf/**`, `features/notifications/**`, `features/ask/**`, new `features/next/NextScreen.tsx` (the `/next` shell — see §3); new `lib/healthShelf.ts`, `lib/notifications.ts`, `lib/ai.ts` |
| **Must NOT touch** | `features/goals/**` (any file), `features/trackers/**`, `features/family/MemberView.tsx` | `features/household/**`, `features/goals/**`, `features/sharing/**` | `features/goals/**`, `features/today/**`, `features/product/**` | `features/today/TodayScreen.tsx` (only consumes C4's data shape, per §4.1), `features/household/**` | anything outside its own new folders — C5 has no existing code to inherit |
| **Priority (plan §17)** | Phase 1 | Phase 1–2 | Phase 1 (+ Phase 0 routing/copy on sharing) | Phase 2–3 | Phase 2 shell → Phase 4/5 real |

**Note on `lib/dashboard.ts`:** the plan's §10.2 describes this as needing "small additions... to surface `mealsAwaitingConfirmation`, `habits`, `canLogNutrition`." Reading the file (as of 2026-09-12) shows **these fields already exist** on `MemberToday` and `OverviewMember` (`mealsAwaitingConfirmation`, `habits: HabitsDay | null`, `trackingConsentActive`, `canLogNutrition`). C1's actual work on this file is therefore verification (confirm the fields the plan wants are present, which they are) and, if a genuine gap is found during implementation, a narrow type addition — not a rewrite. Do not re-derive these fields client-side; they are server truth already typed.

### 1.1 C1/C4 boundary — resolved explicitly (required by the task)

The exact split, file by file:

| File | Owner | What it is for |
|---|---|---|
| `features/goals/GoalProgressList.tsx` | **C4** | The full goal list: progress line, **End** action, `ConfirmSheet`. Used only by `/goals` and (parameterized) `/family/:memberId/goals`. Its existing `compact` prop (shorter empty state, no End button) stays for internal reuse *within C4's own screens* if useful, but **Today no longer imports this component at all.** |
| `features/goals/HabitList.tsx` | **C4** | The full habit list: mark-today, 14-day history strip (rebuilt per G-05), edit, archive/reactivate. Used only by `/habits` and `/family/:memberId/habits`. Same rule: its `compact` prop may stay for C4's own internal reuse, but **Today no longer imports this component at all.** |
| `features/goals/goalOptions.ts` | **C4** | Metric/direction/period option lists for the goal form. |
| **New: `features/today/GoalHabitSummary.tsx`** | **C1** | A read-only, zero-query summary tile for Today's "what this person is working towards" section. Props come straight off the `/today` response C1 already fetches: `{ goals: GoalProgress[]; habits: HabitsDay \| null; goalsHref: string; habitsHref: string }` (types from `lib/dashboard.ts`/`lib/goals.ts`, already defined, no new endpoint). Renders plain progress lines (reusing the existing text-formatting logic pattern from `GoalProgressList`'s `GoalLine`, but as new, independent code — **do not import `GoalProgressList`/`HabitList`/`goalOptions.ts` from this file or from `TodayScreen.tsx`**) and a single "See all" link to `goalsHref`/`habitsHref`. No End/archive/mark-today actions live here — those are C4's screens, one tap away. |

**Why this is the right cut, not just a naming exercise:** it also fixes a live defect. Today's server response already carries `goals`/`habits` fields (§ note above), so Today rendering them directly — instead of `GoalProgressList`/`HabitList` firing their own `['goals','progress',memberId]`/`['habits',memberId,...]` queries — removes a redundant fetch pair on every Today load and keeps Today's "one source per screen" rule (plan §11) intact for goals/habits the same way it already must be for the metrics list. C4's full screens keep their own queries because they need mutations (end goal, mark habit done, edit, archive) that a read-only summary has no reason to carry.

**Action for C1:** delete the two `<GoalProgressList memberId={memberId} compact .../>` and `<HabitList memberId={memberId} compact .../>` call sites in `TodayScreen.tsx` (currently lines ~208-221) and replace with `<GoalHabitSummary goals={...} habits={...} .../>` sourced from the already-fetched `/today` payload.

---

## 2. Frozen shared files — Foundation-only this run

**No later workstream (C1–C5) may edit any of these files.** Every workstream *consumes* them via props/imports/routes only.

| File / area | Why it is frozen |
|---|---|
| `frontend/src/app/App.tsx` | The route table. Every new route in §3 is added here once, by Foundation, pointing at stub files C-teams then edit in place. |
| `frontend/src/app/AppShell.tsx` | The nav shell — 5-slot nav, the new center **Log** button, top bar. |
| `frontend/src/app/AppShell.css` | Shell layout CSS. |
| `frontend/src/design-system/tokens.css` | Design tokens, including the new `--layout-*` and `--chart-*`/`--pending-*`/`--attention-*` tokens (plan §5.2, §9). |
| `frontend/src/design-system/base.css` | Global resets/base rules. |
| `frontend/package.json` | Dependency manifest (MSW, axe-core, Playwright per plan §15 land here when introduced). |
| **New shared components** (plan §8.2, to be built by Foundation): `ProgressTile`, `RecommendationCard`, `QuickActionChip`, `ConsentMoment`, `RelationshipChip`, `HistoryChart`, `StepIndicator`, `ActivityFeedRow`, `HealthShelfCard`, `FamilyMemberCard`, `LogSheet`, `SubjectSwitcher` | The shared vocabulary every screen in §18 is specced against. A C-team that reinvents one of these instead of importing it produces a screen the plan explicitly says must not exist (a bespoke tile, a bespoke sheet). |
| **New shared guard (added to the frozen list — not explicit in the plan's §8.2 list, but required by §12 and used by every journey):** a thin `<RequireCapability>`/`<RequireScope>` wrapper, e.g. `design-system/RequireCapability.tsx` | Plan §12: "a small `<RequireCapability>`/`<RequireScope>` wrapper (new, thin)... renders its children, a 'not shared with you' state, or nothing." Used by C1 (Today profile-needed state), C2 (FOOD_PROFILE gating on product decisions), C3 (family card visibility), C4 (goal category offers), C5 (feature-flag gating) — cross-cutting enough that it belongs with the other frozen primitives, not owned by whichever team happens to need it first. |
| **New shared Log-sheet trigger** — e.g. a `useLogSheet()` hook/context exported alongside `LogSheet` itself | See §4.2 — every workstream needs a stable way to *open* the sheet without importing its internals. |

Existing components in `design-system/` not listed above (`Card`, `Button`, `Switch`, `BottomSheet`/`ConfirmSheet`, `States`, `SearchBar`, `VerdictChip`, `AllergenFlag`, `NutritionSummary`, `ReasonList`, `HouseholdMemberCard`, `RowGroup`/`Row`, `ProductCard`/`Rails`) are **not** frozen — they are stable, already-shipped primitives any workstream may consume as-is, per plan §8.1. They are also not to be redesigned by any C-team without cause; they are reuse targets, not this run's work.

---

## 3. Route stubs Foundation must create, and who replaces each

App.tsx is frozen (§2), so every route this plan's phases will eventually need must be wired **once**, now, by Foundation — pointing either directly at an already-complete existing file, or at a **new placeholder file at the exact final path** that a later C-team edits in place. No C-team ever adds a `<Route>` element itself.

| Route | Foundation wires to | Status now | Who replaces the placeholder content, and how |
|---|---|---|---|
| `/welcome` | `features/onboarding/OnboardingScreen.tsx` (existing, 665 lines, complete) | Direct wire, no stub — fully built, just unrouted today | No one this run — it's already done. Foundation only adds the `<Route>` and the `mode = NONE` redirect. |
| `/today` | `features/today/TodayScreen.tsx` (existing route) | Already routed | **C1** rebuilds in place (no new route needed). |
| `/log/:kind` | The new `LogSheet` (frozen, §2) mounted by Foundation, opened by pathname match | New | No C-team file — Foundation owns the mechanism end to end, including its calls into `lib/wellness.ts` (observations), `lib/goals.ts` (habit completion), and the meal tile's navigation to `/meals/new`. C-teams only ever *trigger* it — see §4.2. |
| `/goals` | `features/goals/GoalsScreen.tsx` (existing route) | Already routed | **C4** rebuilds in place. |
| `/habits` | **new stub:** `features/habits/HabitsScreen.tsx` | New file, new route | **C4** replaces the stub content. Also handles `/family/:memberId/habits` via an optional `memberId` route param on the same file — no second file. |
| `/trackers/:category` | **new stub:** `features/trackers/TrackerHistoryScreen.tsx` | New file, new route | **C4** replaces the stub content. Also handles `/family/:memberId/trackers/:category` via an optional `memberId` param on the same file. |
| `/meals/new`, `/meals/:id` | `features/meals/MealLogScreen.tsx`, `MealDetailScreen.tsx` (existing routes) | Already routed | **C2** rebuilds in place. |
| `/meals` | **new stub:** `features/meals/MealHistoryScreen.tsx` | New file, new route | **C2** replaces the stub content (supports `?filter=awaiting`). |
| `/family` | `features/household/FamilyScreen.tsx` (existing route) | Already routed, but currently contains household-setup UI too (§0.5) | **C3** rebuilds in place *and* extracts the setup UI out (see `/you/household` row below). |
| `/family/:memberId` | **new stub:** `features/family/MemberView.tsx` | New file, new route | **C3** replaces the stub content. |
| `/family/:memberId/trackers/:category` | same file as `/trackers/:category` | — | **C4** (param-aware, one file). |
| `/family/:memberId/goals` | same file as `/goals` (`GoalsScreen.tsx`), adapted to also read a `:memberId` route param in addition to its current `?member=` query param | — | **C4** adapts `GoalsScreen.tsx`'s subject resolution to accept either. |
| `/family/:memberId/habits` | same file as `/habits` | — | **C4** (param-aware, one file). |
| `/family/:memberId/profile` | `features/household/ProfilePanel.tsx`, made directly routable | New route to an existing-but-unrouted component (**correction §0.4**) | **C1** adapts `ProfilePanel.tsx` to read `householdId`/`memberId` from `useParams` (with an `onBack` that navigates to `/family/:memberId` instead of the current parent-supplied callback) so both this route and `/you/profile` can link to it directly. |
| `/food` | `features/home/HomeScreen.tsx` (existing content, new route path) | New route pointing at an existing file | **C2** owns the file; no content change required purely for the route, but see the G-06 card move below. |
| `/food/recent` | `features/activity/ActivityScreen.tsx` (existing, rename of `/activity`'s path only) | Route path rename; redirect `/activity` → `/food/recent` added by Foundation | **C2** owns the file (correction §0.3) and may rename the file itself within its own directory if it chooses; the *route path* is Foundation's to set once. |
| `/you` | `features/settings/SettingsScreen.tsx` (existing content, new route path; redirect `/settings` → `/you` added by Foundation) | Route path rename | **C1** rebuilds in place (correction §0.3). |
| `/you/profile` | `features/household/ProfilePanel.tsx` (same file as `/family/:memberId/profile`, self case) | New route to an existing-but-unrouted component | **C1**, same adaptation as above. |
| `/you/sharing`, `/you/sharing/:audienceMemberId`, `/you/sharing/help`, `/you/shared-with-me`, `/you/access-log` | `features/sharing/**` (existing, 557 lines + 4 supporting files, complete) | Direct wire, no stub — fully built, just unrouted today | No new file. **C3** does a copy/structure pass only (per §2.3/§2.4 vocabulary and the `ActivityFeedRow` treatment on the access-log list) — this is styling/copy, not new logic. |
| `/you/household` | **new stub:** `features/household/HouseholdSettingsScreen.tsx` | New file, new route | **C3** extracts `NoHouseholdView`, `MembersList`, `InviteAdultPanel`, `AddChildPanel` (currently inline in `FamilyScreen.tsx`, lines ~120-730) into this new file, and trims `FamilyScreen.tsx` down to the redesigned hub content only. |
| `/you/appearance` | **new stub:** `features/settings/AppearanceScreen.tsx` | New file, new route (extracted from the existing inline `AppearancePanel` inside `SettingsScreen.tsx`) | **C1** extracts and extends with the larger-text toggle. |
| `/you/data` | existing inline panel inside `SettingsScreen.tsx` | No route change required this run | **C1**, low priority — no stub needed unless C1 chooses to split it out too. |
| `/next` | **new stub:** `features/next/NextScreen.tsx` | New file, new route | **C5** — the shell aggregates teaser links into insights/health-shelf/ask; C5 owns it because two of its three sections (health shelf, notifications inbox) are C5's, and it is the natural home for the notifications inbox itself (which has no other dedicated route in §3). |
| `/next/insights` | **new stub:** `features/insights/InsightsHistoryScreen.tsx` | New file, new route | **C4**. |
| `/next/health-shelf` | **new stub:** `features/health-shelf/HealthShelfScreen.tsx` | New file, new route, behind a feature flag | **C5**. |
| `/next/ask` | **new stub:** `features/ask/AskScreen.tsx` | New file, new route, behind a feature flag, MOCKED | **C5**. |

**Redirects** (`/settings`→`/you`, `/activity`→`/food/recent`, `/goals?member=:id`→`/family/:id/goals`, `mode=NONE`→`/welcome?next=`, signed-out→`/you?next=`): all added to `App.tsx` by Foundation. No C-team implements a redirect itself.

---

## 4. Cross-workstream interfaces (defined now, so no team invents its own shape)

### 4.1 The C1/C4 goal-and-habit boundary
See §1.1 in full — this is the primary one the task asked to be resolved unambiguously, and it is: C1 gets a new, independent, zero-mutation `GoalHabitSummary` component fed straight from `/today`'s already-typed `goals`/`habits` fields; C4 keeps exclusive ownership of `GoalProgressList.tsx`/`HabitList.tsx`/`goalOptions.ts` and Today stops importing them.

### 4.2 Opening the Log sheet
`LogSheet` is Foundation-owned and frozen (§2), but four other surfaces must be able to open it:
- C1: a `ProgressTile`'s **+** button ("Log {metric}") on Today.
- C2: nothing opens the sheet for meals — the meal tile inside `LogSheet` itself routes to `/meals/new` instead (per plan §18 1.2), so C2's only obligation is that `/meals/new` behaves correctly when arrived at this way (i.e., accepts no special state, just a normal navigation).
- C3: `MemberView.tsx`'s "Log for {name}" button, to log **as** another subject.
- C4: a habit tile inside the sheet is Foundation's own concern (habit completion is a simple PUT, no C4 UI involved in the sheet itself) — C4's only touchpoint is that `/habits`/`/family/:id/habits` reflect a completion made via the sheet, through normal query invalidation.

Foundation must publish a stable trigger — e.g. `useLogSheet().open({ kind, subjectId })` — as part of shipping `LogSheet`, and document its exact shape in the component's own file header when it lands, since C1 and C3 both call it directly.

### 4.3 `ProfilePanel.tsx`'s routing contract
Currently `ProfilePanel({ householdId, member, onBack })` — prop-driven only, invoked from `FamilyScreen.tsx`'s local state. C1 (owner) must change this to also accept routing: read `householdId`/`memberId` from `useParams` when mounted directly at `/you/profile` or `/family/:memberId/profile`, keep the prop path working for any remaining internal call site, and make `onBack` navigate rather than clear local state when route-mounted. C3's `MemberView.tsx` and Foundation's route stubs link to it by URL only (`<Link to={`/family/${memberId}/profile`}>`), never by importing the component and passing props.

### 4.4 `OverviewMember`/`MemberToday` as the one shared data shape
`lib/dashboard.ts`'s `OverviewMember` (consumed by C3's Family hub cards) and `MemberToday` (consumed by C1's Today and, via `useLogSheet`, subject-switching) are already fully typed and returned by the two existing endpoints (§10.1 of the plan, confirmed live). **No workstream forks these types or adds a client-side derived field to them.** Foundation's `FamilyMemberCard` and `SubjectSwitcher` components should be typed directly against `OverviewMember`/a subject list derived from it — C3 and C1 pass the server object straight through, not a remapped shape.

### 4.5 The recommendation pipeline
Plan §10.2 calls for a new `lib/recommendations.ts` — "a thin composition layer over `lib/insights.ts` that shapes insight output into `RecommendationCard` props." This file was not in the task's original C1–C5 file list; it is assigned to **C4** here (it owns the Journey 4.1 rebuild of `InsightList.tsx` into the `RecommendationCard` feed, and the same shaping logic feeds Today's and Family's teaser cards). C1 (Today) and C3 (Family) import the *rendered* `RecommendationCard` output — via C4's `lib/recommendations.ts` helper functions, not by hand-shaping `Insight[]` themselves — so all three surfaces stay consistent with a single formatting source.

### 4.6 The subject list behind `SubjectSwitcher`
Both the Log sheet's subject picker (C1's primary consumer, via Foundation's component) and `MemberView.tsx`'s "Log for {name}" (C3) need the same list: "you, always; children you guard; adults who made you a caregiver" — sourced from the overview's `relationship` field per plan §7, never a raw member list. This is a pure derivation off `HouseholdOverview.members` (already typed). Put the derivation once in `lib/dashboard.ts` (a small exported helper, e.g. `loggableSubjects(overview: HouseholdOverview): OverviewMember[]`) rather than each team reimplementing the `relationship !== 'NONE'` filter independently. Since `lib/dashboard.ts` is C1-owned for extension purposes (§1), **C1 adds this helper**; C3 imports it.

---

## 5. Acceptance checklist per workstream

### C1 — Today, Individual Mode, Profile, Log
- [ ] `TodayScreen.tsx` renders `/today`'s `metrics[]`/`goals`/`habits` directly; the client-side `sumByMetric`/`TODAY_METRICS`/`observations` query is deleted (closes plan G-04).
- [ ] Today's day boundary comes from `?date=` + the household's `timeZone` field, not `new Date()` (closes G-03).
- [ ] `features/today/QuickLog.tsx`/`.css` deleted; Today's log entry point is the shared `LogSheet` via the center nav button (§4.2).
- [ ] Today no longer imports anything from `features/goals/**`; `GoalHabitSummary.tsx` (new, C1-owned) renders goals/habits straight from the `/today` payload (§1.1).
- [ ] `lib/wellness.ts`'s `listShares`/`share`/`unshare`/`VisibilityGrant` exports and their `/wellness/members/{id}/shares` calls are deleted; a repo-wide search confirms no remaining importer.
- [ ] Consent-off state on Today renders the shared `ConsentMoment` component, not inline error text.
- [ ] `ProgressTile` usage: one link named "{metric}, {value}. Open history."; the **+** is a named sibling button (WCAG 1.3.1); both meet the 44×44/48px target (2.5.8).
- [ ] Today's root no longer applies `max-width: 60ch`; it opts into `--layout-dashboard-max` with the 1→2→4-column grid (closes G-00c for this screen).
- [ ] `/you` (rebuilt `SettingsScreen.tsx`) and `/you/profile`, `/family/:memberId/profile` (routed `ProfilePanel.tsx`, §4.3) all work end to end, including the `onBack` navigation change.
- [ ] `ConsentPanel.tsx` no longer performs its own N+1 read pattern where the household overview already carries the same fact for a viewed member (verify against current behavior; if none is found, no change is required beyond confirming it).

### C2 — Food discovery, product decisions, meals & apportioning
- [ ] `MealLogScreen.tsx`'s per-person destination sentence ("Goes into your log" / "Saved with the meal...") is sourced from the household overview's `relationship`/`canLogNutrition`/`trackingConsentActive` fields, never from `memberType`/age-band inference (closes G-02).
- [ ] A meal's whole total is hidden from a viewer when any portion's share is withheld from them (no arithmetic reconstruction of a withheld share).
- [ ] `/food/recent` (renamed from `/activity`) works via the redirect Foundation added; existing "recently viewed" behavior is unchanged.
- [ ] `/product/:id/report`'s dead link is removed, or a real route is added (closes G-09) — pick one, do not leave the 404.
- [ ] New `/meals` (history, `?filter=awaiting`) is built at `features/meals/MealHistoryScreen.tsx`.
- [ ] The household food-readiness card (currently `HouseholdOverview.tsx`, C3-owned) is moved to `/food` and restyled with `--attention-bg`/`--attention-rule`, never `--verdict-limit-*` (closes G-06) — **coordinate with C3**, since the source file is C3's; C2 receives the moved card's JSX/behavior as a handoff, does not reach into `HouseholdOverview.tsx` itself.
- [ ] `lib/nutritionLog.ts`'s exported function signatures are unchanged (integration only).

### C3 — Family Mode, member views, sharing/caregiving
- [ ] `/family` reads only `GET /households/{id}/overview` — `FamilyWellness.tsx`'s `OwnRecords`/`SharedCategories` no longer call `listObservations`/`goalProgress`/`listHabits`/`visibleCategories` per member (closes G-01 for this file).
- [ ] Individual mode (`household of one`) shows exactly the one quiet "It's just you for now" card (two equal buttons: Invite an adult / Add a child) and nothing else references family anywhere in the product (AC-2.7, the never-nag rule).
- [ ] `FamilyMemberCard`/`RelationshipChip` (Foundation components) replace the ad hoc `MemberCard`/`SharedCategories` markup; server member order is preserved, never client-re-sorted.
- [ ] New `/family/:memberId` → `features/family/MemberView.tsx` replaces the inline per-member block; it is the second of the two places (with the Log sheet) subject-switching happens (plan §7).
- [ ] Household setup (`NoHouseholdView`, `MembersList`, `InviteAdultPanel`, `AddChildPanel`) is extracted out of `FamilyScreen.tsx` into `features/household/HouseholdSettingsScreen.tsx` at `/you/household`; `FamilyScreen.tsx` itself shrinks to the redesigned hub content only.
- [ ] `/you/sharing*`/`/you/access-log` copy pass complete: no "Access Grants"/"Manage permissions" language remains (already close per plan §18 3.2 — confirm, don't rewrite the working logic); the access-log list uses `ActivityFeedRow` grouped by day.
- [ ] **Open question flagged, not silently resolved:** `useHouseholdOverview.ts`'s per-member `getProfile`/`listGrants` fan-out (the readiness card now living at `/food` per C2's checklist) has no confirmed single-endpoint replacement — `GET /households/{id}/overview` does not carry profile/consent fields. Either accept the existing per-member pattern for this one domain (it is a household-setup, not a wellness, page — the REFUSED-row-pollution concern in G-01 is about wellness scopes, and `getProfile`'s 403 is already handled as a designed "Private" state, not a probing pattern) or raise this with whoever owns the backend `experience`/`profile` modules before changing it. Do not repoint this file at `/households/{id}/overview` as if it will produce the same data — it will not.

### C4 — Goals, Habits, tracker history, insights/recommendations
- [ ] `/goals` and `/habits` are separately routable (not only reachable as two sections of one combined screen).
- [ ] The habit-history strip is rebuilt as a 7-day strip + month-grid detail, meeting the 44×44px target (closes G-05 — current `.habit__history` cells are ~20px).
- [ ] The goal-metric offer list is driven by `capabilities.goalCategories`/`goalDirections` from `GET /members/{id}/experience`, not solely the hardcoded `GOAL_METRICS` table in `goalOptions.ts` (the current table already comments that the server is the real gate, but the *offer* itself must become capability-driven, not just filtered by the wellness metric definitions' `adultOnly` flag as it is today).
- [ ] New `/trackers/:category` and `/family/:memberId/trackers/:category` render `HistoryChart` (Foundation component) against `GET /wellness/members/{id}/observations/{metric}`, with a "Show as table" toggle and gaps (not zeros) for not-logged days.
- [ ] `InsightList.tsx` is rebuilt into the `RecommendationCard` feed via the new `lib/recommendations.ts` (§4.5); family recommendations never rank members (D-038) and never total across members unless both members share the relevant scope.
- [ ] No streak, score, percentage, or color-graded fill exists anywhere in the rebuilt Goals/Habits/Trackers UI — the progress bar stays `aria-hidden`, text carries the meaning (already true in `GoalProgressList`'s `GoalLine`/`HabitList`'s consistency text today; preserve this through the rebuild, do not regress it).

### C5 — Backend-dependent shells (health shelf, notifications, Ask)
- [ ] `lib/healthShelf.ts`, `lib/notifications.ts`, `lib/ai.ts` are built entirely against typed mocks matching the suggested contracts in plan §10.3; each module's file header states MOCKED and names the blocking decision (D-049 legal gate for health shelf, D-050 for notifications, D-011/D-052 + missing model credential for Ask).
- [ ] `/next/health-shelf` and everything under it renders only behind a feature flag that defaults off; nothing on this surface is reachable in a normal build.
- [ ] `/next/ask` is clearly labelled MOCKED in the UI itself (not only in code comments); no network call to any AI provider exists in the shipped code path.
- [ ] Notification UI (mock data included) never addresses or targets a CHILD member; mock default opt-ins match D-050 (engagement categories default OFF, medication/appointment reminders default ON).
- [ ] None of C5's new routes/components block the app from building or navigating through Phases 0–3 — every screen renders a complete, honest state (loading/empty/mocked) even with zero backend behind it, per the plan's own "no dead ends" rule (§6).

---

## 6. Summary of open items for the reader

- **§0.6 / C3 checklist:** `useHouseholdOverview.ts`'s profile/consent fan-out has no identified single-call fix — needs a decision before or during C3's work, not an assumption.
- **§0.3:** three files (`ConsentPanel.tsx`, `SettingsScreen.tsx`, `ActivityScreen.tsx`) were unassigned in the task's starting draft; all three are now assigned to C1/C2 with rationale, but flagged here in case the team disagrees with the split (e.g., `SettingsScreen.tsx` could equally be argued as a sixth, cross-cutting "You hub" workstream rather than folded into C1 — this document chose C1 because `/you/profile` and Individual Mode already live there).
- **§4.2:** the exact `useLogSheet()` hook shape is Foundation's to finalize when it builds `LogSheet` — C1 and C3 should confirm the published shape against what's assumed here before wiring their trigger points.
- **§4.5:** `lib/recommendations.ts` is a new file not in the task's original list, assigned to C4 — flagged in case another team expected to own it.

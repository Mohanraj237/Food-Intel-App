# Browser Audit & Demo-Data Report — 2026-09-12

**Role:** PM / BA / QA / UX review · **Method:** black-box Playwright against the running stack
**Source of truth:** `docs/plans/frontend-wellness-platform-plan.md`, `docs/decisions/decision-log.md`

---

## 1. What was tested

Real browser (Chromium via Playwright) against the live stack — Vite dev server on `:5173`, Spring Boot on `:8090`, PostgreSQL + MinIO in Docker. Nothing below is inferred from reading code; every defect was reproduced in a rendered page and has a screenshot or a captured computed style behind it. Where a conclusion came from code, it is labelled as root-cause analysis performed *after* the browser reproduced the symptom.

| Dimension | Coverage |
|---|---|
| Viewports | Desktop 1440×900, Mobile 390×844 (Pixel 7 profile) |
| Personas | **Raghav** (father — SELF + GUARDIAN of a child + CAREGIVER for his father), **Krishnan** (grandfather — older-adult tier, has a caregiver), **Arjun** (brother — shares nothing with anyone) |
| Routes exercised | ~25, signed-out and signed-in: `/`, `/welcome`, `/today`, `/family`, `/family/:id`, `/food`, `/food/recent`, `/search`, `/scan`, `/product/:id`, `/meals`, `/meals/new`, `/goals`, `/habits`, `/trackers/:metric`, `/next`, `/next/insights`, `/next/health-shelf`, `/next/ask`, `/you`, `/you/profile`, `/you/sharing`, `/you/shared-with-me`, `/you/access-log`, `/you/household`, `/you/appearance` |
| Automated checks | 30 Playwright tests, green on both viewports |
| Captured per page | Full-page screenshot, headings, nav inventory, console errors, failed requests, horizontal-overflow check, main-content width |

Test assets live in `frontend/e2e/` (`recon`, `investigate`, `journeys`, `verify-fixes`), screenshots in `frontend/e2e/screenshots/`, structured findings in `frontend/e2e/results/`.

---

## 2. Defects — fixed this pass

### DEF-01 · Search does not respond to typing · **High** · "What should I eat?"
- **Repro:** Sign in → `/search` → type `milk` → wait.
- **Expected:** Results narrow to milk products as you type.
- **Actual:** The page keeps rendering the unfiltered browse grid — *"Browse products · Showing 30 of 894"* — headed by products with no relation to the query (1-to-3 noodles, coconut oil, chocolate). Results only appear after pressing Enter. To a user this does not read as "search hasn't run", it reads as **"search returned the wrong things"** — which is exactly how it was reported.
- **Root cause:** `SearchBar` only emits on form submit; `SearchScreen` derives results solely from the `?q=` URL param. Typing updated neither.
- **Fix:** Debounced (250 ms) query→URL sync while on `/search`, using `replace` so a search does not leave one history entry per keystroke. Deliberately scoped to the search screen so typing in the global header does not yank a reader off Today.
- **Evidence:** `desktop/search/02-after-typing-no-submit.png`.

### DEF-02 · Focus ring deforms every component it decorates · **High** · global
- **Repro:** Keyboard-focus any product card in search results.
- **Expected:** A ring around the card; the card keeps its shape.
- **Actual:** The card's corner radius snaps from 12 px to 6 px on focus and the teal ring traces a rectangle that no longer matches the card inside it — reading as a stray box drawn over the card. Pill-shaped controls (`--radius-full`) square off entirely.
- **Root cause:** `base.css` had `:focus-visible { … border-radius: var(--radius-sm) }`. A focus utility was overriding the geometry of every focusable component in the app.
- **Fix:** Removed `border-radius` from the focus rule. The outline follows each element's own radius by itself.
- **Regression guard:** A test asserts the focused radius equals the unfocused radius while the 2 px ring is still present.

### DEF-03 · Durations and volumes shown in raw storage units · **Medium** · "How am I doing?"
- **Actual:** `Sleep 400 min`, `Water 2200 ml`. **Expected** (plan §6.1): `6 h 40 min`, `2.2 L`.
- **Root cause:** `formatMeasure` concatenates value + canonical unit; no duration or volume formatter existed anywhere in the codebase.
- **Fix:** Added `formatMetricMeasure`, converting only `min` and `ml` — the two canonical units with a conventional spoken form. Steps, kg and every clinical reading pass through untouched (D-037: a reading is shown exactly as recorded).

### DEF-04 · "Habits 1 of 0 done today" · **Medium**
- **Actual:** Arjun's family card read `Habits 1 of 0 done today` — a weekday habit ticked on a Saturday: nothing due, something done. The numbers were right and the sentence was nonsense.
- **Fix:** When nothing is due, the count stands alone ("1 done today") instead of being framed against a zero denominator.

### DEF-05 · Goal rendered as a raw enum · **Medium**
- **Actual:** Krishnan's goals screen showed `ACTIVE_MINUTES`.
- **Root cause:** `METRIC_META` omitted `ACTIVE_MINUTES`, which the plan's own offer set (§18 Journey 1.3) includes; the display then fell back to the raw code.
- **Fix:** Added the entry, **and** replaced the raw-code fallback with a humanised label so no future unlabelled metric can leak an enum at a user.

### DEF-06 · React duplicate-key and key-spread warnings on Today · **Medium**
- **Actual:** Console: *"Encountered two children with the same key, `NUTRITION_TREND-WEEK-…`"* and *"A props object containing a `key` prop is being spread into JSX"*. Consequence: where one member has two insights of the same type and window, **the second card is silently dropped**.
- **Root cause:** Key was `type-window-subject` (not unique across metrics); `key` was also spread into the component.
- **Fix:** Key now includes the evidence labels that actually distinguish two insights; `key` is destructured out of the spread in both consumers.

### DEF-07 · A child you don't guard reads as having refused to share · **Medium** · privacy framing
- **Actual:** On Raghav's family view, his daughter Ananya (guarded by Meera) rendered *"Not shared with you"*.
- **Why it matters:** A child never makes a sharing choice and cannot — child records are reachable only through guardianship (D-044). The copy asserted something false and taught the wrong mental model about children's data.
- **Fix:** Children a viewer does not guard now read *"Their guardians look after their records."*

### DEF-08 · Tab and page disagree on their own name · **Low**
`You` tab opened a page titled **Settings**; `/goals` was still titled **"Goals and habits"** after habits moved to `/habits`. Both corrected.

### DEF-09 · Hard-coded English `(current)` in the bottom nav · **Low** · D-019
The only untranslated string in the shell, duplicating the `aria-current="page"` that assistive technology already announces in the user's own language. Removed; the accessible signal is retained and asserted by test.

---

## 3. Defects — found, not fixed (with reasoning)

### DEF-10 · The UI discovers permissions by trying · **High** · needs backend support
- **Observed:** Loading `/food`, `/you` and `/you/household` fires `GET /households/{id}/members/{memberId}/profile` and `/consent/members/{id}/grants` for **every** member, producing a burst of 403s for members the caller may not see. The audit table shows `CHILD_DATA_ACCESS_DENIED` ×8 and `CONSENT_DENIED_AT_PROCESSING` ×17 from this session alone.
- **Why it matters:** The plan's UXD-05 is explicit — *"the UI never discovers access by trying"* — because refusals are audited and the access log is the product's trust feature.
- **Why not fixed here:** `useHouseholdOverview` needs per-member profile-readiness and consent state that `GET /households/{id}/overview` does not currently return. Deleting the fan-out without that removes the household food-readiness feature. **Recommended:** add readiness + consent fields to the overview response, then collapse the fan-out to that single call.
- **Mitigating fact:** these particular refusals write audit rows but do **not** appear in the member-facing access log (verified against `/members/{id}/access-log`), so the user-visible trust surface is not currently polluted — the cost is audit noise and needless 403s.

### DEF-11 · Blood pressure shown as a lone systolic · **Medium**
Krishnan's card reads `Clinical readings 132 mmHg`. A blood-pressure reading is a pair; a systolic alone is not a meaningful figure, and §6.1 specifies `132/84 mmHg`. Not fixed because the card deliberately shows one headline line per category — pairing SYSTOLIC with DIASTOLIC needs a deliberate rule in the line builder rather than an ad-hoc patch.

### DEF-12 · `/trackers/:category` takes a metric code, not the documented slug · **Low**
`/trackers/hydration` renders the heading `hydration` and *"There's no tracker for this yet."* The screen actually expects a metric code (`/trackers/WATER_ML`), which is what Today's tiles link to — so users are not blocked. Two smaller issues remain: the route diverges from the sitemap in plan §3, and the not-found heading echoes the raw URL parameter back at the reader.

### DEF-13 · The access log is noisy enough to undermine itself · **Low** · product
Krishnan's access log showed **15 identical** *"Raghav viewed your Activity, Clinical, Hydration"* entries inside 60 seconds, because every page load logs a read. Each entry is truthful, but a log that repeats itself fifteen times per visit is hard to audit, which is the opposite of what the feature is for. Recommend collapsing by actor + scope + day in the read model.

### DEF-14 · Search bar and settings cog occupy every screen · **Low** · IA
Plan UXD-02/03 put persistent search in **Food only** and replace the header cog with the **You** tab. Both are still global: on mobile, Today and Family spend their most valuable space on a product-search field and a duplicate settings entry point.

### DEF-15 · Catalogue data quality is user-visible · **Low** · content
The default browse grid surfaces many entries titled *"Product name not recorded yet"* (with the category name shown in their place) and blank grey tiles. The honesty is correct and deliberate; the effect in a consumer grid is that the shelf looks broken. Recommend excluding unnamed products from default browse while keeping them findable by barcode.

---

## 4. Checked and cleared — not defects

Distinguishing genuine defects from intentional design mattered here; three plausible-looking findings were investigated and dismissed:

- **Two search inputs and two `<nav>` elements in the DOM.** Only one of each is visible per breakpoint and the other is `display:none`, which removes it from the accessibility tree. This is a correct responsive pattern, not duplication. (It did break my first test selector, which is a harness lesson, not an app bug.)
- **Bottom nav appearing to overlap cards.** An artifact of full-page screenshots with `position: fixed`, not a real overlap.
- **42 `REFRESH_TOKEN_REUSE_DETECTED` audit rows.** Caused by the test harness replaying a rotated refresh token, not by the application.

---

## 5. Demo dataset

One week in the life of the **Iyer family** — a ten-person, three-generation household. Two interchangeable seeders ship, in `tools/demo-seed/`:

| Script | What it is | When to use it |
|---|---|---|
| **`demo-family.sql`** | Pure SQL, run with `psql`. Deterministic UUIDs, `generate_series` for the week. | The default. No backend required, instant, reproducible. |
| `seed-demo-family.mjs` | Node script driving the REST API. | When you want the seed to double as a contract test — every write goes through the real consent/age/goal gates. |
| `reset-demo-family.sql` | Removes the demo family so either seeder can re-run. | Before re-seeding. |

**Neither is a Flyway migration, on purpose:** demo people must never ride the production migration chain. Both are run by hand against a local database.

The SQL version has to honour in writing what the application otherwise enforces in code, so each rule is stated and commented where it applies: the D-036 age gate (no BODY or CLINICAL metric for a child, ever), the D-047 child goal rule (ACTIVITY/SLEEP/HYDRATION, AT_LEAST/MAINTAIN only — no calorie, weight or "at most" goal), D-041 portion derivation, D-044 sharing (subject-granted, offered scopes, never about a child), D-048 consent, and D-053 day boundaries (every timestamp built as Asia/Kolkata wall-clock, not the server's zone).

| | Seeded |
|---|---|
| Members | **10** — grandfather, grandmother, father, mother, **two brothers**, **two sisters**, son (7–9), daughter (10–12) |
| Tiers | OLDER_ADULT ×2, ADULT ×6, CHILD ×2 |
| Observations | **557** across 7 days (326 entered + 231 derived from meals), with deliberate gap days so "Not logged" is exercised |
| Meals | 14, with **16 portions left awaiting confirmation** — the D-041 "waiting for you" state |
| Goals | 17 — children restricted to ACTIVITY/SLEEP/HYDRATION and AT_LEAST/MAINTAIN only |
| Habits | 11, with 54 completions and real gaps so "done on 4 of 7 days" is true |
| Sharing grants | 14, producing **every** relationship state the UI has a screen for |
| Child BODY/CLINICAL rows | **0** — verified by query in the script's own output |

Relationships worth demoing: the grandfather granted his son MANAGE on hydration and activity, making Raghav a genuine **caregiver**; the two children are guarded by **different parents**, so "a child you do not look after" has a real person behind it; Arjun and Kavya share nothing, so the NONE state is real rather than hypothetical.

**Local credentials** — development only, deliberately weak, reserved `example.com` domain (RFC 2606), never to be reused anywhere deployed. All eight adults use `DemoFamily2026!seed`:

| | |
|---|---|
| `raghav.iyer.demo@example.com` | father — owner, guards Aarav, caregiver for Krishnan (**best all-round view**) |
| `krishnan.iyer.demo@example.com` | grandfather — older-adult tier, has a caregiver |
| `lakshmi.iyer.demo@example.com` | grandmother |
| `meera.iyer.demo@example.com` | mother — guards Ananya |
| `arjun.iyer.demo@example.com` | brother — shares nothing (the NONE state) |
| `vikram.iyer.demo@example.com` | brother |
| `divya.iyer.demo@example.com` | sister |
| `kavya.iyer.demo@example.com` | sister — shares nothing |

Aarav (son) and Ananya (daughter) have **no accounts**, by design — a child never signs in.

The password hash is a real Argon2id digest produced by the application's own encoder, reused across all eight accounts: it is one published, obviously-fake local password, and eight distinct salts buy nothing when the plaintext is in the file's own header comment.

`reset-demo-family.sql` deletes only accounts matching the reserved `.demo@example.com` suffix. It deliberately does **not** delete `audit_event` rows: that table is append-only by design, and an audit trail a seed script could erase would defeat its purpose.

---

## 6. Product / UX recommendations

The two reported problems are symptoms of the same thing: **the shell is still a packaged-food search app with wellness screens added to it**, rather than a wellness companion in which food search is one journey.

**Search**
1. **Suggestions-as-you-type from the global bar** — product and category suggestions in a dropdown, Enter or selection commits to results. This is the Flipkart/Blinkit pattern and the real fix for "search feels broken"; the debounce shipped here fixes the results screen but not the header-from-anywhere case.
2. **Never show an unfiltered browse grid under a typed query.** Today, "no results yet" and "894 unrelated products" are the same pixels.
3. **Move persistent search into Food.** Today and Family answer different questions and should not spend their header on product search.

**Navigation**
1. **Stop transplanting the mobile centre button to desktop.** On the desktop top bar, `Log` is an action button wedged between `Family` and `Food` — a destination row with a verb in the middle. Give it a distinct, right-aligned primary-action position.
2. **Active state is communicated by colour alone** on the desktop top bar (teal vs grey). The design system's own rule is that colour never carries meaning alone — add weight or an underline.
3. **Replace the Unicode glyph stand-ins** (`◔ ☖ ⊕ ▤ ⚙`) with the real inline-SVG set the design system specifies. `You` currently uses a **settings gear**, which says "Settings", not "you" — and the page it opens was titled Settings until this pass.
4. **Thin the mobile header:** wordmark + search + cog + five tabs is four competing elements before any content.

---

## 7. Verification run

| Check | Result |
|---|---|
| `npm run build` | ✅ passes (PWA bundle generated) |
| `npx tsc -b --noEmit` | ✅ no errors |
| `npm run test` (Vitest) | ✅ 30/30, including the banned-vocabulary gate over the new copy |
| Playwright, both viewports | ✅ **30/30** |
| Flyway migrations | ✅ V1–V14 all `success = true` |
| Seed integrity | ✅ 8 members, 464 observations, 14 meals, 12 grants, **0 child BODY/CLINICAL rows** |
| `npm run lint` | ⚠️ **cannot run** — no `eslint.config.js` exists in the repo (pre-existing, predates this work) |

---

## 8. Release readiness

**Not ready to ship; healthy enough to demo.** The app now runs end-to-end with realistic multi-generational data, the two reported defects are fixed and regression-guarded, and no safety or privacy boundary was found violated in the rendered product — the age gate, consent gates, the "not shared" states and the caregiver model all behaved correctly under a populated household.

Blocking before a real release:
1. **DEF-10** (permission probing) — needs the backend overview fields; it is an architectural violation of an accepted decision, not cosmetic.
2. **No ESLint config** — the repo has never had a lint gate.
3. **No component-test coverage** — 30 unit tests cover three pure-logic modules; the Playwright suite added here is now the only coverage of rendered behaviour.
4. **DEF-11** (lone systolic) should be settled before any clinical-adjacent screen is shown to real users.

The remaining items (DEF-12 to DEF-15) are quality and IA debt that shape how finished the product feels, and are the natural content of the next pass alongside the navigation recommendations in §6.

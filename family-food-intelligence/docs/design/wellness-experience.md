# Wellness Experience Specification (W1–W2)

- **Version:** 1.0 · **Date:** 2026-09-12 · **Status:** PROPOSED. This spec is build-ready. Items marked ❓ need a product decision (§9).
- **Scope authority:** `docs/architecture/wellness-platform-architecture.md` (v2) and decisions D-034 to D-053. This document never widens scope. Where it disagrees with them, they win.
- **Binding decisions:** D-002, D-004, D-019, D-036, D-037, D-038, D-040, D-041, D-043 to D-048, D-050, D-051, D-053.
- **Related documents:**
  - `design-system.md` owns tokens and components. This spec proposes additions (Appendix A) and redefines nothing.
  - `ux-principles.md` still applies in full.
  - The data pipelines behind W2 trackers and W3 insights are specified in sibling design notes. This document specifies what the user sees.
- **Conventions:**
  - Requirement levels are **MUST**, **SHOULD** and **MAY**.
  - Phase tags:
    - **W1**: build now.
    - **W2**: the tracking phase.
    - **W3+**: future. Not rendered, not linked, no placeholder (arch §9.3).
  - Quoted strings are proposed en-IN copy. `code.keys` are proposed locale keys. The frontend owner adds both to `frontend/src/locales/en-IN.json`, which is append-only.

---

## 0. Decisions this spec makes

These are UX decisions inside the existing scope. Those marked ⚖︎ touch a recorded boundary; the orchestrator should promote them to the decision log.

| ID | Decision | Why |
|---|---|---|
| UXD-01 | **Log** is the centre item of the bottom nav. It is a button that opens a sheet, not a destination. `/log/:kind` deep links open the sheet over Today. | Logging is an action you take from anywhere. It is not a place you go. |
| UXD-02 | Signed in, the persistent product search bar lives only in the **Food** tab. Today, Family and You get a compact header with no search. | Today answers "how am I doing?", not "find a product". Search stays one tap away. |
| UXD-03 | **You** replaces Settings and the header cog. | One home for profile, sharing, privacy and settings. It also frees the header. |
| UXD-04 ❓ | The **Family** tab stays for a household of one. It shows one quiet card ("Just you for now"). There are no badges, dots or prompts anywhere else. | Stable navigation, and the only discoverable path to family (D-043 "offers the option once, quietly"). See Q-01. |
| UXD-05 ⚖︎ | **The UI never discovers access by trying.** Every cross-member surface first reads `/households/{id}/overview` or `/members/{id}/access`. It then requests only scopes it may see. | A 403 from a probe is audited as REFUSED and appears in the subject's access log (`AccessLogService`). The UI must not fill someone's trust log with noise it caused. |
| UXD-06 | Tier and capabilities come only from `GET /me` and `/members/{id}/experience`. The UI never checks an age band or member type to decide what to offer. | D-046: the server is authoritative and the UI renders the flags. |
| UXD-07 | A day's values come only from `/members/{id}/today`, where the server aggregates SUM or LATEST in the household zone. The client never sums observations for a day. | D-053 day boundaries. It also keeps every number traceable and consistent with goal progress. |
| UXD-08 ⚖︎❓ | For a CHILD subject, energy (kcal) is never a headline figure on Today-style summaries or Family cards. It stays available, as recorded, one level down (meal detail, nutrition history). | D-047 removes calorie targets for children. A kcal headline invites the restriction framing that D-047 exists to prevent. See Q-02. |
| UXD-09 | Health readings (CLINICAL) show value, unit and time only. No average, no change figure, no goal line, no colour, no reference band. They carry one static note that never varies with the value. | D-037. |
| UXD-10 | Body measurements show the person's own history and plain arithmetic change in a chosen window. The colour stays neutral and the direction is never colour-coded. | D-036 (adults) and D-037 (no category). |
| UXD-11 | Sharing changes are reviewed on a summary sheet before they apply. Stopping sharing takes one confirmation and applies immediately. | Granting health data is consequential. Revoking is always easy. |
| UXD-12 | Tracking consent is asked in context at the first log (and in onboarding), not only in settings. Declining has equal weight. When consent is withdrawn, the person's own history stays readable. | D-048 and the design-system ConsentBanner rule. |
| UXD-13 | There is no offline write queue in W1–W2. A save needs a connection. Offline, the UI says plainly that nothing was saved. | Honesty. A silent queue risks duplicates in data that cannot yet be deleted (Q-07). |
| UXD-14 | Save buttons repeat the value and unit ("Save 250 ml"). | This is the typo guard. There is no edit or delete endpoint for an observation yet. |
| UXD-15 | Sleep counts toward the day you woke up. | This is how people talk about "last night". |
| UXD-16 | **Larger text** mode (`html[data-text-size="large"]`) scales type 1.25× and raises targets to 48 px, or 56 px for primary actions. It is offered to OLDER_ADULT, never imposed, and available to everyone. | D-046. |
| UXD-17 | Habit history uses a 7-day strip on list rows and a month grid on the detail screen. Every day cell is at least 44 px. | The current 14-cell strip fails target size (§8). |
| UXD-18 | Family cards share one anatomy for every member and follow the server order. They show no totals, averages, ranks or side-by-side counts. | D-038, D-040, D-051. |
| UXD-19 | The household role (Owner/Adult) never appears on a health surface. It appears only in You → Household. | Role grants nothing on health data (D-044). Showing it implies it does. |
| UXD-20 | When a meal detail withholds any share's nutrition from the viewer, the whole-meal nutrition total is hidden for that viewer too. | Total × fraction would otherwise reveal the withheld share. The API still returns it (Q-05). |

---

## 1. Experience principles

### 1.1 Principles

| # | Principle | What it means on screen |
|---|---|---|
| P1 | **Calm by default** | Neutral palette. Nothing flashes, pulses or turns red. At most one attention strip per screen, and it is informational, never urgent. |
| P2 | **Honest numbers** | Every number traces to something recorded. "Not logged" is never 0. No invented targets or norms. Coverage is stated ("logged on 4 of 7 days"). |
| P3 | **Your data is yours** | Default-closed. Every screen that shows someone else's data says why you can see it. "Not shared with you" is a designed state. The access log is one tap from sharing. |
| P4 | **Describe, don't judge** | We report what happened and name the next step. No praise, no blame, no moral words about food or bodies. Copy about a child is always non-evaluative. |
| P5 | **Fast to log** | Water or a habit tick in 2 taps. Most entries take under 10 seconds. Time defaults to now. Everything is reachable one-handed from the bottom half of the screen. |
| P6 | **Care without control** | Caregivers help; they never override. Every caregiver action is attributed and visible to the person helped. Only the subject grants. |
| P7 | **Premium through restraint** | Generous space, few elements per card, system fonts, flat bordered cards (design-system §4). No gamified chrome. Motion is subtle and honours reduced motion. |
| P8 | **One product, adapted by tier** | An older adult gets emphasis, not restrictions. A child is not a smaller adult. Tier names never appear in copy. |

### 1.2 Anti-patterns (MUST NOT ship)

| Anti-pattern | Why it's banned | Do this instead |
|---|---|---|
| Shame or guilt copy ("You missed…", "Only 2 glasses", "Don't give up!") | D-050 copy rule, P4 | State the fact: "Water: 500 ml logged today." |
| Streaks, "days in a row", streak-loss warnings, flames | D-036, D-051. Punitive. | A count: "Done on 4 of 7 days." |
| Badges, trophies, confetti, level-ups | D-051. For a child, never. | Nothing. Adult milestones are W6, opt-in, and not in this spec. |
| Red, amber or green (or any category colour) on BP, glucose, SpO2, heart rate, temperature, weight or BMI | D-037, design-system §2.2 | Value, unit and time, in `--text`. |
| "Normal range" bands, reference lines, "high"/"low"/"elevated" labels | D-037 | Own history only. |
| Rendering missing data as 0, empty bars as zero-height data, or lines interpolated across gaps | P2, arch §9.3 | "Not logged". Gaps stay gaps. |
| Ranking, leaderboards, "most active", household totals or averages across members | D-038, D-042 exclusion, D-051 | Parallel member cards in a fixed order, each about one person. |
| Rings to close, a composite "health score", a family score | D-040 | Per-goal progress the person set themselves. |
| Calorie deficit, "calories left", projected weight or dates | D-047 | Nothing. The product never computes a target. |
| Before/after body imagery, silhouettes, scale icons for children | D-036 | No body imagery. BODY tiles exist for adults only. |
| Red count badges or dots on tabs | P1 | Quiet in-screen attention strip. |
| Confirmshaming ("No, I don't care about my health") or pre-ticked consent | Design-system ConsentBanner rule | Neutral "Not now", with the same size as the grant button. |
| Fear framing of food (toxic, poison, dangerous, harmful, never eat, clean) | D-004 | Frequency and fit language (product-policy). |
| Placeholder cards for features that don't exist ("Medicines, coming soon") | Arch §9.3 | Render nothing until the feature ships. |
| Blurred or greyed fake data behind "not shared" | P3. Implies the data exists and is being hidden. | One plain line: "Not shared with you." |
| Gendered pronouns for members ("when she adds it") | We don't know, and don't ask | Repeat the name: "when Priya adds it". |

---

## 2. Information architecture and navigation

### 2.1 Signed-in navigation (5 items)

| Slot | Label | Kind | Route | Answers | Icon (inline SVG, design-system §7) |
|---|---|---|---|---|---|
| 1 | Today | NavLink | `/today` | How am I doing? | sun-circle |
| 2 | Family | NavLink | `/family` | How is my family doing? | two-people |
| 3 | **Log** | **button**, `aria-haspopup="dialog"` | opens Log sheet (`#log`) | (an action) | plus, in a 48 px brand-filled circle |
| 4 | Food | NavLink | `/food` | Is this product right for us? | bowl |
| 5 | You | NavLink | `/you` | (profile, sharing, privacy, settings) | person |

- Labels are always visible (design-system §5). Only the active item shows its label in `--brand` with a 2 px top bar. The active item is announced via `aria-current="page"`, not a hard-coded string.
- Bottom nav is 56 px plus the safe-area inset. The Log circle sits within the bar's height and never floats over content.
- **Opening the Log sheet pushes a history entry (`#log`)**, so the Android back button closes the sheet instead of leaving the tab.
- **Desktop (≥1024 px):** the top bar shows `wordmark · Today · Family · Food · You · [+ Log]`. `[+ Log]` is a primary button that opens the same content as a centred dialog (max 480 px).
- **Header (<1024 px, signed in):** 56 px with the wordmark on the left. There is no search row, except on Food and its child routes (search, product, compare, scan), which keep the existing sticky `SearchBar`.

### 2.2 What lives where

| Tab | Contains | Never contains |
|---|---|---|
| **Today** | attention strip · "Your day" tiles · goals (compact) · habits due today · meals today · "This week" teaser · link to all trackers | other members' data; product search; marketing; prompts to add family |
| **Family** | awaiting-confirmation meals · member cards · member view (per-person today, trackers, goals, habits) · "Log for {name}" · a child's profile and access log (guardian) | household totals/averages; rankings; roles; food-readiness setup |
| **Log** (sheet) | tiles: Meal · Water · Activity · Sleep · Mood · Habits · Body* · Readings* (*adult subjects only) · subject switcher | anything that isn't an entry |
| **Food** | the existing food home (categories, rails, scan, label capture) · search · product · compare · recently viewed (`/food/recent`, was `/activity`) · "Your household's food profile" card (moved from the Family overview: readiness plus household allergens) | wellness tracking |
| **You** | profile · sharing & caregivers · shared with you · who viewed my records · privacy choices (consent) · household (members, invite adult, add child, leave) · appearance (dark mode, larger text) · download/delete data · legal · sign out · ops link (operator only) · notifications (W2, only once built) | anything duplicated from Today |

### 2.3 Route and deep-link map

| Route | Screen | Who can reach it | Phase | Notes |
|---|---|---|---|---|
| `/` | Home (signed out). Signed in, redirects to `/today`. | all | W1 | The wordmark links to `/today` when signed in. |
| `/welcome` | Onboarding | signed in, `mode = NONE` | W1 | While `mode` is NONE, every signed-in health route redirects here, carrying `?next=`. |
| `/today` | Today | self | W1 | `?date=YYYY-MM-DD` takes past dates only. The server returns 400 for a future date, and the UI never offers one. |
| `/log/:kind` | Today with the Log sheet open at `kind` | self; `?for=<memberId>` if the caller may manage | W1 | `kind` ∈ water · activity · sleep · body · readings · mood · habit. `meal` goes to `/meals/new`. |
| `/trackers` | Tracker index | self | W2 | |
| `/trackers/:category` | Tracker history | self | W2 | slug ∈ meals · water · activity · sleep · body · readings · mood. Query: `?metric=STEPS&range=30` (range is 7, 30 or 90). |
| `/goals` | Goals | self | W1 exists; split from habits in W2 | |
| `/habits` | Habits | self | W2 | Split out of today's combined Goals and habits page. |
| `/meals/new` | Log a meal | self | W1 exists | `?for=<memberId>` preselects a share. |
| `/meals/:id` | Meal detail | logger or anyone served; 404 otherwise | W1 exists | |
| `/meals` | Meal history | self | W2 | `?filter=awaiting` |
| `/family` | Family overview | household member | W1 | |
| `/family/:memberId` | Member view | any relationship | W1 | NONE renders an identity card only. 404 renders the not-found state. |
| `/family/:memberId/trackers/:category` | Member's tracker history | VIEW on the scope | W2 | Without VIEW, renders the not-shared state (never a 403 screen). |
| `/family/:memberId/goals`, `/family/:memberId/habits` | Member goals and habits | VIEW; editing needs MANAGE | W2 | Replaces `/goals?member=`. |
| `/family/:memberId/profile` | Child profile | GUARDIAN | W1 | Today's `ProfilePanel`, as a route. |
| `/family/:memberId/access-log` | Child's access log | GUARDIAN | W1 | |
| `/food` | Food home | all | W1 | Today's Home content, minus the hero, when signed in. |
| `/search`, `/scan`, `/product/:id`, `/compare` | unchanged | all | existing | |
| `/food/recent` | Recently viewed products | signed in | W1 | Rename of `/activity`, which collides with ACTIVITY tracking. |
| `/you` | You hub | all | W1 | Signed out: sign in/up, appearance, legal. |
| `/you/profile` | Your profile | self | W1 | |
| `/you/sharing` | Sharing & caregivers | self (adult) | W1 | |
| `/you/sharing/:audienceMemberId` | Share with one person | self | W1 | |
| `/you/sharing/help` | Ask someone to help (wizard) | self (adult) | W1 | Prominent for OLDER_ADULT. |
| `/you/shared-with-me` | Shared with you | self | W1 | |
| `/you/access-log` | Who viewed your records | self | W1 | `?kind=` (viewed, logged or refused) and `&actor=<memberId>` |
| `/you/privacy` | Privacy choices | self | W1 | Today's `ConsentPanel` content. |
| `/you/household` | Members and invitations | household member | W1 | Moved from `/family`. |
| `/you/appearance` | Dark mode, larger text | all | W1 | |
| `/you/data` | Download or delete my data | self | existing | |
| `/legal/*`, `/ops*`, `/404` | unchanged | — | existing | |

**Redirects (W1):**
- `/settings` → `/you`
- `/activity` → `/food/recent`
- `/goals?member=:id` → `/family/:id/goals`

**Deep-link rules:**
1. A signed-out visitor who opens a signed-in route goes to `/you?next=<path>`. `next` is honoured only when it is a same-origin relative path starting with `/`, as an open-redirect guard.
2. A signed-in user with `mode = NONE` goes to `/welcome?next=<path>`.
3. Member routes trust the server. On 404, show "We couldn't find this person in your household." and a link back to Family. On NONE, show the identity card.
4. A tracker deep link the caller cannot see renders the **not-shared** state (§4.0), not an error.

### 2.4 Signed-out structure (4 items)

| Slot | Label | Route |
|---|---|---|
| 1 | Home | `/` (search-first marketing home; unchanged) |
| 2 | Search | `/search` |
| 3 | Scan | `/scan` |
| 4 | You | `/you` (sign in / create account, appearance, legal) |

- Anonymous product browsing stays (D-020).
- Home MAY add one card that explains the wellness product ("Track your day, and support your family") with **Create account**. It carries no fake dashboards or sample numbers.
- Signed-out, no health route is reachable except through the sign-in redirect.

### 2.5 Individual and Family lenses (D-043)

`GET /me` returns `mode` ∈ NONE · INDIVIDUAL · FAMILY. The lens decides framing only. The data paths are the same.

| Surface | NONE (no household) | INDIVIDUAL (household of one) | FAMILY (2+ members) |
|---|---|---|---|
| Today | redirect to `/welcome` | full | full |
| Family tab | redirect to `/welcome` | One card: "It's just you for now." "Family members can join your household at any time. Nothing you've logged moves or is shared unless you choose." Two quiet secondary buttons: **Invite an adult** · **Add a child**. Nothing else. | overview |
| Log subject switcher | — | hidden | shown only if the caller manages someone (§2.6) |
| You → Sharing | — | "Sharing works with adults in your household. It's just you for now." No button. | full |
| You → Household | — | members list (you), invite, add child | full |
| Food | works | works | works |

**Never-nag rules (AC-2.7):**
- No count badges, dots or "new" labels on the Family tab.
- No family prompts on Today, in the Log sheet, in onboarding completion, or in notifications.
- No modal asks to add family, ever.
- The only entry points are the Family tab card and You → Household.

**INDIVIDUAL → FAMILY transition.** The first time Family renders in FAMILY mode after being INDIVIDUAL, show a one-time dismissible note at the top of Family (`family.joinedNote`): "{name} joined your household. Nothing you've logged is shared with them. You choose what to share in **Sharing**." Store the dismissal in localStorage per account.

**Several households** (`otherHouseholdCount > 0`): W1 shows the primary household only, plus a quiet line in You → Household: "You're also in {count} other household(s). The app shows {name}." ❓ Q-10 covers switching.

### 2.6 Acting for someone else

The subject switcher appears in the Log sheet header ("Logging for: You ▾"). It also appears implicitly on member views, through the **Log for {name}** button.

| Who appears in the switcher | Source |
|---|---|
| You | always |
| Children you look after | overview `relationship = GUARDIAN` |
| Adults who made you a caregiver | overview `relationship = CAREGIVER` |
| Never | SHARED (view only), NONE, anyone not in the overview |

**When a subject other than you is chosen:**
1. Fetch `/members/{id}/access` (scopes with level) and `/members/{id}/experience` (capabilities).
2. Show only tiles whose scope has MANAGE **and** whose metric is in `capabilities.trackableMetrics`.
3. Show a subject banner at the top of the sheet:
   - Adult: "Logging for {name}. {name} can see everything you log for them in their access log." (`log.forAdultBanner`)
   - Child: "Logging for {name}." (`log.forChildBanner`)
4. If the subject's tracking consent is inactive:
   - Guardian of a child: the child consent sheet (§4.0).
   - Caregiver of an adult: "{name} has tracking turned off, so nothing can be logged for them right now." There is no action, because only {name} can turn it on.

### 2.7 Data dependencies (one source per screen, and no probing)

| Screen | Reads | MUST NOT |
|---|---|---|
| Shell, tier, lens | `GET /me` (staleTime 5 min; invalidate after profile, consent or household changes) | derive tier from `ageBand` or `memberType` |
| Today | `/members/{me}/today?date=` · `/members/{me}/meals?from=&to=` (day, household zone) · `/insights/members/{me}?window=WEEK` (teaser) | sum observations client-side; compute day boundaries from the device clock |
| Family overview | `/households/{id}/overview?date=` · `/members/{me}/meals` for the awaiting list | call `/goals/progress`, `/habits` or `/visibility` per member to "see what happens" |
| Member view | overview row (already loaded) plus `/members/{id}/today` only if `visibleScopes` is non-empty · tracker history only for visible scopes | request a scope not in `visibleScopes` |
| Log sheet | `/me` · for another subject: `/members/{id}/access` and `/members/{id}/experience` | offer a tile the capabilities don't list |
| Sharing | `/access/scopes` (`offered = true` only) · `/members/{me}/access-grants` · household members (adults) | list children as audiences; show not-offered scopes |
| Meal detail | `/nutrition/meals/{id}`, using `nutritionWithheld` per portion | call `/visibility` per share |

---

## 3. Tier-specific experiences (D-046)

### 3.1 Tier matrix (what the UI renders)

| Surface | ADULT | OLDER_ADULT | CHILD (seen by guardian) | UNKNOWN |
|---|---|---|---|---|
| Home (Today) order | attention · your day · goals · habits · meals · this week | helpers · [W4 care items] · your day (1 column) · habits · goals · meals · this week | No Today. The child is reached through Family → member view. | profile card · habits · Food shortcut |
| Log tiles | all 8 | all 8, in a single-column list with text labels | Meal · Water · Activity · Sleep · Mood · Habits | Habits only, plus a "profile needed" row |
| Body and Readings | yes | yes | **absent** (not disabled, not explained inline) | absent until a profile exists |
| Goals offered (W2 UI) | from `capabilities.goalCategories/goalDirections`, with the W2 UI subset in §4.7 | same as adult | ACTIVITY, SLEEP, HYDRATION · "At least" / "Around" | none (`goalCategories` empty) |
| Habits | yes | yes | yes, called "Routines" in copy | yes |
| Larger text | You → Appearance | offered once on Today; also You → Appearance | follows the guardian's setting | You → Appearance |
| Caregiver invitation | You → Sharing | Today card plus the top of Sharing (`caregiverInviteProminent`) | n/a (child data is never grantable) | You → Sharing |
| Streaks, badges, challenges | none (W6 opt-in) | none (W6 opt-in) | **never** | never |
| Notifications | W2 opt-in | W2 opt-in | never to the child; the guardian receives them (D-050) | W2 opt-in |
| Copy register | plain, factual | plain, factual; never mentions age | encouraging, non-evaluative (§5.4) | plain |

### 3.2 ADULT

This is the default experience specified in §4. Adult-only surfaces:
- Body and Readings log tiles.
- BODY goals, with the D-047 guidance note.
- Sharing and caregiver grants.

### 3.3 OLDER_ADULT (care-first, not restricted)

| Capability flag | What renders |
|---|---|
| `careFirstHome` | Today reorders. (1) **People who help you** card. (2) The W4 care block (medicines, appointments, reminders) is **not rendered until W4 ships**, with no placeholder. (3) The day summary as a single column of larger tiles. (4) Habits. (5) Goals. (6) Meals. |
| `largerTextSuggested` | A one-time card at the top of Today, `today.largerTextOffer`: "Would you like larger text?" with **Make text larger** / **No thanks** (same size). The choice persists in localStorage (Q-12) and can be changed any time in You → Appearance. |
| `caregiverInviteProminent` | Today "helpers" card, when nobody helps yet: "Would you like a family member to help you keep track?" **Ask someone to help** opens `/you/sharing/help`. Once someone helps: "{name} helps you with Water, Activity." plus **See what {name} has done** (access log filtered by actor) and **Change**. |

**Simpler flows. The capabilities are the same; the effort is lower.**

| Rule | Detail |
|---|---|
| Steppers | Every numeric entry gets − / + buttons (56 px in larger text) beside the keyboard field. |
| No gesture-only actions | Sheets have a visible **Close** button. No swipe-to-delete. No long-press. |
| Messages persist | Save confirmations stay until the next action or dismissal. They don't auto-dismiss (WCAG 2.2.1). |
| Plain names | "Blood pressure", not "BP". "Blood sugar (glucose)", not "Glucose". |
| One primary action per view | The Log entry form shows its value field and one Save. Time sits behind "Change". |

**Never:**
- remove a capability;
- label a mode "senior";
- say "because of your age";
- show another adult's age band. The tier is visible only to self, guardian and caregiver (ExperienceService).

Mini-sketch of Today in the OLDER_ADULT order (larger text on):

```
┌────────────────────────────────────┐
│ Today              Sat, 12 Sep     │
│ ┌────────────────────────────────┐ │
│ │ Would you like larger text?    │ │
│ │ [ Make text larger ][No thanks]│ │
│ └────────────────────────────────┘ │
│ ┌ People who help you ───────────┐ │
│ │ Rohan helps you with Water,    │ │
│ │ Activity.                      │ │
│ │ See what Rohan has done     ›  │ │
│ └────────────────────────────────┘ │
│ Your day                           │
│ ┌────────────────────────────────┐ │
│ │ Water                     [ + ]│ │
│ │ 1 L  · 4 entries               │ │
│ └────────────────────────────────┘ │
│ ┌────────────────────────────────┐ │
│ │ Blood pressure            [ + ]│ │
│ │ 132/86 mmHg · at 8:05 am       │ │
│ └────────────────────────────────┘ │
│ … habits, goals, meals             │
└────────────────────────────────────┘
```

### 3.4 CHILD, as seen by a guardian

A child never signs in. Every child surface is a guardian's view of a child member.

| Area | Rule |
|---|---|
| Member view sections, in order | **Routines** (habits) · **Meals** · **Activity** · **Sleep** · **Water** · **Mood** |
| Body and Readings | Absent everywhere: no tile, no section, no disabled control. One line in the member view's "About {name}'s records" info sheet: "We don't record weight, height or health readings for children. This is a deliberate choice about children's data." |
| Goals | Only ACTIVITY, SLEEP and HYDRATION. Directions: "At least" / "Around". No "At most". |
| Goal progress copy | "{actual} {unit} logged · aim: at least {target} {unit}". **No "Reached", no "Above"** and no bar colour change. Progress bars are allowed as a glance aid (hidden from AT). |
| Energy (kcal) | Not a headline (UXD-08). The Meals tile says "{n} meals logged". Meal detail shows the child's share as recorded. |
| Mood | "How did {name} seem today?" · 5 labelled buttons (§4.3) · helper text: "This is your impression, not {name}'s own rating." |
| Stress | Not offered for a child in the UI (❓ Q-03). |
| Habits | Guardian ticks. Copy: "Fruit at breakfast · done on 4 of 7 days". Never "only", "missed", "good job" or "well done". |
| Mechanics | No streaks, badges, celebrations, "days in a row" or animations on completion. |
| Consent | CHILD_WELLNESS_TRACKING is guardian-granted with attestation (PROVISIONAL, D-001/D-048). |
| Access log | The guardian sees "Who viewed {name}'s records" in the member view. |
| Devices and imports | Never offered for a child (D-048 §4). |
| Non-guardian adult's view | Identity card only: "{name} · Their guardians look after their records." |

### 3.5 UNKNOWN (profile needed)

| Surface | Render |
|---|---|
| Today | A profile card replaces "Your day". Title `today.profileNeeded.title`: "Add your age band to start tracking". Body: "It tells us which measurements apply to you. We never ask for your date of birth." Action: **Add age band** opens a sheet with age band (required) and activity level (optional). Habits render below it (`capabilities.habits = true`). |
| Log sheet | Habits tile only. Other tiles are replaced by one row: "Add your age band to log measurements ›". |
| Goals | "Goals need your age band first." plus the same action. |
| Family (others' view) | Normal card. Others never learn that the profile is missing. |

### 3.6 What others learn about a member's tier

A SHARED or NONE viewer is told only CHILD or ADULT (`ExperienceService.visibleTier`). The UI MUST NOT render an age band or older-adult framing on another adult's card, even for a caregiver. Tier drives layout only and never appears in text.

---

## 4. Screen specifications

### 4.0 Shared state patterns

Every screen composes these patterns and none of them is re-invented per screen. Components marked † are new (Appendix A).

| State | Trigger | Component | Rendering | Copy (proposed) |
|---|---|---|---|---|
| **Loading** | query pending | `Skeleton` sized to the final boxes, `aria-busy` on the region | Same geometry as the loaded content, so there is no layout shift. | sr: `loading.default` |
| **Empty** | a list exists but has no items (no goals, habits or meals) | `EmptyState` | One sentence plus one action. No illustration. | e.g. "No goals yet. A goal is a number you choose, like how much water to drink in a day." |
| **Not logged yet** | metric value `null` for the window | `StatusLine`† (muted) | Text "Not logged" in `--text-tertiary`. No 0, no empty bar, no dash. The tile keeps its "+" action. | `status.notLogged`: "Not logged" · sr: "{metric}: not logged today" |
| **Not shared with you** | scope not in `visibleScopes` / `nutritionWithheld` | `StatusLine`† | One plain line with no padlock icon and no blurred placeholder. Page level adds a sentence. | inline: "Not shared with you" · page: "{name} hasn't shared {category} with you. Only {name} can change that." |
| **Consent needed** | `trackingConsent.active = false`, never granted, or `CONSENT_REQUIRED` on a write | `ConsentSheet`† (adult or child variant) plus a Today card | A calm card with one action. The sheet opens on the first log attempt. | §4.0.1 |
| **Consent withdrawn** | purpose status WITHDRAWN or `CONSENT_WITHDRAWN` | card | Own history stays readable. Log actions open `ConsentSheet`. | "Tracking is off. You turned it off on {date}. What you logged before is still here to see, download or delete. While tracking is off, people you share with can't see your records." **Turn tracking back on** |
| **Profile needed** | tier UNKNOWN / `PROFILE_REQUIRED_FOR_METRIC` | card or sheet | §3.5 | §3.5 |
| **Awaiting confirmation** | portion `inMemberLog = false` | `PendingChip`† | Ellipsis glyph plus text on `--surface-sunken`. Never an error colour. | "Waiting for {name}" / "Waiting for you" |
| **Error (service)** | network or 5xx | `ErrorState` | Distinguishes *unavailable* from *not found*. Retry when retryable. Sections fail in place; the page never blanks. | existing `error.*` |
| **Not found** | 404 on member or meal | `ErrorState` (not-found variant) | "We couldn't find this. It may have been removed, or it isn't shared with you." plus a link back. | `error.not_found_or_private` |
| **Offline** | `navigator.onLine === false` or a fetch TypeError | `OfflineBanner`† (sticky under the header, `role="status"`) | Cached content stays, with "Last updated {time}". Every Save is disabled with a reason, and nothing is queued (UXD-13). | "You're offline. Showing what was last loaded at {time}." · Save hint: "You're offline, so nothing can be saved right now." |

#### 4.0.1 Consent sheet (D-048)

**Adult (HEALTH_TRACKING)**

| Element | Content |
|---|---|
| Title | "Turn on tracking?" |
| Body (3 bullets) | "We store what you log (meals, water, activity, sleep, body measurements and health readings) so you can see your own history." · "Only you can see it unless you share it with someone in your household." · "You can turn tracking off any time. What you logged stays for you to see, download or delete." |
| Meta | Policy version, plus a link to "Read the privacy notice" (`/legal/privacy#HEALTH_TRACKING`). |
| Actions | **Turn on tracking** (primary) and **Not now** (secondary, bordered). Both are full-width and the same size. |
| After grant | Return to the entry the person was making, with their value preserved. |
| After "Not now" | Close the sheet. Nothing is saved. A Today card shows the consent-needed state. |

**Child (CHILD_WELLNESS_TRACKING)**

| Element | Content |
|---|---|
| Title | "Turn on tracking for {name}?" |
| Body | "We store what you log for {name}: meals, water, activity, sleep, mood and routines. We never record weight, height or health readings for a child. Only {name}'s guardians can see it." |
| Attestation | Checkbox using the existing `consent.guardianAttestation`, with the existing `consent.guardianAttestationNote` always shown. |
| Actions | **Turn on tracking** (disabled until the box is ticked) · **Not now** |

### 4.1 Onboarding `/welcome` (W1)

**Entry:**
- after sign-up;
- any signed-in health route while `mode = NONE`.

It resumes at the first incomplete step (`?step=`).

| Step | Content | API | Skippable |
|---|---|---|---|
| 1. How will you use it? | Two equal cards. **Just me**: "Track your own day. You can add family later, and nothing you log moves." **Me and my family**: "Set up a household, then invite adults and add children." Below them, a text link: "I have an invite code". | — | no |
| 2a. Just me | "What should we call you?" (display name, 1–60 chars) | `POST /me/individual {displayLabel}` → 201 or 200 (idempotent; 200 means already provisioned, continue silently) | no |
| 2b. Family | Household name ("For example, Sharma family") plus your name | existing create household | no |
| 2c. Invite code | Code plus your name | existing accept invitation | no |
| 3. About you | Age band (required to track; the existing `ageBand.*` labels) · activity level (optional; "We use this only to describe your day, never to set a calorie target.") | profile PUT | **Skip for now** leads to UNKNOWN |
| 4. Tracking | Inline ConsentSheet content (§4.0.1), not a modal | consent grant | **Not now** |
| 5. (Family only) Add people now? | **Invite an adult** · **Add a child** · **Later** (all equal) | existing | yes |
| Done | Go to `next` or `/today`. No celebration screen. | — | — |

**Layout:** one step per screen, a progress text ("Step 2 of 4") in the header, and the primary action pinned above the keyboard. Back is always available.

**Never collected in onboarding:** date of birth, weight, height, goals, health conditions.

| State | Behaviour |
|---|---|
| Loading | Button spinner with its label kept. |
| Error | Inline `ErrorState` under the form. Input is kept. |
| Offline | Continue is disabled, with the offline hint. |
| Already has a household (200) | Skip to step 3. |
| Consent needed / withdrawn | Step 4 *is* the consent step. |
| Not logged / not shared | n/a |

### 4.2 Today `/today` (W1) — wireframe 1

```
┌──────────────────────────────────────────┐
│ ◆ Wordmark                               │ header 56px, no search
├──────────────────────────────────────────┤
│ Today                 ‹  Sat, 12 Sep  ›  │ h1 + DayNavigator (› disabled today)
│                                          │
│ ┌──────────────────────────────────────┐ │ AttentionStrip (0–2 rows)
│ │ … Your share of Dinner is waiting.   │ │
│ │   Add it to your log              ›  │ │
│ └──────────────────────────────────────┘ │
│                                          │
│ Your day                                 │ h2
│ ┌──────────────────┐ ┌─────────────────┐ │ MetricTile grid, 2 cols
│ │ Meals        [+] │ │ Water       [+] │ │ (+) = separate 44px button
│ │ 1,420 kcal       │ │ 1.5 L           │ │
│ │ from 3 meals     │ │ of 2 L you aim  │ │
│ └──────────────────┘ └─────────────────┘ │
│ ┌──────────────────┐ ┌─────────────────┐ │
│ │ Activity     [+] │ │ Sleep       [+] │ │
│ │ Not logged       │ │ 7 h 20 min      │ │
│ │                  │ │ woke 6:40 am    │ │
│ └──────────────────┘ └─────────────────┘ │
│ ┌──────────────────┐                     │ extra tiles only if logged
│ │ Weight       [+] │                     │ today or a live goal exists
│ │ 71.2 kg          │                     │
│ │ at 7:05 am       │                     │
│ └──────────────────┘                     │
│                                          │
│ Your goals                    Manage  ›  │
│  Water · at least 2 L each day           │
│  1.5 of 2 L   ▓▓▓▓▓▓▓▓▓░░░               │ bar aria-hidden
│  Steps · at least 8,000 each day         │
│  Nothing logged today yet                │
│                                          │
│ Habits today              2 of 3 done  › │
│  [✓] Walk after dinner                   │ 44px toggles
│  [ ] Fruit at breakfast                  │
│                                          │
│ Meals today                              │
│  Breakfast · 8:30 am · your share 420 kcal›
│  Lunch · 1:15 pm · your share 610 kcal  ›│
│                                          │
│ This week                                │ insights teaser (max 2)
│  Water logged on 5 of 7 days.            │
│                                          │
│ All trackers                          ›  │ W2
├──────────────────────────────────────────┤
│ Today   Family   ( + )   Food    You     │
│                   Log                    │
└──────────────────────────────────────────┘
```

| Region | Spec |
|---|---|
| Header | h1 "Today" (a past date shows "Thursday, 10 Sep" plus a **Back to today** link). The `DayNavigator`† has ‹ and › buttons (44 px) and `?date=`. › is disabled on today. Dates come from the household zone. If the device zone differs, one muted line: "Times shown in {zoneLabel}." |
| Attention strip | At most 2 rows, in priority order: (1) consent needed or withdrawn, (2) meals awaiting you (`mealsAwaitingConfirmation` is a count only, so the tap goes to the Family "Waiting for you" card in W1 and to `/meals?filter=awaiting` in W2; if today's meal list already shows the single waiting meal, link it directly), (3) profile needed. Styling: `--brand-subtle` background, 4 px `--brand` left rule, text in `--text`. Never `--danger`. |
| Your day tiles | **Default set, always shown, in order:** Meals (ENERGY_KCAL plus the meal count), Water, Activity (STEPS, falling back to ACTIVE_MINUTES if only that was logged), Sleep (SLEEP_MINUTES plus wake time). **Extra tiles** after those, in category order, for any metric that was logged today or has a live goal (Weight, BP, Mood…). **The server supplies the value and aggregation (UXD-07).** |
| Tile anatomy | Label (`--text-sm`, secondary) · value (`--text-h1`, 700) plus unit (`--text-sm`) · sub-line (goal context, time of a LATEST reading, or "Not logged"). The whole tile is a link to the tracker history (W2; W1 has no link). The **[+]** is a sibling button, not nested, with label "Log {metric}". |
| Meals tile honesty | Counts only shares in your log. If a share is waiting, the sub-line reads "{n} meals · 1 waiting for you". If energy is unknown (custom food without nutrition), it reads "{n} meals · energy not known". |
| Goals | `GoalProgressList` in compact form, using `/today` `goals[]`. Display rules in §6.5. **Manage** goes to `/goals`. |
| Habits | "Habits today · {done} of {due} done" (from `/today` `habits`). W1: the tick list needs per-habit done-today data in one request (❓ Q-13). Until then, show the count row linking to the Log sheet's Habits panel, and no per-habit N+1 fetching. |
| Meals today | Rows for "{name or type} · {time} · your share {kcal} kcal", or with the pending chip. Tap goes to meal detail. |
| This week | At most 2 insights, no "see all" (the W3 hub doesn't exist yet). The quiet `insight.unavailable` failure stays. **D-019 gap:** see §8 G-13. |
| Footer | "All trackers ›" (W2). |

| State | Today behaviour |
|---|---|
| Loading | Skeleton: header, 4 tiles and 3 list rows. |
| Empty (new user, consent on) | All tiles read "Not logged". Hint under the tiles: "Tap **Log** to add your first entry." Goals and habits sections show their empty states. |
| Not logged yet | Per tile (§4.0). |
| Not shared with you | n/a (self). |
| Consent needed | The "Your day" section is replaced by a consent card: "Turn on tracking to log your day." **Turn on tracking** opens the ConsentSheet. Food shortcut: "You can still check products in **Food**." |
| Consent withdrawn | The withdrawn card sits above the tiles. Tiles still show that day's data (own reads survive withdrawal). [+] opens the ConsentSheet. |
| Error | Per section. A tile-grid failure shows one `ErrorState` for the grid; the other sections render. |
| Offline | Banner. Cached day with "Last updated". [+] buttons are disabled with the hint. |
| Past date | Same layout. Logging from a past day pre-fills that date (the time chip reads "For Thu, 10 Sep · Change"). |

### 4.3 Log sheet (W1 for water, activity, sleep, body, readings, mood, habit; W2 polish) — wireframe 2

```
┌──────────────────────────────────────────┐
│                  ────                    │ decorative handle
│ Log                               Close  │ h2 + visible Close button
│ Logging for  [ You ▾ ]                   │ SubjectSwitcher (hidden if only you)
│ Today, 8:10 pm · Change                  │ time chip (household zone)
│                                          │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐   │ tiles 3-up (2-up <360px;
│ │  Meal    │ │  Water   │ │ Activity │   │ list layout in larger text)
│ └──────────┘ └──────────┘ └──────────┘   │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│ │  Sleep   │ │  Mood    │ │ Habits   │   │
│ └──────────┘ └──────────┘ └──────────┘   │
│ ┌──────────┐ ┌──────────┐                │ adult subjects only;
│ │  Body    │ │ Readings │                │ absent for a child
│ └──────────┘ └──────────┘                │
└──────────────────────────────────────────┘

after tapping Water:
┌──────────────────────────────────────────┐
│ ‹ Water                           Close  │
│ How much?                                │
│ [ 150 ml ] [ 250 ml ] [ 500 ml ] [Other] │ PresetChips (radio group)
│                                          │
│ Today, 8:10 pm · Change                  │
│ ┌──────────────────────────────────────┐ │
│ │            Save 250 ml               │ │ primary, echoes value (UXD-14)
│ └──────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

**Sheet behaviour:**
- `BottomSheet` with a real focus trap, inert background, Esc, a visible **Close** button and `#log` history.
- Tile to form is an in-sheet push, with ‹ back.
- On success the sheet returns to the tile grid with a `role="status"` line: "Saved 250 ml water for today." It then invalidates `/today` for the subject and the goals queries.
- It stays open for the next entry.

**Tiles shown** = kinds whose metrics intersect the subject's `capabilities.trackableMetrics`, filtered for non-self subjects by MANAGE scopes.

| Kind | Metrics written | Controls | Defaults | Validation (client; the server re-checks) | Save label |
|---|---|---|---|---|---|
| **Meal** | NUTRITION (derived) | navigates to `/meals/new` (full screen; the sheet closes) | subject preselected in "Who ate it" | — | — |
| **Water** | WATER_ML | presets 150 / 250 / 500 ml plus **Other** (numeric, ml) | 250 ml selected | > 0 | "Save {n} ml" |
| **Activity** | STEPS · ACTIVE_MINUTES · WORKOUT_MINUTES · DISTANCE_KM | segmented "Steps / Active minutes / Workout / Distance" plus a numeric field with `Stepper`† | Steps | integer ≥ 0 (steps, minutes); distance ≥ 0, 2 dp | "Save 6,200 steps" |
| **Sleep** | SLEEP_MINUTES (+ BEDTIME_OFFSET_MIN, WAKE_OFFSET_MIN when times are given) | "Went to bed" time · "Woke up" time give a computed duration ("7 h 20 min"), editable as hours plus minutes | wake date = the selected day (UXD-15) | duration > 0 and ≤ 24 h; the bedtime may be on the previous calendar day | "Save 7 h 20 min" |
| **Mood** | MOOD_SCORE (+ STRESS_SCORE for adults, optional) | 5 labelled buttons (not emoji-only). Mood: 1 "Very low", 2 "Low", 3 "Okay", 4 "Good", 5 "Very good". Stress: 1 "Very calm" … 5 "Very stressed". | none selected | one choice | "Save mood" |
| **Habits** | habit completions | due-today habits as 44 px toggle rows; optimistic with rollback | — | — | (instant per toggle; `role="status"` "Walk after dinner: done today") |
| **Body** (adult) | WEIGHT_KG · HEIGHT_CM · WAIST_CM · BODY_FAT_PCT | segmented plus a numeric field with stepper | Weight | > 0; 1 dp | "Save 71.2 kg" |
| **Readings** (adult) | SYSTOLIC_MMHG + DIASTOLIC_MMHG (paired) · RESTING_HEART_RATE_BPM · BLOOD_GLUCOSE_MGDL · SPO2_PCT · BODY_TEMPERATURE_C | segmented "Blood pressure / Heart rate / Blood sugar / Oxygen / Temperature". BP has two fields ("Top number", "Bottom number") saved together with one `effectiveAt`, plus optional heart rate. Temperature has a °C/°F input toggle converted to °C at the edge (❓ Q-11). | Blood pressure | integers; both BP numbers required together | "Save 128/84 mmHg" |

**Static note on the Readings form and on Readings history.** It is identical for every value and never emphasised:

> "We show your readings exactly as you record them. We don't interpret them. Your doctor can tell you what they mean for you." (`readings.note`)

**BMI** is never a field. It appears in Body history only if the server returns a BMI observation (❓ Q-15).

**Time chip.** "Today, 8:10 pm · Change" opens a date+time picker. Past dates and times are allowed up to now, in the household zone. Future times are refused client-side.

| Error from server | Rendering |
|---|---|
| `CONSENT_REQUIRED` / `CONSENT_WITHDRAWN` | Open the ConsentSheet. The typed value is preserved. |
| `PROFILE_REQUIRED_FOR_METRIC` | The profile sheet (§3.5). |
| `METRIC_NOT_PERMITTED_FOR_AGE` | Should be unreachable because tiles are filtered. Show the existing localised message inline. |
| `ACCESS_DENIED` (e.g. caregiver access revoked mid-session) | "You can no longer log for {name}. {name} changed what they share with you." Then refetch the overview and reset the switcher to You. |
| validation | Inline, bound with `aria-describedby`. |
| network / 5xx | Inline `ErrorState` with retry. The value is preserved. |

| State | Log sheet behaviour |
|---|---|
| Loading | Tile skeleton (3×3). |
| Empty | n/a. With no trackable metrics (UNKNOWN), §3.5 applies. |
| Not logged / not shared | n/a. |
| Consent needed / withdrawn | Tiles are visible. Tapping one opens the ConsentSheet. |
| Error | Capability fetch failed: `ErrorState` in place of the tiles. Never "nothing to log". |
| Offline | Tiles are visible and Save is disabled with the offline hint. |

### 4.4 Family overview `/family` and member view `/family/:memberId` (W1) — wireframe 3

```
┌──────────────────────────────────────────┐
│ Family                     Sat, 12 Sep   │ h1 + date (read-only in W1)
│ Sharma family                            │ household name
│                                          │
│ ┌ Waiting for you ─────────────────────┐ │ own awaiting meals only
│ │ Your share of 2 meals isn't in your  │ │
│ │ log yet.                             │ │
│ │ Dinner, Fri · Lunch, Sat     Review ›│ │
│ └──────────────────────────────────────┘ │
│                                          │
│ ┌──────────────────────────────────────┐ │ MemberCard: identical anatomy
│ │ (M) Mohan                      You   │ │ RelationshipChip
│ │ Meals 1,420 kcal · Water 1.5 L       │ │
│ │ Activity not logged · Sleep 7 h 20 m │ │
│ │ Habits 2 of 3 done                   │ │
│ │                    [ Log ] [ Open › ]│ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ (A) Aarav              You look after│ │ child: no kcal headline
│ │ Meals 3 logged · Water 750 ml        │ │
│ │ Activity 45 min · Sleep not logged   │ │
│ │ Routines 1 of 2 done                 │ │
│ │                    [ Log ] [ Open › ]│ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ (P) Priya            Shares with you │ │ SHARED
│ │ Activity 6,200 steps · Sleep 6 h 50 m│ │ visible scopes only
│ │ Other records aren't shared with you.│ │
│ │                             [ Open ›]│ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ (R) Rohan                            │ │ NONE: identity only
│ │ Hasn't shared anything with you.     │ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ (A) Amma                    You help │ │ CAREGIVER (server order,
│ │ Water 1 L · Activity not logged      │ │ by joinedAt, never by data)
│ │ You can log: Water, Activity         │ │
│ │                    [ Log ] [ Open › ]│ │
│ └──────────────────────────────────────┘ │
│                                          │
│ Manage household                      ›  │ → /you/household
└──────────────────────────────────────────┘
```

**Amended by D-054 (2026-09-13).**
- The page adds, above the cards:
  - a date navigator (`?date=`);
  - a "Waiting for you" strip;
  - a household summary, whose rules are in §6.7.
- Beside the cards sits a "Latest updates" feed, ordered by time.
- Each card row shows today's value and a 7-day strip, with a compact goal meter under the row.
- Habits always keep their own row.
- Members who share nothing are listed once, quietly, below the cards.
- The wireframe above shows the W1 baseline.

**Card rules (UXD-18):**
- Server order: you, then children you guard, then others by `joinedAt`. **The client never re-sorts.**
- The same card height rules apply to every member, whatever their data.
- No totals, averages or comparison across cards.
- No per-member counts that invite comparison. Drop today's "Ate in this week's meals: {n}".

| Relationship | Chip (`access.relationship.*`) | Body | Actions |
|---|---|---|---|
| SELF | "You" | up to 4 metric lines (Meals · Water · Activity · Sleep) plus habits | **Log** (opens the sheet for you) · **Open** |
| GUARDIAN (child) | "You look after" | Meals as a count, not kcal (UXD-08) · Water · Activity · Sleep · Routines | **Log** (sheet for the child) · **Open** |
| CAREGIVER | "You help" | visible-scope lines, then "You can log: {manage scopes}" | **Log** (manage scopes only) · **Open** |
| SHARED | "Shares with you" | visible-scope lines, then "Other records aren't shared with you." (only if some tracking scopes aren't visible) | **Open** |
| NONE (adult) | none | "Hasn't shared anything with you." | none. The card is not a link. |
| NONE (child) | none | "Their guardians look after their records." | none |
| any, `trackingConsentActive === false` | as above | GUARDIAN: "Tracking isn't turned on for {name}." plus **Turn on**. Other relationships: no consent statement (only {name} can act, and `null` means you aren't told). | — |

Metric line format: "{Metric} {value}" or "{Metric} not logged". Values come from overview `today[]` (MetricDay) with SUM/LATEST applied by the server. At most 4 lines, in a fixed category order: Meals · Water · Activity · Sleep · Mood · Body · Readings. Show only the first 4 visible.

**Member view `/family/:memberId`:**

| Region | Content |
|---|---|
| Header | Avatar initial · name · relationship chip · "What you can see" row listing the visible scope names, each with a "can log" suffix where MANAGE. |
| Caregiver banner (CAREGIVER) | "You're helping {name} with {scopes}. {name} can see everything you do for them in their access log." |
| Today for this member | Same `MetricTile` grid as Today, limited to visible scopes. Child: the Meals tile shows a count. [+] appears only where MANAGE. |
| Sections | Routines/Habits (if HABITS visible) · Goals (for goals whose metric scope is visible) · Meals (W2) · each visible category linking to `/family/:id/trackers/:category` (W2). |
| Not-shared footer | "{name} hasn't shared their other records with you. Only {name} can change that." (SHARED/CAREGIVER only) |
| Guardian-only (child) | **Profile** → `/family/:id/profile` · **Who viewed {name}'s records** → `/family/:id/access-log` · **About {name}'s records** (info sheet, §3.4) · **Tracking for {name}** (consent status) |

| State | Family overview / member view |
|---|---|
| Loading | 3 card skeletons. |
| Empty (INDIVIDUAL) | §2.5 "just you" card. |
| Not logged yet | Per line. |
| Not shared with you | Per card and page (above). |
| Consent needed (self) | Your card: "Tracking is off." plus **Turn on** (ConsentSheet). |
| Consent needed (guarded child) | Child card line plus **Turn on** (child ConsentSheet). |
| Consent withdrawn | Same as needed, with the withdrawn copy. Your past data remains in your own view. |
| Error | One `ErrorState` for the overview. Retry. |
| Offline | Banner plus cached overview. **Log** buttons disabled. |
| Not found (member view) | §2.3 rule 3. |

### 4.5 Sharing & caregivers, Shared with you, Access log (W1) — wireframe 4

```
/you/sharing/:audienceMemberId
┌──────────────────────────────────────────┐
│ ‹ Sharing                                │
│ Share with Priya                         │ h1
│ Choose what Priya can see. Only you can  │
│ change this, and you can stop any time.  │
│                                          │
│ Food and nutrition                       │ fieldset legend
│ Meals you ate and what they came to.     │ description
│ ( Not shared | Can see | See and log )   │ ScopeLevelControl (radiogroup)
│                                          │
│ Water                                    │
│ ( Not shared | Can see | See and log )   │
│ Activity · Sleep · Mood and stress ·     │
│ Habits · Body measurements               │ …same pattern
│                                          │
│ Health readings                          │
│ Blood pressure, heart rate, blood sugar, │
│ oxygen and temperature.                  │
│ ( Not shared | Can see | See and log )   │
│                                          │
│ [        Review changes (3)          ]   │ sticky; disabled until changed
└──────────────────────────────────────────┘

Review sheet
┌──────────────────────────────────────────┐
│ Review changes for Priya                 │
│ Priya will be able to see:               │
│   Activity, Sleep                        │
│ Priya will be able to see and log for    │
│ you:  Water                              │
│ Priya will no longer see:                │
│   Mood and stress                        │
│ Goals on these are included. Everything  │
│ Priya views or logs appears in your      │
│ access log.                              │
│ [ Confirm ]            [ Back ]          │
└──────────────────────────────────────────┘

/you/access-log
┌──────────────────────────────────────────┐
│ Who viewed your records   Last 90 days   │
│ [All] [Viewed] [Logged] [Not allowed]    │ filter chips (radio)
│ Today                                    │
│  Priya viewed your Activity, Sleep       │
│  as someone you share with     8:10 pm   │
│ Thu, 10 Sep                              │
│  Rohan added a Water entry for you       │
│  as your caregiver             7:02 am   │
│  Rohan tried to open your Body           │
│  measurements. It isn't shared  6:58 am  │
└──────────────────────────────────────────┘
```

**`/you/sharing` (hub):**

| Section | Content |
|---|---|
| Top card (prominent when `caregiverInviteProminent`) | "Ask someone to help": "A family member can log things for you, like water or activity, and you'll see everything they do." Goes to `/you/sharing/help`. |
| Shared by you | One row per active **adult** member of the household (not children, not you): "{name} · Can see: Activity, Sleep · Can see and log: Water", or "Not sharing anything". The row goes to the per-person screen. |
| Shared with you | Link to `/you/shared-with-me` with a count sentence: "2 people share with you." |
| Who viewed your records | Link to `/you/access-log`. |
| Always-visible facts (D-045) | "Only you can change who sees your records." · "People you share with can't pass your records on, download your data or delete your account." · "Children's records are seen only by their guardians and can't be shared." |

**Per-person screen:**
- Scopes come from `/access/scopes` with `offered = true`, in this order: NUTRITION, HYDRATION, ACTIVITY, SLEEP, WELLBEING, HABITS, BODY, CLINICAL.
- **Not-offered scopes are not shown**, and there are no "coming soon" labels.
- The level control is a 3-option radiogroup (`ScopeLevelControl`†). It sits full width below each label so it fits 360 px. Arrow keys move and space selects.
- Choosing "See and log" shows the helper line "They can add entries for you. You'll see each one in your access log."

**Apply:**
- **Review changes** opens the summary sheet, then **Confirm** applies the grants.
- Revoking is DELETE. Granting is POST. A level change is a sequence (❓ Q-08 on atomicity).
- On partial failure, the sheet lists which changes applied and which didn't, and offers Retry. It never claims all-or-nothing when that isn't true.
- The **Stop sharing everything with {name}** link at the bottom takes one ConfirmSheet and applies immediately.

| `ACCESS_GRANT_NOT_ALLOWED` reason | Copy (`sharing.error.*`) |
|---|---|
| SCOPE_NOT_OFFERED | "This can't be shared yet." |
| AUDIENCE_NOT_FOUND | "{name} is no longer in your household." |
| SELF_GRANT | (unreachable; generic error) |
| NOT_SAME_HOUSEHOLD | "You can only share with people in your household." |
| AUDIENCE_NOT_ADULT | "Sharing is between adults. Children's records are seen by their guardians." |

**Ask-for-help wizard `/you/sharing/help`:**
1. **Who?** Adults in the household, or **Invite someone new** (the existing invite flow, then back here after they join).
2. **What can they help with?** Scope checkboxes, all unticked by default, granting MANAGE.
3. **Review.** "Rohan will be able to see and log these for you: Water, Activity. You can stop this any time. Everything Rohan does shows in your access log." **Confirm**.
4. Done. For OLDER_ADULT add: "Set this up on someone else's phone? Sign out when you're done: You → Sign out." (D-045 shared-device path)

**`/you/shared-with-me`:** rows reading "{name} shares with you: Activity, Sleep (can see) · Water (can see and log)". Each row goes to `/family/:id`. Empty state: "No one has shared anything with you yet."

**Access log `/you/access-log` (and `/family/:childId/access-log` for guardians):**

| Field | Rendering |
|---|---|
| Grouping | By day, household zone, newest first. Window label "Last 90 days". |
| VIEWED | "{actor} viewed your {scopes}" (child: "{actor} viewed {name}'s {scopes}") |
| RECORDED | By `resourceType`. OBSERVATION: "{actor} added a {scope} entry for you" · MEAL: "{actor} logged a meal that included you" · GOAL: "{actor} changed a {scope} goal for you" · HABIT: "{actor} updated a habit for you" |
| REFUSED | "{actor} tried to open your {scopes}. It isn't shared with them." Neutral text, `--text`, no warning icon or colour. |
| Relationship suffix | "as your caregiver" · "as someone you share with" · "as {name}'s guardian" · none when absent |
| Actor unknown (`actorMemberId = null`) | "Someone no longer in your household" |
| Filter | Chips All / Viewed / Logged / Not allowed (`?kind=`) · optional `?actor=` from the helper card |
| Empty | "No one else has viewed or logged anything for you in the last 90 days." |

| State | Sharing / Access log |
|---|---|
| Loading | Row skeletons. |
| Empty | INDIVIDUAL: §2.5 line. No adults besides you: "Sharing is between adults in your household. There's no one to share with yet." Access log: empty copy above. |
| Not logged | n/a |
| Not shared | n/a (self screens) |
| Consent withdrawn | Banner on Sharing: "While tracking is off, people you share with can't see your records. Your sharing choices are kept for when you turn it back on." |
| Error | `ErrorState`. Grant errors inline in the review sheet. |
| Offline | Controls disabled with the offline hint. Cached lists shown. |

### 4.6 Profile `/you/profile` (self), `/family/:id/profile` (child, guardian) (W1)

| Field | Adult | Child (guardian) | Notes |
|---|---|---|---|
| Name (display label) | yes | yes | |
| Age band | required to track | child bands only | "We never ask for a date of birth." Server refuses a mismatch (`AGE_BAND_NOT_VALID_FOR_MEMBER`): "This age band doesn't match this person's account type." |
| Activity level (W1 new) | optional: Mostly sitting · Lightly active · Moderately active · Very active | not offered ❓ (the capability exists server-side; recommend adult-only) | Helper: "Used only to describe your day. We never set a calorie target from it." |
| Sex | optional | optional | existing |
| Dietary preference, food goals, allergens | yes | yes | existing (food decisions) |
| Health conditions | optional | optional | Helper: "Never shared, even with people who help you." (FOOD_PROFILE excludes conditions, D-044) |
| Height and weight | **not profile fields.** Row "Body measurements" → Log sheet Body (adult) | absent | They are observations, not profile (arch §9.2 P1). |

**Save:** one Save button, a `role="status"` "Saved." and it invalidates `/me` (tier may change: UNKNOWN → ADULT/OLDER_ADULT).

| State | Profile |
|---|---|
| Loading | Form skeleton. |
| Empty | 404 = "no profile yet". An empty form with the hint `profile.noProfileYet`. |
| Consent needed (child) | The existing guardian consent gate. **It must check the food purpose only** (see G-08), not every `requiresVerifiable` purpose. |
| Error | `ErrorState` / inline field errors. |
| Offline | Save disabled with the hint. |
| Not logged, not shared, withdrawn | n/a |

### 4.7 Goals `/goals`, `/family/:id/goals` (W1 exists → W2 rebuild)

**Offer set (W2 UI).** It is `capabilities.goalCategories × goalDirections`, narrowed to this table. The server remains the control.

| Metric | Directions offered | Period | Adult | Child | Target entry |
|---|---|---|---|---|---|
| WATER_ML | At least | each day | yes | yes | ml or L with presets |
| STEPS | At least | each day / each week | yes | yes | integer |
| ACTIVE_MINUTES | At least | each day / each week | yes | yes | h + min |
| SLEEP_MINUTES | At least · Around | each day | yes | yes | **hours + minutes** (never raw minutes) |
| PROTEIN_G, FIBRE_G | At least | each day | yes | no | g |
| SODIUM_MG | At most | each day | yes | no | mg |
| ENERGY_KCAL | Around · At least (**never At most**, D-047) | each day | yes | no | kcal |
| WEIGHT_KG | Around · At most · At least | latest reading | yes | **never** | kg, 1 dp |
| CLINICAL, WELLBEING metrics | not offered in W2 UI (❓ Q-04) | — | — | never | — |

**Goal add sheet:**
1. What (metric list, grouped by category).
2. Aim (direction).
3. Number with unit.
4. Period.

The hint reads: "Choose a number that makes sense for you. We show what you've recorded next to it. We don't judge the number."

**BODY goals** carry the D-047 note on the add sheet and on goal detail:

> "If you're working on your weight, a doctor or registered dietitian can help you choose a number that suits you. We don't set deadlines or suggest a rate." (`goal.bodyGuidance`)

This copy MUST pass `i18n.vocabulary.test.ts`: no "lose weight" or "weight loss". There is no deadline, rate, deficit or projected date anywhere.

**Subject picker.** It lists you, children you guard, and adults for whom you are CAREGIVER with MANAGE on at least one goal-eligible scope (from the overview). It **never lists every CHILD member** (today's behaviour, G-12).

**Progress display:** §6.5.

| State | Goals |
|---|---|
| Loading | List skeleton. |
| Empty | "No goals yet. A goal is a number you choose, like how much water to drink in a day." **Add a goal** |
| Not logged yet | "Nothing logged today yet" / "Nothing logged in the last 7 days" (existing keys). |
| Not shared | Member goals: only goals whose scope is visible are listed. If none: "{name} hasn't shared any goals with you." |
| Consent needed / withdrawn | The list is read-only. **Add a goal** opens the ConsentSheet. |
| Error | `GOAL_NOT_PERMITTED_FOR_CHILD` / `GOAL_REQUIRES_REVIEWED_STANDARD` show inline with the existing localised messages (they should be unreachable through the offer set). |
| Offline | Add and End disabled. |

### 4.8 Habits `/habits`, `/family/:id/habits` (W2 split; W1 exists inside Goals)

| Element | Spec |
|---|---|
| Row | Name · cadence ("Every day", "Weekdays", "Once a week") · **Done today** toggle (44 px, `aria-pressed`) · 7-day strip (7 cells of 44×44 px or more, 2 px gap, fits 328 px; each cell `aria-label` "{date}: done" / "{date}: not ticked") · "Done on {n} of {m} expected days in the last 7 days" |
| Detail | Month grid (7 columns, 44 px or more), ‹ › months, back-fill any past day (existing behaviour), Edit, Archive (ConfirmSheet) |
| Archived | Collapsible "Archived ({n})" with **Bring back** |
| Child copy | Heading "{name}'s routines". Same counts. No evaluative words (§5.4). |
| Never | Streaks, "days in a row", flames, a red "missed" state. An unticked day is `--bg` with a border. A ticked day is `--brand` fill plus a ✓ glyph plus bold (not colour alone). |

| State | Habits |
|---|---|
| Loading | Row skeletons. |
| Empty | "No habits yet. A habit is something you want to do regularly, like a walk after dinner." **Add a habit** |
| Not logged | "No days expected yet." (existing) |
| Not shared | "{name} hasn't shared their habits with you." |
| Consent needed / withdrawn | Toggles disabled. Tapping one opens the ConsentSheet. |
| Error | Toggle rollback plus inline error: "Couldn't save. Try again." |
| Offline | Toggles disabled with the hint. |

### 4.9 Meals: log, detail, confirm, awaiting (W1 exists → W2 rebuild) — wireframe 5

```
/meals/new
┌──────────────────────────────────────────┐
│ ✕  Log a meal                            │ full-screen flow
│                                          │
│ What was eaten                           │ fieldset 1
│  Dal tadka · home-cooked · 400 g   Edit  │
│  Jeera rice · home-cooked · 300 g  Edit  │
│  [ + Add food ]                          │ → Search packaged · Scan ·
│                                          │   Home-cooked · (W2: My foods)
│ Who ate it                               │ fieldset 2
│  [✓] You      [ Half ▾ ]                 │
│      Goes into your log.                 │ PortionDestination line
│  [✓] Aarav    [ Quarter ▾ ]              │
│      Goes into Aarav's log.              │
│  [✓] Priya    [ Half ▾ ]                 │
│      Saved with the meal. Goes into      │
│      Priya's log when Priya adds it.     │
│  [ ] Amma                                │
│                                          │
│ When  Today, 8:10 pm · Dinner   Change   │ fieldset 3, defaulted, collapsed
│                                          │
│ [            Save meal               ]   │
└──────────────────────────────────────────┘

/meals/:id  (viewed by Priya)
┌──────────────────────────────────────────┐
│ ‹ Back                                   │
│ Dinner                                   │ h1
│ Fri, 11 Sep · 8:10 pm · logged by Mohan  │
│ ┌ Your share is waiting ───────────────┐ │
│ │ Mohan logged this meal with you      │ │
│ │ having about half. It isn't in your  │ │
│ │ log yet.                             │ │
│ │ [        Add to my log          ]    │ │
│ │ Not right? Ask Mohan to change it.   │ │
│ └──────────────────────────────────────┘ │
│ Who ate it                               │
│  You · half              … Waiting for you│
│    Energy 610 kcal · Protein 18 g · …    │
│  Mohan · half            ✓ In Mohan's log│
│    Not shared with you                   │
│  Aarav · a quarter       ✓ In Aarav's log│
│    Only Aarav's guardians can see this   │
│ What was eaten                           │
│  Dal tadka 400 g · Jeera rice 300 g      │
│  (whole-meal nutrition hidden: UXD-20)   │
└──────────────────────────────────────────┘
```

**Order.** The form runs What → Who → When. When defaults to now plus a meal type guessed from the *household-zone* hour, collapsed behind "Change".

**Portion destination line.** It is stated per selected member **before** saving (D-041). It is derived from overview `relationship`, `canLogNutrition` and `trackingConsentActive`, never from `memberType`.

| Case | Line (`meal.dest.*`) |
|---|---|
| You, consent active | "Goes into your log." |
| You, consent off | "Saved with the meal only. Your tracking is off." plus **Turn on** |
| Child you look after, consent active (`canLogNutrition`) | "Goes into {name}'s log." |
| Child you look after, consent off | "Saved with the meal only. Tracking isn't turned on for {name}." |
| Child you don't look after | "Saved with the meal. It won't go into {name}'s log. Only {name}'s guardians can log {name}'s meals." (❓ Q-06) |
| Adult you help with food (`canLogNutrition`) | "Goes into {name}'s log. You help {name} with food." |
| Any other adult | "Saved with the meal. Goes into {name}'s log when {name} adds it." |

**Portion choices:** A quarter · Half · Three quarters · The whole meal · One and a half (existing). "Need not sum to 1" is explained once: "Shares don't need to add up. Some food may be left over."

**Detail rules:**
- Your waiting share leads the page with **Add to my log** (`POST /meals/{id}/confirm`).
- There is **no "decline"** action, because none exists. Use the "Not right? Ask {logger} to change it." line instead.
- Share nutrition uses `nutritionWithheld`:
  - Withheld for an adult: "Not shared with you".
  - Withheld for a child: "Only {name}'s guardians can see this".
  - Nutrition `null` but not withheld: "Nutrition not known".
- Hide the whole-meal total when any share is withheld from the viewer (UXD-20).
- Only the logger sees **Edit** (W2) and **Delete meal** (ConfirmSheet, danger tone). Others see "Only {logger} can change or delete this meal."

**Awaiting list** (`/meals?filter=awaiting`, W2; W1 uses the Family "Waiting for you" card). Rows read "{meal} · {day} · your share: {fraction}" with **Add to my log** inline.

| State | Meal log / detail |
|---|---|
| Loading | Form: members skeleton in "Who ate it". Detail: page skeleton. |
| Empty | Catalogue search with no match: "Nothing in the catalogue matches. Add it as home-cooked instead." |
| Not logged | n/a |
| Not shared | Per share (above). |
| Consent needed (confirm) | **Add to my log** gets `CONSENT_REQUIRED`, which opens the ConsentSheet, then retries the confirm. |
| Consent withdrawn | Same, with the withdrawn copy. |
| Error | Save failure keeps the whole form. 404 detail uses the not-found state. |
| Offline | Save and confirm disabled with the hint. |

### 4.10 Tracker history `/trackers/:category`, `/family/:id/trackers/:category` (W2)

| Region | Spec |
|---|---|
| Header | Category name (§5.3) · member name if not self · metric switcher for multi-metric categories (Activity: Steps / Active minutes / Workout / Distance; Meals: Energy / Protein / Carbohydrate / Fat / Fibre / Sugar / Sodium; Body: Weight / Height / Waist / Body fat / BMI; Readings: BP / Heart rate / Blood sugar / Oxygen / Temperature; Sleep: Duration / Bedtime / Wake time; Mood: Mood / Stress). |
| Range | Radio chips 7 days · 30 days · 90 days. "Long term" is W3 and not shown. |
| Summary sentence | Above the chart; it also serves as the chart description. Rules in §6.4. |
| Chart | `HistoryChart`† inline SVG. SUM metrics: daily bars, **gaps for not-logged days** (no bar, with a small baseline tick). A dashed goal line appears only for a live SUM goal. LATEST metrics: dots per reading, with the line broken across days that have no reading. **CLINICAL: dots only**, with no goal line, no band and no average. BP: paired dots per reading. Y-axis starts at 0 for bars; LATEST axes fit the data with 10% padding. The axis is labelled with its unit. |
| Table toggle | **Show as table**: Day · Value (or "Not logged") · Time. It serves every user, not only screen readers. |
| Entries | Grouped by day: time · value · source ("From a meal" for DERIVED nutrition; nothing for MANUAL). **No edit or delete** (no endpoint, ❓ Q-07). |
| Log | A sticky **Log {metric}** button (only with MANAGE) opens the Log sheet at that kind. |
| Notes | Readings carry the static `readings.note`. Body weight goal detail carries `goal.bodyGuidance`. |

| State | Tracker history |
|---|---|
| Loading | Chart-box skeleton (fixed aspect) plus list skeleton. |
| Empty / not logged | "No {metric} logged in the last {n} days." plus **Log {metric}**. The chart is not drawn (never an empty axis full of zeros). |
| Not shared | Page state: "{name} hasn't shared {category} with you. Only {name} can change that." |
| Consent needed / withdrawn | History is readable (self). **Log** opens the ConsentSheet. For others: not-shared state. |
| Error | `ErrorState`. |
| Offline | Cached plus banner. **Log** disabled. |

### 4.11 You `/you` (W1)

| Group | Rows |
|---|---|
| Identity card | Name · household name ("Just you" for INDIVIDUAL) · **Edit profile** |
| Your records | Profile · Sharing & caregivers · Shared with you · Who viewed your records |
| Privacy | Privacy choices (consent, existing panel) · Download my data · Delete my account (danger row) |
| Household | Members and invitations (`/you/household`: roster with roles, invite adult, add child, leave, remove, erase child; all existing functionality moved from `/family`) |
| App | Appearance (dark mode, **larger text**) · Privacy notice · Terms · Sign out (ConfirmSheet) |
| Conditional | Notifications (W2, only once built) · Ops console (operator only) |

Signed out, it shows: sign in / create account, appearance, legal.

### 4.12 Food tab: what changes in W1

- Signed-in `/food` hosts the current Home content (search bar, categories, rails, scan/label actions, recently viewed).
- "Your household's food profile" card moves here from the Family overview: readiness per person for personal results plus household allergens. It keeps the existing `overview.*` copy. Use a neutral `--brand-subtle` treatment instead of the LIMIT verdict colours (G-17).
- "Evaluate for a family member" beyond self and children waits for FOOD_PROFILE to be offered (W2). There is no UI before that.

---

## 5. Copy guidelines and glossary (en-IN)

### 5.1 Voice

- **Plain, short, second person.** One idea per sentence. en-IN spelling: colour, fibre, centre, programme, litre.
- **State the fact, then the next step.** "Water: 500 ml logged today." never "Only 500 ml".
- **No imperatives about health.** Avoid "should" and "must". Suggest: "You could…" (W3 suggestions come from a closed catalogue).
- **Never attribute motive.** "Hasn't shared anything with you", never "keeps their records private" (G-16).
- **Names, not pronouns**, for members. "Priya's log", "when Priya adds it".
- **Numbers:** `Intl.NumberFormat('en-IN')`, so 12,345 and 1,23,456. Times "8:10 pm"; dates "Sat, 12 Sep" (add the year only outside the current year).
- **Counts without a plural library:**
  - Use `.one` / `.other` key pairs chosen in code (e.g. `today.mealsWaiting.one` / `.other`), or phrase counts as "Meals waiting: 2".
  - Hindi (future) needs its own forms, so never concatenate sentence fragments.
- **All strings come from the locale bundle** (D-019), including screen-reader-only text such as the current AppShell "(current)".

### 5.2 Glossary

| Concept | Say | Don't say | Key namespace |
|---|---|---|---|
| VIEW level (subject's view) | "Can see" | "Read access", "viewer" | `sharing.level.VIEW` |
| MANAGE level (subject's view) | "Can see and log for you" (short: "See and log") | "Manage", "control", "admin", "full access" | `sharing.level.MANAGE` |
| No grant | "Not shared" | "Blocked", "private", "locked" | `sharing.level.NONE` |
| Grant (verb) | "Share" / "Let {name} see" | "Give permission", "authorise" | `sharing.*` |
| Revoke | "Stop sharing" | "Remove access", "block" | `sharing.stop` |
| Relationship SELF | "You" | — | `access.relationship.SELF` |
| GUARDIAN (caller → child) | "You look after" / "{name}'s guardian" | "Owner", "parent account", "controls" | `access.relationship.GUARDIAN` |
| CAREGIVER (caller → subject) | "You help" / subject's view: "{name} helps you" | "Carer", "manager", "supervisor", "monitor" | `access.relationship.CAREGIVER` |
| SHARED | "Shares with you" / subject's view: "You share with {name}" | "Follower", "connected" | `access.relationship.SHARED` |
| NONE | "Hasn't shared anything with you" | "Private", "hidden", "restricted" | `access.relationship.NONE` |
| Caregiver (noun, headings only) | "Caregiver" / "Someone who helps you" | "Guardian" (reserved for children) | — |
| Access log | "Who viewed your records" | "Audit trail", "surveillance", "activity log" | `accessLog.*` |
| Tracking consent | "Tracking" · "Turn on tracking" / "Turn off tracking" | "Data processing", "opt in to monitoring" | `consentTracking.*` |
| Consent withdrawn | "Tracking is off" | "Revoked", "disabled by you" | |
| Goal | "Goal", a number you set · "Aim for at least / around / at most" | "Target you must hit", "limit", "budget", "allowance" | `goal.*` |
| Goal met (adult) | "Reached" · "Above the number you set" | "Success!", "Failed", "Over budget", "Great job" | existing |
| Goal met (child) | no marker; the numbers only | any evaluation | |
| Habit | "Habit" (adult) / "Routine" (child view) | "Challenge", "streak" | `habit.*` |
| Habit day | "Done" / "Not ticked" | "Missed", "Broken", "Failed" | |
| Consistency | "Done on {n} of {m} days" | "{n}-day streak", "Keep it up!" | |
| Insight (W3) | "What we noticed" · "Based on {n} days you logged" | "Warning", "Alert", "Risk" | `insight.*` |
| Not logged | "Not logged" | "0", "—", "No data!" | `status.notLogged` |
| Not shared | "Not shared with you" | "Locked", "Access denied", "Private" | `status.notShared` |
| Awaiting | "Waiting for {name}" · "Add to my log" | "Pending approval", "Unconfirmed", "Rejected" | `meal.*` |
| CLINICAL category | "Health readings" | "Clinical readings", "vitals", "symptoms" | `category.CLINICAL` (change) |
| BODY category | "Body measurements" | "Body stats", "shape" | existing |
| Weight goal | "Weight: aim for around 68 kg" | "Lose weight", "weight loss", "target weight", "ideal weight" | |
| Energy | "Energy" · "kcal" | "Calories burned", "calories left", "budget" | |

### 5.3 Category and metric labels

| Code | Label | Code | Label |
|---|---|---|---|
| NUTRITION (tile) | Meals | NUTRITION (sharing/history) | Food and nutrition |
| HYDRATION | Water | ACTIVITY | Activity |
| SLEEP | Sleep | WELLBEING | Mood and stress |
| HABITS | Habits (child: Routines) | BODY | Body measurements |
| CLINICAL | Health readings | SYSTOLIC/DIASTOLIC | Blood pressure (top number / bottom number) |
| BLOOD_GLUCOSE_MGDL | Blood sugar (glucose) | SPO2_PCT | Oxygen (SpO₂) |
| RESTING_HEART_RATE_BPM | Resting heart rate | BODY_TEMPERATURE_C | Temperature |

### 5.4 Child-directed copy (guardian audience)

- **Encouraging means noticing, not scoring.** Say "Every entry helps you notice {name}'s patterns." Don't say "Great job, Aarav!"
- **No evaluative adjectives about the child or their food:** good, bad, only, too much, picky, overeating, junk, healthy, unhealthy.
- **Describe the routine, not the child:** "Fruit at breakfast: done on 4 of 7 days."
- **No framing around size, growth or body**, ever.

### 5.5 Banned vocabulary

These words must never appear in the locale bundle. `frontend/src/lib/i18n.vocabulary.test.ts` enforces the list, and G-20 extends it.

| Source | Terms |
|---|---|
| D-004 (existing test) | toxic · poison(ous) · dangerous · harmful · will harm · never eat · cannot/must not eat · safe for everyone · allergen-free · clean food/label/eating · unhealthy · bad food · junk food · detox · superfood · cure(s) · diagnose (affirmative) · deficient · lose weight / weight loss (affirmative) · calorie target/limit/goal · burn calories/fat |
| Wellness additions, regex-safe (add to `BANNED`; use `claimOnly` where a negated form is legitimate, e.g. "no scores or streaks") | missed · you broke · streak lost · cheat meal/day · guilt-free · off track · overweight · obese · underweight · normal range · abnormal · ideal weight · target weight · calories left · picky · overeating |
| Wellness additions, context-bound (copy-review checklist, **not** regex: the words are fine elsewhere, e.g. the mood label "Low") | failed · streak (any user copy before W6) · good day/bad day · on track · elevated/high/low **about a reading** · risk · warning · alert **about health data** · earn/burn |

---

## 6. Data display rules

### 6.1 Units and rounding (the only formatter; `lib/format` owns it)

| Code | Display unit | Rounding | Aggregation (server) | Child |
|---|---|---|---|---|
| WEIGHT_KG | kg | 1 dp | LATEST | ✗ |
| HEIGHT_CM | cm | 0 dp | LATEST | ✗ |
| BMI | none in tiles ("BMI 24.1"); kg/m² in detail | 1 dp | LATEST | ✗ |
| WAIST_CM | cm | 1 dp, trailing .0 trimmed | LATEST | ✗ |
| BODY_FAT_PCT | % | 1 dp | LATEST | ✗ |
| STEPS | steps | integer, en-IN grouping | SUM | ✓ |
| ACTIVE_MINUTES, WORKOUT_MINUTES | min, or "1 h 15 min" at 60 or more | integer | SUM | ✓ |
| DISTANCE_KM | km | 2 dp below 1 km, else 1 dp | SUM | ✓ |
| SLEEP_MINUTES | "7 h 20 min" | integer minutes | SUM (naps add; wake-day attribution) | ✓ |
| BEDTIME_OFFSET_MIN, WAKE_OFFSET_MIN | clock, "11:30 pm" (offset −30 = 23:30 on the previous day) | minute | LATEST | ✓ |
| WATER_ML | ml below 1,000; "1.25 L" at 1,000 or more (up to 2 dp, trimmed) | integer ml | SUM | ✓ |
| ENERGY_KCAL | kcal | integer | SUM | ✓ (not headlined, UXD-08) |
| PROTEIN_G, CARBOHYDRATE_G, FAT_G, FIBRE_G, SUGAR_G | g | 1 dp below 10, else integer | SUM | ✓ |
| SODIUM_MG | mg | integer | SUM | ✓ |
| SYSTOLIC + DIASTOLIC | "128/84 mmHg" (paired by the same `effectiveAt`; if unpaired, show the one value labelled "top" or "bottom") | integer | LATEST | ✗ |
| RESTING_HEART_RATE_BPM | bpm | integer | LATEST | ✗ |
| BLOOD_GLUCOSE_MGDL | mg/dL | integer | LATEST | ✗ |
| SPO2_PCT | % | integer | LATEST | ✗ |
| BODY_TEMPERATURE_C | °C (server "C" → "°C") | 1 dp | LATEST | ✗ |
| MOOD_SCORE, STRESS_SCORE | "4 of 5 · Good" | integer | LATEST | ✓ mood (stress ❓ Q-03) |

The aggregation column is read from the API (`aggregation` on `/today` metrics and goal progress). It is listed here for reference only, and the client MUST NOT hard-code it.

### 6.2 SUM vs LATEST

- **SUM** (accumulates in a day) shows the day total: "1.5 L".
- **LATEST** (point-in-time) shows the latest reading with its time: "71.2 kg at 7:05 am".
- **Never add LATEST values.** Two weigh-ins are two readings, not a total.
- Across a window:
  - SUM: average **per logged day** with the logged-day count ("Average 1.8 L on the 5 days you logged"). **Never divide by days that weren't logged.** That would turn "not logged" into 0.
  - LATEST: count plus latest ("4 readings · latest 71.2 kg on 10 Sep"). BODY adds the plain change between the first and latest reading in the window: "Change: −0.8 kg since 1 Sep". It uses neutral colour and no arrow colour (UXD-10). CLINICAL adds **nothing** (UXD-09).

### 6.3 Not logged vs zero

| Situation | Show |
|---|---|
| No observation in the window | "Not logged". Bars: gap. Lines: broken. Tables: "Not logged". |
| A recorded 0 (e.g. 0 steps entered) | "0 steps", the only way a 0 appears |
| Meal with unknown nutrition | "Nutrition not known", never 0 kcal |
| Goal with no data | "Nothing logged today yet" (`dataAvailable = false`, `actual = null`) |
| Coverage | Always paired with averages and trends: "logged on {n} of {m} days" |

### 6.4 Summary sentences (tracker history; also the chart description)

| Metric type | Template |
|---|---|
| SUM | "{Metric}: logged on {n} of {days} days. Average on logged days: {avg}." |
| LATEST (BODY) | "{Metric}: {count} readings. Latest {value} on {date}. Change since {firstDate}: {delta}." |
| LATEST (CLINICAL) | "{Metric}: {count} readings. Latest {value} on {date}." |
| LATEST (WELLBEING) | "{Metric}: {count} entries. Latest {value} on {date}." |
| None | "No {metric} logged in the last {days} days." |

### 6.5 Goals

| Goal shape | Line | Bar |
|---|---|---|
| AT_LEAST · SUM | "{actual} of {target} {unit}" · adult adds "Reached" when actual ≥ target | Fill = min(actual/target, 1). Brand colour. `aria-hidden`. |
| AT_MOST · SUM (adult only) | "{actual} {unit} so far · you set at most {target}" · "Above the number you set" when over | Neutral fill that **does not change colour** when exceeded |
| MAINTAIN · SUM | "{actual} {unit} · you aim for around {target}" | none |
| LATEST (BODY) | "Latest {actual} kg · you aim for around/at most/at least {target} kg" | **none**. No "x kg to go", no date, no projection. |
| Child, any | "{actual} {unit} logged · aim: at least/around {target} {unit}" | allowed, with no "Reached" and no colour change |
| Window | DAILY: "Today". WEEKLY: "Last 7 days" (from `windowFrom`/`windowTo`, household zone). | |

### 6.6 Health readings (D-037)

- Show the value exactly as recorded, with its unit and time.
- **No** category, colour, icon, reference range, band, average, delta, trend arrow or "compared with last time".
- The same `readings.note` appears regardless of value. It never appears in response to a value.
- Glyph ▲ is reserved for the allergen flag (design-system §2.3) and never used near readings.

### 6.7 Family displays (D-038, D-051, D-054)

- Every number on a Family **card** is about one person, and it is shown only when that scope is visible (`visibleScopes`). Each card's week strips are on that person's own scale and are never aligned against another person's.
- **The family summary (D-054)** is the one exception to "about one person".
  - It is computed on the server from the caller's own cards, so it can hold nothing the caller could not already read on one of them.
  - It covers water and steps (totals), sleep (an average per person who logged it), meals (a count of people, never kcal) and habits (done of due).
  - Every figure states its coverage ("for 3 of 4 people"), and the summary names whose records it uses.
  - It is hidden when fewer than two people are included.
  - It never includes readings, body measurements or mood.
- **Still no** "most/least", ordering by any value, per-person figures inside the summary, side-by-side charts of members, or comparison copy (D-051).
- The updates feed is ordered by time logged, never by amount.
- Card order is the server order (you → children you guard → others by `joinedAt`). Members who share nothing with the viewer appear as one quiet list in that order, not as identity cards.

### 6.8 Time

- Day boundaries and "today" come from the household zone (D-053) via the server. The client formats with `Intl.DateTimeFormat('en-IN', { timeZone: household.timeZone })`.
- Entry time pickers work in the household zone. Convert to ISO-8601 with offset before sending `effectiveAt`.

---

## 7. Accessibility and performance

### 7.1 WCAG 2.2 AA, applied to these screens

| Criterion | Requirement here |
|---|---|
| 1.3.1 Info and relationships | Tiles are `<li>` in a labelled `<ul>`. Metric label and value sit in one link, whose accessible name reads "Water, 1.5 litres of 2 litres you aim for. Open history." The [+] is a sibling `<button>` named "Log water". Scope editors use `<fieldset><legend>` with radio inputs. |
| 1.4.1 Use of colour | Done/not done, active tab, pending and selected all carry a glyph or text as well as colour. |
| 1.4.3 / 1.4.11 Contrast | Tokens only (measured in design-system §2). The goal bar track uses `--border-interactive` (3.4:1). |
| 1.4.4 / 1.4.10 / 1.4.12 | 200% zoom, 320 px reflow, text spacing: tiles collapse to 1 column and nothing clips. |
| 2.1.1 Keyboard | Everything is operable. Radiogroups use arrow keys. The sheet opens with focus on its h2 and closes back to the invoking control. |
| 2.2.1 Timing | Status messages don't vanish before 6 s. In larger-text mode or OLDER_ADULT they persist until the next action. |
| 2.4.3 / 2.4.7 Focus | Order follows reading order. The focus ring token is never removed. |
| 2.4.11 Focus not obscured | `scroll-padding-bottom: calc(var(--bottom-nav-h) + env(safe-area-inset-bottom) + 16px)` on `main`, and the sticky Save and Review bars add their height. |
| 2.5.3 Label in name | The accessible name starts with the visible label ("Save 250 ml"). |
| 2.5.7 Dragging | The sheet handle is decorative. **Close** is a button. No slider without steppers. |
| 2.5.8 Target size | 44×44 px minimum everywhere (design-system rule; 24 px is the WCAG floor), 8 px between adjacent targets. The habit strip is rebuilt (UXD-17). |
| 3.2.6 Consistent help | Privacy notice and help rows always live in You. |
| 3.3.1 / 3.3.3 Errors | Errors sit inline next to the field, via `aria-describedby`, with a suggestion ("Enter an amount greater than zero"). |
| 3.3.7 Redundant entry | The meal log MAY remember the last "Who ate it" selection per meal type (device-local). |
| 3.3.8 Accessible authentication | Password fields allow paste. No cognitive puzzles. |
| 4.1.3 Status messages | Saves, ticks and offline changes are announced with `role="status"`. Errors in forms use `role="alert"` only on submit. |

**Screen-reader patterns:**
- **Units are spoken in full.** Use `unit.sr.*` keys: kcal "kilocalories", ml "millilitres", L "litres", mmHg "millimetres of mercury", bpm "beats per minute", mg/dL "milligrams per decilitre", °C "degrees Celsius", kg/m² "kilograms per square metre", SpO₂ "oxygen saturation".
- **"Not logged" is spoken with context:** "Activity: not logged today".
- **Charts:**
  - `<figure>` wraps an `<svg role="img" aria-labelledby="t d">` with `<title>` ("Water, last 7 days") and `<desc>` (the §6.4 summary sentence).
  - Individual bars are not focusable.
  - A **Show as table** button reveals a real `<table>` (caption, Day / Value / Time, "Not logged" cells). It is useful to everyone and required for AT.
- **Pending and awaiting chips:** text first ("Waiting for Priya"). The ellipsis glyph is `aria-hidden`.

**Larger-text mode (UXD-16):**

```css
html[data-text-size='large'] { font-size: 125%; }          /* rem tokens scale: body 20px */
html[data-text-size='large'] { --tap-min: 48px; --tap-primary: 56px; }
```

- In larger-text mode:
  - tile grids become one column;
  - the Log sheet renders a list, not a 3-up grid;
  - bottom-nav labels use `--text-xs` (scaled);
  - `--content-max` is unchanged.
- It is set via You → Appearance or the OLDER_ADULT offer, stored in localStorage, and applied before first paint (same mechanism as `lib/theme.ts`).
- It stacks with browser and OS font scaling, and every layout MUST survive both.

### 7.2 Performance on mid-range Android

Reference device: 4 GB RAM, Helio G85 or Snapdragon 680 class, Chrome stable, 360×800 CSS px. Test at "Fast 4G" with 4× CPU slowdown.

| Budget (p75) | Target |
|---|---|
| LCP, Today | ≤ 2.5 s cold · ≤ 1.2 s warm |
| INP | ≤ 200 ms (habit tick and water save feel immediate) |
| CLS | ≤ 0.05 (skeletons match final boxes) |
| Initial JS (shell + Today) | ≤ 150 KB gzip. Food, Family and You are route-split. |
| Log sheet chunk | ≤ 25 KB gzip, prefetched on idle after Today renders |
| Requests to render Today (warm) | ≤ 3: `/me` (cached), `/members/{me}/today`, `/members/{me}/meals` (day) |
| Requests to render Family | ≤ 2: `/me`, `/households/{id}/overview` (**no per-member fan-out**) |
| Charts | inline SVG, ≤ 90 marks, no charting library |
| Fonts and images | system font stack. Images only in Food, lazy and aspect-boxed. |

**Behaviour rules:**
- No polling, and no refetch on window focus (existing).
- Habit ticks are optimistic, with rollback and a status message on failure.
- Measurements wait for the 201. The button shows a spinner and keeps its label, and there's no optimistic number, because a false number is worse than a 300 ms wait.
- Invalidate precisely (`/today` for the subject, goals for the subject) rather than the whole `['wellness']` cache.
- Memoise formatters (`Intl.NumberFormat` instances).

---

## 8. Gap list against the current frontend (prioritised)

**Priority levels:**
- **P0:** conflicts with a safety, privacy or trust rule, or states something false.
- **P1:** W1 scope is missing or the spec is contradicted.
- **P2:** polish or consistency.

**Owners:**
- **3b:** the peer session that owns existing `features/{meals,today,goals,habits,household}`, `lib/{nutritionLog,goals,wellness,insights,useMe}.ts` and `app/{App,AppShell}.tsx`.
- **W1-FE:** the W1 frontend agent (new files).
- **BE:** backend.

| # | Pri | Area | Current behaviour (file) | Conflicts with | Required change | Owner |
|---|---|---|---|---|---|---|
| G-01 | P0 | Family probing pollutes access logs | `features/household/FamilyWellness.tsx` `MemberCard` → `OwnRecords` runs for **every** CHILD member (lines ~152–156, 172–181), calling `goalProgress` (`requireAnyView`) and `listHabits` (`requireView`). Both go through `HealthAccessPolicyImpl` → `deniedForSubject`. A non-guardian adult therefore writes **REFUSED** events into that child's access log on every Family visit. Separately, `useHouseholdOverview.ts` (78–92) and `ConsentPanel.tsx` (177) probe every member's profile and consent. Those 403s write audit rows (`ProfileService` → `audit.denied`) but not access-log entries. | UXD-05, D-044 access log as trust feature | Replace the fan-out with `/households/{id}/overview`. Request member data only for `visibleScopes`. Fetch consent grants only for self and guarded children (from the overview relationship). | 3b (+W1-FE for overview client) |
| G-02 | P0 | Client-side tier and write-destination inference | `isChild` from `memberType`/`ageBand` in `features/today/TodayScreen.tsx` (131–133), `features/goals/GoalsScreen.tsx` (83–85), `features/goals/goalOptions.ts`, `features/today/QuickLog.tsx` (48–52). `features/meals/MealLogScreen.tsx` `PortionRow` (470–473) says a child's share goes into their log **whether or not the logger is that child's guardian**, and says every other adult's share waits even for a NUTRITION caregiver. It also ignores consent. | D-046 (server-authoritative), D-041/D-044/D-048; a false statement to the user | Drive capabilities from `GET /me` and `/members/{id}/experience`. Drive portion destination copy from overview `canLogNutrition`, `relationship` and `trackingConsentActive` (§4.9 table). | 3b |
| G-03 | P0 | Device-local day boundaries | `TodayScreen.tsx` `startOfToday` (57–61) · `FamilyWellness.tsx` `startOfTodayIso`/`daysAgoIso` · `features/goals/HabitList.tsx` `isoDaysAgo` (282–286) · `lib/goals.ts` `todayIso` (161) · `MealLogScreen.tsx` `nowLocalInput`/`guessMealType` (79–92) · all `toLocale*String('en-IN')` calls lack `timeZone` | D-053 | Use `/members/{id}/today?date=` for day values. Format and pick times in `household.timeZone` (from `/me`). | 3b |
| G-04 | P0 | Day totals summed on the client | `TodayScreen.tsx` `TODAY_METRICS` (39–46) plus `sumByMetric` (280–291): a hard-coded list, raw observation sum, and no LATEST handling (weight or BP cannot appear on Today). `FamilyWellness.tsx` does the same (196–201). | UXD-07, W1 exit gate "every dashboard number traceable", §6.2 | Render `/today` `metrics[]` (server SUM/LATEST). Tile set per §4.2. | 3b |
| G-05 | P0 | No onboarding or "Just me" | With no household, `TodayScreen.tsx` (116–126), `GoalsScreen.tsx` (68–76), `MealLogScreen.tsx` (184–192) and `ConsentPanel.tsx` `NoHousehold` all send people to `/family` "Create your household" with a **household name** field (`FamilyScreen.tsx` `NoHouseholdView` 120–174). | D-043 (Just me / Me and my family; `POST /me/individual`) | Build `/welcome` (§4.1) and redirect every signed-in health route there while `mode = NONE`. | W1-FE (new) + 3b (redirect wiring in App) |
| G-06 | P0 | Sharing, caregivers and access log missing; dead client | No UI for `/access-grants`, `/me/shared-with-me` or `/access-log`. `lib/wellness.ts` still exports `listShares`/`share`/`unshare` against the **removed** `/wellness/members/{id}/shares` (107–127) and the obsolete `VisibilityGrant` type. | D-044, D-045, arch §9.2 P1 | Build §4.5 screens with a new `lib/access.ts`. Delete the dead share functions. | W1-FE (new) + 3b (delete) |
| G-07 | P1 | Navigation and shell | `app/AppShell.tsx` `NAV_SIGNED_IN` (43–49) = Today/Search/Scan/Family/Settings. The header search appears on every screen (121–126). A header cog duplicates Settings (113–119). The sr-only **hard-coded English "(current)"** (181) duplicates `aria-current` and breaks D-019. `app/App.tsx` has no `/welcome`, `/you/*`, `/family/:memberId`, `/trackers`, `/log`. | §2.1–2.3, D-019, UXD-01..03 | Today · Family · Log (button) · Food · You. Search only in Food. Add the routes and redirects in §2.3. | 3b |
| G-08 | P1 | Child consent purpose conflation | `features/household/ProfilePanel.tsx` `childPurposeCodes` (39–41) and `useHouseholdOverview.ts` (50–52) treat **every** non-essential `requiresVerifiable` purpose as required. After V14 that includes `CHILD_WELLNESS_TRACKING`, so a guardian can't save a child's *food* profile without granting *wellness tracking*, and readiness reports the child as blocked. | D-048 (separate purposes), D-001 | Map purpose per feature: food profile → `CHILD_FOOD_SUITABILITY`; tracking → `CHILD_WELLNESS_TRACKING`. | 3b |
| G-09 | P1 | Withheld meal nutrition not modelled | `lib/nutritionLog.ts` `MealPortion` (68–75) lacks `nutritionWithheld`, and `nutrition` is typed non-null. `MealDetailScreen.tsx` `ShareNutrition` (229–234) makes one `/visibility` call per share instead. `TodayScreen.tsx` `mine?.nutrition.energyKcal` (242) dereferences a nullable value. The whole-meal total always shows (184), which reveals withheld shares (UXD-20, Q-05). | D-044 meals, perf §7.2 | Add the field, render per §4.9, and hide the total when any share is withheld. | 3b |
| G-10 | P1 | Consent not a designed state | Consent lives only in Settings. A tracking write without consent surfaces `error.consent_required` ("…turn it on in Settings") as inline red text (`QuickLog.tsx` 97–143, `HabitList.tsx`, `MealDetailScreen.tsx` confirm). There's no Today state for never-granted or withdrawn consent, and no child tracking-consent sheet. | UXD-12, D-048, design-system ConsentBanner | `ConsentSheet` (§4.0.1), consent cards on Today, Family and member view, and the value kept across consent. | W1-FE (sheet) + 3b (integration) |
| G-11 | P1 | Habit strip fails target size | `features/goals/Goals.css` `.habit__history` (231–236) is 14 columns, about 20 px wide at 360 px with 3 px gaps (below WCAG 2.5.8's 24 px and the design system's 44 px). `.habit__dow` is 0.625 rem (10 px, below the 12 px floor). | §7.1, design-system §3/§6 | 7-day strip on rows, month grid on detail (UXD-17). | 3b |
| G-12 | P1 | Goal offer and subject picker | `goalOptions.ts` hard-codes metrics and directions instead of `capabilities.goalCategories/goalDirections`. BODY goals lack the D-047 professional-guidance note. The sleep target is entered in minutes. `GoalsScreen.tsx` (78–79) lists **every** CHILD member (not only guarded children) and omits adults the caller is CAREGIVER for. | D-046, D-047, D-044 | §4.7 offer set, `goal.bodyGuidance`, hours+minutes entry, and a picker from the overview relationships. | 3b |
| G-13 | P1 | Insight prose from the server | `features/today/InsightList.tsx` (60–61) renders the server's English `headline`/`detail`. | D-019 (arch §2 "violates D-019") | W3 typed `messageKey`+params. Until then, cap at 2 on Today and track as a known PARTIAL. | BE (W3) + 3b |
| G-14 | P1 | Quick log shape | `QuickLog.tsx` is select + number + Save (3 controls) with sleep in raw minutes (21–26) and weight mixed with water. Its "Saved." auto-hides after 4 s (66). There's no time picker (cannot log for earlier today), no presets, and no value echo on Save. | §4.3, UXD-14, §7.1 2.2.1 | Replace with the Log sheet (§4.3). | 3b (+W1-FE components) |
| G-15 | P1 | Profile completion | `ProfilePanel.tsx` has no **activity level** field (W1 API ready). There's no path to add adult height or weight from the profile. | arch §9.2 P1 profile completion | §4.6. | 3b |
| G-16 | P2 | Family card content | `FamilyWellness.tsx`: side-by-side "Ate in this week's meals: {n}" per member (121, 147–151) invites comparison. Tags are "Adult/Child" rather than relationship (139). Energy kcal appears for a child (206–214). Copy `familyWellness.private`, "{name} keeps their health records private.", imputes intent. | UXD-18, UXD-08, §5.1 | §4.4 card anatomy and copy. | 3b |
| G-17 | P2 | Food-readiness overview on Family, using verdict colours | `HouseholdOverview.tsx` sits on the Family screen, and `.hoverview__status--attention` uses `--verdict-limit-*` (`HouseholdOverview.css` 38–39) for a non-verdict state. | design-system §2.2 (verdict colours are verdict-only), §2.2 IA | Move it to Food as "Your household's food profile" and restyle it with `--brand-subtle`. | 3b |
| G-18 | P2 | Household role on health surfaces | `FamilyScreen.tsx` `HouseholdMemberCard roleLabel` (481), and the consent panel shows the role (`ConsentPanel.tsx` 197–199). | UXD-19, D-044 | Roles only in You → Household. | 3b |
| G-19 | P2 | Naming collisions | `/activity` (`features/activity/ActivityScreen.tsx`) is *product-viewing history* but named "Activity", which collides with ACTIVITY tracking. `category.CLINICAL` reads "Clinical readings". Settings is not "You". There is no larger-text toggle in `SettingsScreen.tsx` `AppearancePanel` (77–95). | §2.2, §5.3, UXD-16 | `/food/recent` "Recently viewed", "Health readings", `/you/appearance` with larger text. | 3b |
| G-20 | P2 | Locale coverage and vocabulary gate | No keys for new error codes `error.access_grant_not_allowed` and `error.age_band_not_valid_for_member`. `i18n.vocabulary.test.ts` lacks the wellness terms in §5.5. | D-019, §5.5 | Add the keys and extend `BANNED` (mind negation-only terms like "missed" in "never missed"; prefer `claimOnly` where apt). | 3b / W1-FE (append-only) |
| G-21 | P2 | BottomSheet a11y | `design-system/BottomSheet.tsx` has no focus trap, a non-inert background, no visible Close button, and no history entry. Android back leaves the page. | design-system §5 Sheet, §7.1 2.5.7 | Trap focus, set `inert` on the app root, add a Close button, and push `#sheet` state. | 3b (design-system) |
| G-22 | P2 | Goals and habits combined page | `GoalsScreen.tsx` holds both, with inline forms, making it long on mobile. | §4.7–4.8 | Split `/goals` and `/habits`, and move add forms into sheets. | 3b (W2) |

---

## 9. Open questions (need a product decision)

| # | Question | Recommended default (what this spec assumes until decided) |
|---|---|---|
| Q-01 | For a household of one, keep the Family tab (quiet card) or switch to a 4-tab layout? | Keep 5 tabs with the quiet card (UXD-04). It gives stable muscle memory and a single discoverable path. |
| Q-02 | Confirm that kcal is never a headline for a child (UXD-08). | Confirm. It is visible one level down, as recorded. |
| Q-03 | STRESS_SCORE is `AgeClass.ANY`, so a guardian can record "stress" for a toddler. Should it be adult-only in `MetricCode`? | Not offered for a child in the UI. Recommend making it adult-only in the domain (BE). |
| Q-04 | Adult capabilities allow CLINICAL and WELLBEING goals (e.g. a BP "at most" goal). Offer them? | Not offered in the W2 UI. A user-chosen BP target reads as a clinical target, and a mood score "is not something to aim at" (D-047's reasoning). |
| Q-05 | `MealResponse.totalNutrition` × `portionFraction` reveals a withheld share. Withhold the total server-side when any share is withheld from the reader, or accept it as a household fact? | UI hides the total (UXD-20). Recommend the server withhold it too (privacy review). |
| Q-06 | A child's share logged by a non-guardian can never reach the child's log (`confirmPortion` is self-only). Add a guardian-confirm path? | Warn before saving (§4.9). Recommend a guardian confirm endpoint in W2. |
| Q-07 | Observations cannot be corrected or deleted (no endpoint). Typos are permanent in history and goals. | Echo the value on Save (UXD-14). Recommend W2 "remove entry" (manual source only, audited). |
| Q-08 | Changing a grant's level (VIEW ↔ MANAGE): is it one POST (replace) or DELETE + POST? This affects atomicity of the review sheet. | Treat as a sequence and report partial failure honestly (§4.5). Prefer a server-side upsert. |
| Q-09 | Offline logging queue? | None in W1–W2 (UXD-13). Revisit once entries can be deleted. |
| Q-10 | Users in several households (`otherHouseholdCount > 0`): a switcher? | Primary household only, plus a quiet note in You → Household. |
| Q-11 | Accept °F temperature input (common on Indian home thermometers), converted to °C at the edge? | Yes, as an input toggle (W2). Store and display °C. |
| Q-12 | Larger-text preference: device-only (localStorage) or account-level (needs an endpoint)? | Device-only in W1. |
| Q-13 | Today's habit ticks need one request with per-habit done-today flags. Today, the UI makes 2 requests per habit (history plus consistency). Add a per-habit day list to `/today` or a habits endpoint? | Today shows the count and links to the Log sheet's Habits panel. Recommend adding `habits[]` with `{id, name, doneToday}` to `/today`. |
| Q-14 | Should activity level be offered on a child's profile (the server accepts it)? | Adult-only in the UI. It has no child use that doesn't drift toward energy estimates. |
| Q-15 | Is BMI computed server-side when height and weight exist? | The UI never asks for BMI and shows it only if the server returns it. |

---

## Appendix A: Proposed design-system additions (for the design-system owner)

### A.1 Tokens (add to `tokens.css`; no raw values in components)

| Token | Value | Use |
|---|---|---|
| `--chart-bar` | `var(--brand)` | SUM bars, LATEST dots |
| `--chart-goal-line` | `var(--text-secondary)` (dashed 4/4) | goal line (SUM goals only) |
| `--chart-grid` | `var(--border)` | gridlines (decorative) |
| `--chart-axis-text` | `var(--text-tertiary)` | axis labels (5.02:1) |
| `--pending-bg` / `--pending-fg` | `var(--surface-sunken)` / `var(--text-secondary)` | awaiting-confirmation chip |
| `--attention-bg` / `--attention-rule` | `var(--brand-subtle)` / `var(--brand)` | attention strip (never danger) |
| Larger-text block | see §7.1 | UXD-16 |

There is one chart hue only, because members are never charted together (§6.7), so no categorical palette is needed.

### A.2 Components

| Component | Purpose | Key a11y |
|---|---|---|
| `MetricTile` | label, value+unit, sub-line; link plus sibling [+] | link name composes label+value+context |
| `StatusLine` | "Not logged" / "Not shared with you" | full sentence for sr |
| `AttentionStrip` | ≤2 informational rows | `role="region"` with label; no live region on load |
| `DayNavigator` | ‹ date › with `?date=` | buttons named "Previous day" / "Next day"; › disabled on today |
| `LogSheet` | BottomSheet with an in-sheet stack (tiles → form) | focus to h2 on push; back restores focus to the tile |
| `SubjectSwitcher` | "Logging for" | native `<select>` or a listbox sheet |
| `PresetChips` | water and portion presets | radiogroup |
| `Stepper` | − / + with numeric input | buttons named "Decrease/Increase {metric}" |
| `ScopeLevelControl` | Not shared / Can see / See and log | fieldset+legend, radiogroup, arrows |
| `RelationshipChip` | You / You look after / You help / Shares with you | text, not colour |
| `MemberCard` | Family card (identical anatomy) | whole-card link only when there's something to open |
| `ConsentSheet` | adult / child variants | equal-size actions; attestation checkbox for child |
| `PendingChip` | "Waiting for {name}" | text first; glyph `aria-hidden` |
| `OfflineBanner` | offline notice | `role="status"` |
| `HistoryChart` | SVG bars and dots plus a table toggle | §7.1 chart pattern |
| `AccessLogRow` | actor · action · scope · relationship · time | plain sentence |

# Design System

**Version:** 2.1 · **Date:** 2026-09-14
**Binding decisions:** D-003 (verdict is the headline, score is secondary), D-004 (closed verdict enum), D-019 (no hard-coded strings), D-055 (consumer identity and information architecture), D-074 (fluid desktop width, category grid, carousel rails, focus-ring gating)

> **v2 note.** Version 1 (2026-09-05, teal on white, system font, Unicode glyphs) was a sound
> token architecture wearing an admin-console skin. Version 2 keeps the token architecture, the
> verdict and allergen colour semantics and every accessibility rule, and replaces the identity:
> a warm cream ground, a terracotta brand, a fresh-green accent, a self-hosted geometric
> typeface and a real icon set. Nothing about *what may be said* changed; how it looks did.

> **v2.1 note (D-074).** Four layout fixes reported directly against the running app: the shell
> was capped at a fixed 1240px, leaving large dead margins on a 1440–1920px monitor; the search
> pill's focus halo could read as "always on"; the category rail required a horizontal scroll to
> see all 13 categories; and product rails offered no way to move them other than a raw swipe/
> drag. `--shell-max` is now fluid (`min(100%, 1800px)`); categories are a wrapped `CategoryGrid`
> (every category visible, no scroll); product rails gained arrow controls
> (`ProductCarousel`, desktop only — mobile keeps the swipe) with `scroll-snap-type: mandatory`;
> the search pill's stronger focus halo is now gated to `:has(:focus-visible)` so it only ever
> appears in response to a real focus event, never unprompted. See §7 and §8 for the detail.

---

## 1. Brand rationale — "Fresh Kitchen"

A consumer food product for India, not a dashboard and not hospital software. The identity has
to say **appetite, freshness, trust, quality, warmth** and **modern technology** without
borrowing Zomato's red or Blinkit's yellow, and without colliding with the semantic colours the
product already uses to say something true (the verdict ramp, the allergen flag, danger).

- **Terracotta brand (`#C2410C`)** — the colour of clay, paprika and a tandoor: appetite and
  warmth. It sits clear of every semantic hue: the verdict ramp is green / blue / ochre /
  violet, the allergen flag is magenta, danger is a cool crimson.
- **Cream ground (`#FBF9F5`) with white cards** — a white card on a cream table reads as an
  object, not a box drawn on a screen. It is what stops a grid of cards feeling like a
  spreadsheet.
- **Fresh green accent (`#1F7A4D`)** — reserved for positive moments only: saved, done, success.
  Never used for a verdict (EVERYDAY keeps its own, slightly different green).
- **Warm charcoal text (`#1C1917`)** — never pure black on cream.

Nothing is red because nothing here is a danger (D-004). Nothing moralises food.

---

## 2. Colour

All ratios are measured against the surface the colour is used on. Body text ≥4.5:1, large
text and interactive boundaries ≥3:1 (WCAG 2.2 AA).

### 2.1 Neutrals and brand

| Token | Light | Ratio on `--bg` | Dark | Ratio on dark `--bg` |
|---|---|---|---|---|
| `--bg` | `#FBF9F5` | — | `#161311` | — |
| `--surface` | `#F4F0E8` | — | `#1F1B18` | — |
| `--surface-raised` / `--card-bg` | `#FFFFFF` | — | `#262120` / `#221E1B` | — |
| `--surface-sunken` | `#ECE6DA` | — | `#110F0D` | — |
| `--text` | `#1C1917` | **16.6:1** | `#F3EDE6` | **16:1** |
| `--text-secondary` | `#57534E` | **7.3:1** | `#C2B8AD` | **9.6:1** |
| `--text-tertiary` | `#6F6A63` | **5.1:1** | `#9B9187` | **6.0:1** |
| `--border` (decorative) | `#EBE5DA` | decorative only | `#2F2925` | — |
| `--border-interactive` | `#8A8074` | **3.7:1** | `#746B62` | **3.5:1** |
| `--brand` | `#C2410C` | **4.9:1** (5.2:1 on white) | `#F0894F` | **7.4:1** |
| `--brand-hover` | `#9A3412` | 7.0:1 | `#F5A071` | 9.1:1 |
| `--brand-subtle` / `--brand-soft` | `#FDEEE4` / `#F9D9C6` | tints | `#3A2116` / `#4A2A1B` | tints |
| `--on-brand` | `#FFFFFF` | **5.2:1** on brand | `#2A1207` | 7.4:1 |
| `--accent` | `#1F7A4D` | **5.1:1** | `#6FD3A6` | 9.9:1 |
| `--focus-ring` | `#C2410C` | 4.9:1 | `#F0894F` | 7.4:1 |

`--border` is documented as decorative. Any boundary that carries meaning (an input outline, a
selected chip, a control edge) uses `--border-interactive`.

### 2.2 Verdict colours — unchanged, and why

The verdict ramp is not a traffic light: fresh green → informational blue → warm ochre →
neutral violet. `DOESNT_FIT` is violet because it means "conflicts with a preference you
declared", a match failure, not a hazard. The values and their measured ratios are exactly
those of v1 (§2.2 there). The chips are now pill-shaped with a translucent border so they sit
on white cards without a hard outline; label + glyph + border style still carry the meaning.

### 2.3 Allergen flag — a separate system, unchanged

Deep magenta (`#8F1746` on `#FAE6ED`), a full-width banner with a white disc for the mark.
Used nowhere else. Never red.

### 2.4 Feedback

`--danger-fg #A11C2E` / `--danger-bg #FBE8EA` — deliberately a *cool* crimson so a destructive
control never reads as the (warm) brand. `--success-*` reuse the EVERYDAY green.

### 2.5 Avatars

Six warm tints (`--avatar-N-bg` / `--avatar-N-fg`), each ≥4.5:1 between ink and ground in both
themes. A person's tint is derived from their name (`Avatar.tsx`) so they look the same on
Family, Today, sharing and meals; "you" is always tint 1. The hue is decorative; the initial
and the name carry the meaning.

### 2.6 Category identity

Each catalogue category keeps a soft pastel pair (`--cat-*`) mixed into the card ground at 20%
(`CategoryArt.tsx`), so the fallback for a product with no photo is a calm icon on a tint that
works in both themes.

---

## 3. Type

**Plus Jakarta Sans**, variable 400–800, self-hosted from `public/fonts/` in two unicode-range
files (latin, latin-ext; ~49 KB total), `font-display: swap`. Self-hosted rather than a
Google Fonts link because this product reaches health data and makes no third-party requests
(D-018). The system stack (with Noto Sans Devanagari for Hindi, D-019) stays as the fallback
and paints first on a slow connection.

| Token | Size (phone / ≥768px) | Weight | Use |
|---|---|---|---|
| `--text-hero` | 2rem / 2.5rem | 800 | Home value statement only |
| `--text-display` | 1.75rem / 2rem | 800 | Screen titles, product name |
| `--text-h1` | 1.5rem | 800 | Nested screen headings |
| `--text-h2` | 1.125rem | 800 | Section headings |
| `--text-card-title` | 0.9375rem / 1rem | 700 | Card names |
| `--text-body` | 1rem | 400 | Default; floor for body copy, stops iOS input zoom |
| `--text-sm` | 0.875rem | 400 | Secondary detail |
| `--text-xs` | 0.75rem | 500–700 | Labels, provenance, eyebrows — never a sentence a user must read to decide |

Headings use `--tracking-tight` (−0.02em); uppercase eyebrows use `--tracking-caps` (0.06em).
`html[data-text-size="large"]` (Settings → Appearance) scales the whole ramp ~1.2× and raises
tap targets to 48/56px.

---

## 4. Space, radius, elevation, motion

**Spacing** — 4px base, `--space-1` 4 … `--space-8` 64.

**Radius** — `--radius-xs` 6 (tags) · `--radius-sm` 10 (inputs, small tiles) · `--radius-md` 16
(cards) · `--radius-lg` 22 (panels, row groups) · `--radius-xl` 28 (hero panels, sheets) ·
`--radius-full` (pills, buttons, avatars). Buttons and chips are pills; cards are not.

**Elevation** — used to lift white cards off the cream ground, sparingly:
```
--shadow-sm: 0 1px 2px rgb(28 25 23 / .05), 0 1px 1px rgb(28 25 23 / .03);   /* cards at rest */
--shadow-md: 0 6px 18px rgb(28 25 23 / .08), 0 2px 4px rgb(28 25 23 / .04);  /* hover, toasts */
--shadow-lg: 0 -10px 32px rgb(28 25 23 / .16);                               /* bottom sheets */
--shadow-float: 0 8px 24px rgb(194 65 12 / .28);                             /* the Log button */
```

**Motion** — `--dur-fast` 120ms · `--dur-base` 200ms · `--dur-slow` 320ms; `--ease` and
`--ease-out` (`cubic-bezier(0.16, 1, 0.3, 1)`) for things that arrive (sheets, toasts). All
motion collapses under `prefers-reduced-motion`.

---

## 5. Icons

`design-system/Icon.tsx`: ~50 inline SVG icons on a 24×24 grid, 1.75px rounded strokes,
`currentColor`. No icon library. Replaces the Unicode symbol glyphs of v1 (⌂ ⌕ ▣ ⚙ …), which
came from different symbol blocks and read as placeholder characters. Decorative by default
(`aria-hidden`); an icon that is the only content of a control takes `label`.

---

## 6. Components

Every interactive component implements `default · hover · focus-visible · active · disabled`,
plus `loading` where async and `error` where it holds a value.

| Component | File | Notes that matter |
|---|---|---|
| `PageHeader` | `PageHeader.tsx` | Title, eyebrow, lede, back link, right-aligned actions. Every screen uses it so titles land at the same place and weight. |
| `Button` | `Button.tsx/.css` | Pill. primary / secondary / ghost / destructive; `sm/md/lg`; loading keeps the label. `.icon-btn` for round icon-only controls. |
| `SearchBar` | `SearchBar.tsx` | A pill with the search icon leading, a clear control, and the barcode scanner at the trailing edge. `variant="hero"` on Home. |
| `Card` | `Card.tsx` | The one bordered surface: raised (white, hairline, `--shadow-sm`) or sunken (`--surface`). |
| `RowGroup` / `Row` | `RowGroup.tsx` | The account-list pattern: one rounded surface, hairlines between rows, optional leading icon, optional trailing control. |
| `ProductCard` | `ProductCard.tsx` | Media first, brand, name, size, then the decisive line (verdict or the category's headline nutrient). Stretched link + a sibling Save button — never a button inside a link. |
| `CategoryRail` / `ProductRail` / `ProductGrid` / `Chip` | `Rails.tsx` | Native overflow rails with scroll snap; category tiles are a glyph on a tint; chips are pills, the active chip inverts to charcoal. |
| `VerdictChip` | `VerdictChip.tsx` | Label + glyph + colour + border style. Still the largest, highest element on a product screen (D-003). |
| `AllergenFlag` | `AllergenFlag.tsx` | Full-width magenta banner, `role="status"`, above everything else. |
| `Badge` | `Badge.tsx` | Small labelled tag (data quality, saved, relationship). Text carries the meaning. |
| `Avatar` | `Avatar.tsx` | Initial on a name-derived warm tint; `child` draws a ring. |
| `ProgressTile` | `ProgressTile.tsx` | Today's metric tile: icon, name, value, optional goal bar, sibling **+**. Never a zero for "not logged". |
| `LogSheet` / `BottomSheet` / `ConfirmSheet` | `LogSheet.tsx`, `BottomSheet.tsx` | The centre-nav quick-entry sheet (icon tiles per kind) and the generic sheet. Escape closes, background inert, back button closes. |
| `Toast` | `Toast.tsx` | One confirmation at a time above the bottom nav; `role="status"`. Only for things that already happened; errors stay inline. |
| `EmptyState` / `ErrorState` / `Skeleton` | `States.tsx` | Icon on a disc, one sentence, one action. Error distinguishes *unreachable* from *not found*. |
| `RecommendationCard` | `RecommendationCard.tsx` | An insight: white card with a brand rule; fact first, why second, never a grade. |
| `WeekStrip` / `HistoryChart` | as v1 | Inline SVG, one hue, gaps for unlogged days; table always available. |
| `.segmented` | `base.css` | Two or three peers, one selected — sign in / create account, scan / photograph. |
| `.page-head`, `.field`, `.note`, `.eyebrow`, `.form-error/.form-success` | `base.css` | Shared primitives every screen builds on. |

---

## 7. Layout and navigation

- **Under 1024px** — a compact sticky header (brand, account avatar) with the search pill under
  it; on Home the header is only the brand row because the hero carries the search. A bottom
  navigation of five slots with a raised terracotta **Log** disc in the centre.
- **1024px and up** — one top bar: brand, search, icon+label links, account.
- **Content width is fluid (D-074):** `--shell-max: min(100%, 1800px)` — the shell fills the
  window minus its gutter (`--page-gutter`, 16px mobile / 32px desktop) up to a wide cap, rather
  than sitting in a fixed 1240px column with large dead margins on a wider monitor.
  `--layout-dashboard-max` tracks `--shell-max`. `--layout-form-max` (560px), `--layout-reading-max`
  (68ch) and `--layout-list-max` (840px) deliberately do **not** — a form or a block of prose keeps
  its own readable measure regardless of window width; only shells built for browsing/discovery
  (`.layout-dashboard`, the app shell's own `<main>`) go fluid.
- **Category discovery is a grid, not a scroller (D-074):** `CategoryGrid` wraps every category
  into `repeat(auto-fit, minmax(…))` tiles — all of them visible at once, no horizontal scroll,
  because a category list is short and worth seeing in full at a glance (unlike a product rail,
  which is inherently long). Reuses the existing `.cat-tile` glyph-on-tint styling.
- **Product rails are carousels on a pointer, swipes on a touchscreen (D-074):** `ProductCarousel`
  (used inside `ProductRail`, so every existing caller's API is unchanged) adds previous/next
  arrow buttons, shown only at ≥1024px and hidden below it — a mouse pointer benefits from a
  click target that native touch scrolling doesn't need. `scroll-snap-type: x mandatory` (was
  `proximity`); `scroll-behavior: smooth`, dropped to `auto` under `prefers-reduced-motion`.
- **Information architecture (D-055):** Home · Today · Log · Family · You when signed in; Home ·
  Search · Scan · Sign in when not. Home is the landing screen for everyone.
- Layout archetypes (`.layout-dashboard/.layout-list/.layout-form/.layout-reading`) are otherwise
  unchanged.

---

## 8. Accessibility rules (unchanged, restated)

- Touch targets ≥44×44px, ≥48px for primary actions; ≥8px between adjacent targets.
- Focus: `outline: 2px solid var(--focus-ring); outline-offset: 2px`, `:focus-visible` only,
  never removed; the ring follows the element's own radius.
- **The search pill's stronger halo is `:has(:focus-visible)`-gated (D-074)**, not
  `:focus-within` alone — a quiet border-colour change happens on any focus, the full
  `0 0 0 3px var(--brand-subtle)` halo only on a real `:focus-visible` match, and never at all
  while the field is untouched (there is no autofocus anywhere in the app). Measured against a
  real browser: Chromium's own `:focus-visible` heuristic treats a text input's focus as
  "visible" on *either* a pointer click or a keyboard tab — by design, since a user needs to see
  where their next keystroke lands regardless of how they got there — so on the search pill
  specifically this still shows the full halo on a tap, not only on Tab. That is the correct,
  accessible outcome for a typing field, not a bug; the gated pattern is written for reuse on
  non-typing interactive elements (buttons, cards, chips) where pointer and keyboard focus do
  differ. The input inside the pill has its own global `:focus-visible` outline suppressed
  (D-061): the pill's border and halo are the one focus indicator, so a second rectangular ring
  is never drawn inside it. This is the only place the global ring is replaced, and it is
  replaced by an equivalent indicator on the containing control, never removed.
- The search box's suggestions are an ARIA combobox (`aria-expanded`, `aria-controls`,
  `aria-activedescendant`); arrow keys move, Enter picks, Escape closes; each option is at
  least 44px tall.
- Colour never carries meaning alone: verdicts, chips, badges and avatars all carry text.
- One `<main>`, landmarks for nav, headings in order.
- Search results, verdicts and toasts announce through polite live regions; the allergen flag
  uses `role="status"`.
- Reduced motion honoured globally; layout survives 200% zoom and 320px width without
  horizontal scroll (the page body never scrolls sideways; wide tables scroll inside `.scroll-x`).
- `<html lang="en-IN">`; every string from the locale bundle (D-019), guarded by the
  banned-vocabulary test.

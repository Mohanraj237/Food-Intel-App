# Design System

**Version:** 1.0 · **Date:** 2026-09-05
**Binding decisions:** D-003 (verdict is the headline, score is secondary), D-004 (closed verdict enum), D-019 (no hard-coded strings)

---

## 1. Brand rationale

The product sits in an awkward gap: it carries health information, but it is not a health app. Indian consumers already associate clinical blues and white coats with anxiety, and associate red/green traffic lights with judgement. Both are wrong here — the product's whole thesis is *better, not perfect*, and a design that moralises food undermines the copy before anyone reads it.

So the visual language is **modern Indian consumer utility with a calm, credible accent**: warm-neutral surfaces, a confident teal-green brand, generous type, card-based scanning, bottom navigation. It should feel closer to a well-made shopping app than to a diagnostic report — fast, visual, unfussy — while the evidence and provenance details sit one tap away for the people who want them.

The teal-green (`#0E7C6B`) is deliberately not the red of Zomato or the yellow of Blinkit. It reads as fresh and trustworthy without reading as medical.

---

## 2. Colour

All ratios below are measured, not estimated. Body text targets ≥4.5:1, large text and interactive boundaries ≥3:1 (WCAG 2.2 AA).

### 2.1 Neutrals and brand

| Token | Light | Ratio on bg | Dark | Ratio on bg |
|---|---|---|---|---|
| `--bg` | `#FFFFFF` | — | `#121513` | — |
| `--surface` | `#F7F8F7` | — | `#1B1F1D` | — |
| `--surface-raised` | `#FFFFFF` | — | `#232826` | — |
| `--text` | `#14181A` | **17.87:1** | `#E8EDEA` | **15.52:1** |
| `--text-secondary` | `#4A5257` | **7.97:1** | `#A8B2AE` | **8.44:1** |
| `--text-tertiary` | `#697077` | **5.02:1** | `#8A9490` | **5.88:1** |
| `--border` (decorative) | `#E3E7E5` | 1.25:1 — decorative only | `#2A302D` | — |
| `--border-interactive` | `#7E8884` | **3.4:1** | `#6E7873` | **4.02:1** |
| `--brand` | `#0E7C6B` | **5.10:1** | `#4ECFB6` | **9.56:1** |
| `--brand-hover` | `#0B6558` | 6.96:1 | `#3FBFA6` | 8.07:1 |
| `--brand-subtle` | `#E6F4F1` | — | `#12302A` | — |
| `--on-brand` | `#FFFFFF` | **5.10:1** on brand | `#0A1F1B` | — |
| `--focus-ring` | `#0E7C6B` | **5.10:1** | `#4ECFB6` | **9.56:1** |

`--border` is explicitly documented as decorative. Any boundary that carries meaning — an input outline, a selected state, a control edge — uses `--border-interactive`, which meets 3:1.

### 2.2 Verdict colours — and why they are not a traffic light

A green→amber→red ramp would say *good food / bad food*. The product says the opposite: frequency and fit, not virtue (Constitution §14, §88). So the ramp is **not** on a safety axis. It moves from a fresh green, through an informational blue, to a warm ochre, to a neutral violet. Nothing is red, because nothing here is a danger.

| Verdict | Text (light) | Tint (light) | Ratio | Text (dark) | Tint (dark) | Ratio |
|---|---|---|---|---|---|---|
| `EVERYDAY` | `#126446` | `#E4F1EA` | **6.15:1** | `#6FD3A6` | `#16281F` | **8.50:1** |
| `OCCASIONAL` | `#175A85` | `#E3EEF6` | **6.28:1** | `#7CC2EC` | `#152430` | **8.13:1** |
| `LIMIT` | `#7D5100` | `#FAEFD9` | **6.04:1** | `#E8B75A` | `#2A2113` | **8.57:1** |
| `DOESNT_FIT` | `#54458A` | `#ECE8F7` | **6.72:1** | `#B3A4E8` | `#221E33` | **7.22:1** |
| `INSUFFICIENT_DATA` | `#525960` | `#EDEFF1` | **6.16:1** | `#A6AFB5` | `#1E2224` | **7.19:1** |

Every verdict colour also passes on plain white (6.89–8.09:1), so a chip works on any surface.

**`DOESNT_FIT` is violet, not red**, on purpose. It means "conflicts with a preference you declared" — a Jain user seeing an onion-containing product. That is a match failure, not a hazard, and colouring it like a hazard would be a lie told in pixels.

### 2.3 Allergen flag — a separate system

| Token | Light | Tint | Ratio | Dark | Tint | Ratio |
|---|---|---|---|---|---|---|
| `--allergen` | `#8F1746` | `#FAE6ED` | **7.40:1** | `#F09BBB` | `#2E1522` | **8.11:1** |

Deep magenta, used nowhere else in the product. It is not on the verdict ramp because an allergen is not a verdict (D-004): it is a hard flag with its own field, its own position at the top of the screen, and it short-circuits the engine. Making it visually unrelated to the verdict chips is how a user learns, without being told, that it is a different kind of statement.

It is not red either. Red carries "danger/stop" and would push toward "this will harm you", which is precisely the claim we must not make — the wording is *contains*, never *will harm you*, and we never claim allergen-free.

### 2.4 Non-colour redundancy (mandatory)

Colour never carries meaning alone (Pack 10 §6, AC-10.6). Every verdict chip renders **text label + distinct glyph + colour**:

| Verdict | Glyph | Shape cue |
|---|---|---|
| `EVERYDAY` | filled circle `●` | solid chip |
| `OCCASIONAL` | half circle `◐` | solid chip |
| `LIMIT` | open circle with bar `⊖` | solid chip |
| `DOESNT_FIT` | crossed circle `⊘` | dashed border |
| `INSUFFICIENT_DATA` | `?` | dotted border |
| allergen flag | `▲` outline | full-width banner, not a chip |

---

## 3. Type

System stack — no webfont. A webfont on a mid-range Android on a shop's poor connection costs a visible render delay for a product whose entire promise is speed.

```
--font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
        "Noto Sans", "Noto Sans Devanagari", Arial, sans-serif;
--font-mono: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
```

`Noto Sans Devanagari` is in the stack from day one so that Hindi (D-019, deferred) does not require a font change later.

| Token | Size | Line height | Weight | Use |
|---|---|---|---|---|
| `--text-display` | 1.75rem / 28px | 1.2 | 700 | Product name on detail |
| `--text-h1` | 1.375rem / 22px | 1.25 | 700 | Screen title |
| `--text-h2` | 1.125rem / 18px | 1.3 | 600 | Section heading |
| `--text-body` | 1rem / 16px | 1.5 | 400 | Default. Never below this for body copy |
| `--text-sm` | 0.875rem / 14px | 1.45 | 400 | Secondary detail |
| `--text-xs` | 0.75rem / 12px | 1.4 | 500 | Labels, provenance, timestamps only — never a sentence a user must read to decide |

16px body is a floor, not a default to be trimmed. It also stops iOS Safari zooming on input focus.

---

## 4. Space, radius, elevation, motion

**Spacing** — 4px base: `--space-1` 4 · `2` 8 · `3` 12 · `4` 16 · `5` 24 · `6` 32 · `7` 48 · `8` 64.

**Radius** — `--radius-sm` 6px (chips, inputs) · `--radius-md` 12px (cards) · `--radius-lg` 20px (sheets) · `--radius-full` 999px (pills, avatars).

**Elevation** — used sparingly; a flat, bordered card reads as calmer and renders faster.
```
--shadow-sm: 0 1px 2px rgb(0 0 0 / 0.06);
--shadow-md: 0 4px 12px rgb(0 0 0 / 0.08);
--shadow-lg: 0 -8px 28px rgb(0 0 0 / 0.16);   /* bottom sheets, upward */
```

**Motion** — `--dur-fast` 120ms · `--dur-base` 200ms · `--dur-slow` 320ms; easing `cubic-bezier(0.2, 0, 0.2, 1)`. All motion is wrapped in `@media (prefers-reduced-motion: reduce)`, which collapses durations to `1ms` and removes transforms.

**Layers** — `--z-sticky` 10 · `--z-bottom-nav` 20 · `--z-sheet-backdrop` 30 · `--z-sheet` 31 · `--z-modal` 40 · `--z-toast` 50.

---

## 5. Components

Every interactive component implements: `default` · `hover` · `focus-visible` · `active` · `disabled` · `loading` where async · `error` where it holds a value.

| Component | Notes that matter |
|---|---|
| `Button` | primary / secondary / ghost / destructive. Min height 48px. Loading shows a spinner **and** keeps the label — a bare spinner loses context for screen readers. |
| `Input` / `SearchField` | 48px, 16px text, `--border-interactive`. Label always present (visually or `sr-only`); placeholder is never the only label. Error text is bound with `aria-describedby`. |
| `Card`, `ProductCard` | Whole card is one link target. Image is lazy, aspect-ratio boxed to prevent layout shift, with a neutral placeholder — never a stretched or cropped-to-nothing fallback. |
| `VerdictChip` | Label + glyph + colour. Renders per member. This is the largest, highest element on a product screen (D-003). |
| `AllergenFlag` | Full-width banner, top of screen, `--allergen`, `▲`, `role="status"`. Never inside the verdict row. |
| `ScorePanel` | Collapsed by default. Contains the 0–100 value and its factor breakdown. Never above the verdict, never the largest element (D-003). |
| `NutrientRow` | Value per serving **and** per 100g side by side (D-010), with the declared serving stated and flagged when implausible. |
| `ProvenanceNote` | Source + last-verified date + data-quality state. Present on every screen showing a nutrient value. |
| `Sheet` | Bottom sheet, `--radius-lg` top corners, drag handle, focus trap, `Esc` to close, background inert. Thumb-reachable actions. |
| `BottomNav` | Fixed, 5 items max, 56px + safe-area inset. Labels always visible — icon-only navigation fails for low-literacy and screen-reader users alike. |
| `Skeleton` | Matches the final layout's box sizes so nothing jumps. `aria-busy` on the container. |
| `EmptyState` | Illustration-free: an icon, a sentence, one action. Every empty state names the next step. |
| `ErrorState` | Distinguishes *not found* from *service unavailable* (Constitution §46). Always offers a retry or an alternative path. |
| `ConsentBanner` | Plain-language purpose, policy version, explicit grant/decline. Decline is as prominent as grant. |
| `ComparisonTable` | Horizontally scrollable inside its own container; the page body never scrolls sideways. First column sticky. |

---

## 6. Accessibility rules

- **Touch targets** ≥ 44×44px, ≥ 48px for primary actions; ≥ 8px between adjacent targets.
- **Focus** — `outline: 2px solid var(--focus-ring); outline-offset: 2px`. Never removed. `:focus-visible` so pointer users do not see rings on click.
- **Never colour alone** — see §2.4.
- **Landmarks** — one `<main>`, `<nav>` for bottom nav, headings in order with no level skipped.
- **Live regions** — search results and verdict updates announce via `aria-live="polite"`; the allergen flag uses `role="status"`.
- **Reduced motion** — honoured globally.
- **Zoom** — layout survives 200% zoom and 320px width without horizontal scroll.
- **Language** — `<html lang="en-IN">`; every string from the locale bundle (D-019).

## 7. Icons

Inline SVG, no icon library — avoids a licence question and a bundle cost for the ~20 icons this product needs. 24×24 grid, 1.5px stroke, `currentColor`, `stroke-linecap="round"`. Decorative icons take `aria-hidden="true"`; an icon that is the only content of a control takes an `aria-label`.

# UX Principles

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §20/21/22/41/45/85/86/87). Verbatim content, reorganized. Visual/color/typography specifics live in `docs/design/design-system.md` — this doc covers interaction and information-architecture principles.

---

## UX Principles

The UX/UI should be influenced by the strengths of Zomato and Blinkit — used as interaction references, not as assets to copy. Take inspiration from: search-first interaction · fast navigation · visual cards · strong hierarchy · product imagery · clear action buttons · bottom navigation · context-sensitive recommendations · responsive states · rapid feedback · polished empty/loading/error states.

Do NOT copy: branding · logos · exact screen layouts · copyrighted assets · exact visual identity · trade dress.

Create a unique brand identity based on: **Trust + simplicity + modern consumer commerce.** The application should not feel like hospital software. It should not feel like a fear-based health app. It should feel like a high-quality consumer utility.

---

## Mobile-First

Design mobile-first. Prioritize: one-handed use · large touch targets · fast search · quick scanning · simple navigation · bottom-sheet interactions when appropriate · fast back navigation · graceful network failures · lightweight image handling · skeleton loaders · clear retry states.

The most important interaction should feel fast: `Search → Select product → Understand → Decide`. Avoid unnecessary screens.

---

## Design System (meta-guidance)

Maintain a centralized design system covering: typography · spacing · radius · elevation · icons · buttons · inputs · cards · sheets · navigation · badges · states · charts · alerts · product cards · score displays. Do not create one-off styles repeatedly. Use reusable components. The design system must remain consistent across screens. (Concrete tokens, colors, components: `docs/design/design-system.md`.)

---

## Accessibility

Validate: semantic HTML · keyboard navigation · focus states · form labels · accessible error messages · screen-reader behavior · color contrast · non-color-only status communication · touch target size. Accessibility is part of feature completion, not a separate pass. (Testing coverage: `docs/testing/testing-strategy.md`.)

---

## Empty States

Every important screen must have a designed empty state. Examples:

- No products: "We couldn't find this product yet. Capture the nutrition label to analyze it."
- No household: "Create your household to personalize food decisions."
- No activity: "Your recent food decisions will appear here."

---

## Frontend Product Language

The frontend should communicate: "Here's what we know." → "Here's why." → "Here's how it fits you." → "Here are better options." Avoid overwhelming the user with raw technical detail. Detailed nutrition/evidence can be available progressively.

---

## Progressive Disclosure

- **Primary screen:** Decision
- **Secondary:** Why?
- **Tertiary:** Nutrition, Ingredients, Evidence, Methodology

Do not force all scientific detail into the first screen.

---

## Product Result Structure

A product result should conceptually include, in order:

```text
Product → Decision → Key reasons → Nutrition highlights → Personal/family fit → Alternatives → Detailed evidence
```

The first few seconds should communicate the decision. (This ordering is implemented as the fixed information order on the product detail screen — see `frontend/src/features/product/ProductScreen.tsx`.)

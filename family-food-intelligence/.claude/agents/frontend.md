# Frontend Engineer — Agent Instructions

You implement React/TypeScript work under `frontend/src/`. Read `.claude/state.md` and `.claude/current-task.md` first — a significant amount of frontend work already exists uncommitted (ProductScreen, ProductCard, Rails, CategoryArt, catalog.ts) and may need review rather than rebuilding.

## Load before writing code

- `docs/design/design-system.md` — tokens, colors, components. Do not invent one-off styles; extend `design-system/` primitives.
- `docs/design/ux-principles.md` — progressive disclosure, product result structure, empty states, accessibility.
- `docs/architecture/engineering-principles.md` "Frontend Architecture" section.
- `docs/architecture/system-architecture.md` §9 for the intended folder layout (`app/ design-system/ features/ lib/ locales/`).

## Non-negotiable structural rules

- **The backend is the only authority on authorization and business rules.** The frontend renders what the API returns; it never decides authorization or computes a verdict itself.
- **No hard-coded user-facing strings.** Everything goes in `frontend/src/locales/en-IN.json` (D-019). There is a lint/test gate for this — `frontend/src/lib/i18n.vocabulary.test.ts` also scans for banned vocabulary; know what that list is (`docs/product/product-policy.md` "Food Language") before writing new copy.
- Server state via TanStack Query; client state stays local. Don't add a global store unless something genuinely needs one.
- Typed API client: hand-written now (`frontend/src/lib/*.ts`), intended to become OpenAPI-generated later. If you change a type here, you are changing the contract — check whether the backend actually implements it (see `.claude/current-task.md`; several endpoints in `lib/catalog.ts` are currently client-only, with no backend behind them yet).
- Every async surface needs a loading skeleton, an error state, and an empty state — not just a happy path.
- Product screen information order is fixed (`docs/design/ux-principles.md` "Product Result Structure"): what it is → allergen hard flag → per-person verdict → why → alternatives → provenance → full panel. The numeric score is never the largest element and never appears above the verdict (D-003).

## Before reporting done

- `npm test` green, including the banned-vocabulary gate.
- Confirm which API calls in your change are against real, implemented backend endpoints vs. assumed contracts — state this explicitly (`CLAUDE.md` §8 statuses).
- Accessibility: keyboard nav, focus states, labels, contrast — not a separate follow-up pass.

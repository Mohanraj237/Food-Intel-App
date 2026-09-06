# Post-MVP Backlog

The only sanctioned landing place for ideas that are not in the MVP. Adding an item here is free; moving one into active scope requires a decision-log entry and a change to `requirements-traceability.md`.

---

## Release 0.2 — the next honest increment (Pack 04 "Should have")

| Item | Why it waits | Entry condition |
|---|---|---|
| Saved products and comparisons | Not required to test the thesis | Cohort asks for it |
| Recent-scan history beyond the current shallow list | Same | Retention signal exists |
| Product formulation-change notifications ("reformulated on…") | Needs version history that only accumulates with time | ≥6 months of version data |
| Hindi UI | D-019; architecture is ready, translation is the work | Cohort composition justifies it |
| Correction queue ops console | MVP has the queue; the console is manual for now | Correction volume exceeds manual handling |
| Simple weekly packaged-food insights | Risks becoming the dashboard the product deliberately isn't | Only if it demonstrably drives a better decision |
| Basket analysis (select 5–20 products → substitutions) | Pack 04 R0.2; needs category-complete data | Category coverage in ≥3 categories |
| Receipt upload | Purpose-limitation and minimisation questions unresolved | Privacy counsel sign-off ⚖️ |

## Release 1+ (Pack 04 "Later")

Legitimate order-history integrations · low-friction meal capture by voice or photo · Indian recipe intelligence · household nutrition trends · B2B2C partner dashboards · clinical/diagnostic integrations where legally appropriate · commerce deep links · regional languages beyond Hindi · elder profiles requiring proxy management ⚖️ · FSSAI Indian Nutrition Rating display (blocked on a consumable data feed) · native app shell (only if D-006's reversal condition triggers).

## Do not build until validated (Pack 04, PANTRY, brief §30)

Full calorie diary · exercise or wearable tracking · sleep · meditation · social network or community feed · doctor marketplace · telemedicine · pharmacy · insurance claims workflows · broad "all diseases" personalisation · child engagement or gamification · cart optimisation requiring price and stock by pin code · full grocery marketplace or checkout.

## Permanently excluded — not a backlog, a boundary

These are not "later". They are architectural commitments that must survive revenue pressure (D-012, PANTRY 01 §5, Constitution §30):

- Retail media, sponsored ranking, paid placement in health recommendations
- Advertising of any kind; profiling for advertising
- Sale of individual or household consumption data
- Any capability directing advertising, engagement mechanics or notifications at a child
- Weight, body-fat or body-composition targets for a minor — a child-safety commitment independent of law
- Diagnosis, treatment, prescription or medical triage

The corresponding code paths do not exist and an architecture test asserts their absence. Adding any of them is a decision-log entry that reverses D-012, not a backlog groom.

## Deferred with a specific external dependency

| Item | Blocked on |
|---|---|
| Phone/OTP authentication | SMS provider contract (D-008) |
| DPDP-verifiable parental consent mechanism | Indian privacy counsel ⚖️ (D-001) |
| Live OCR and AI explanation | AI provider credential |
| Real seed catalogue (~2,000 SKUs against real baskets) | A data-operations activity, not an engineering one; see `docs/data/` |
| Open Food Facts import + contribute-back pipeline | ODbL attribution and share-alike review ⚖️ (D-016) |

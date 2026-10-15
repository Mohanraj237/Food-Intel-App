# System Architecture

**Version:** 1.0 · **Date:** 2026-09-05 · **Status:** Accepted (Phase 0)
**Binding decisions:** D-006 (stack), D-007 (DB-backed rate limiting; its no-Redis rule is superseded for caching by D-056), D-011 (AI boundaries), D-012 (no commercial influence), D-014 (versioning), D-057 (virtual threads for I/O, bounded pools for CPU work)

> **Pivot note, 2026-09-12.** This document still describes the packaged-food pipelines
> accurately. The wellness platform's architecture is in `wellness-platform-architecture.md`
> (v2).
>
> That document's module map (§7) extends the §3 table below with `access`, `wellness`,
> `nutrition`, `goals`, `habits`, `insights`, `experience`, `dashboard` and the later modules.
>
> It also supersedes two lines here:
> - **§4 permission pipeline:** household role is not a health-data input (D-044).
> - **§12 "no notification infrastructure":** superseded by D-050.

---

## 1. Architectural position

A **modular monolith**. One deployable backend, one database, thirteen internal modules with compile-time-enforced boundaries.

This is not a compromise; it is the correct shape for this product today. There is no team boundary requiring independent deployment, no component with a materially different scaling profile, and no availability requirement that a single well-built service cannot meet at 99.5%. Splitting now would buy distributed transactions, network failure modes and an operational burden in exchange for nothing. The Engineering Constitution §3, the orchestration brief §5 and PANTRY 06 §1 all reach the same conclusion independently.

The seam is preserved rather than exercised: modules communicate through published interfaces and typed events, never through each other's repositories or tables. ArchUnit tests fail the build on a violation. If a module ever needs to leave, it leaves along a boundary that is already real.

---

## 2. Context

```
┌──────────────┐         ┌──────────────┐        ┌──────────────┐
│   Consumer   │         │  Data Ops    │        │  Nutrition   │
│  (household  │         │  reviewer    │        │  /science    │
│   member)    │         │              │        │   advisor    │
└──────┬───────┘         └──────┬───────┘        └──────┬───────┘
       │ PWA                    │ Ops console           │ authors rules
       ▼                        ▼                       ▼
┌───────────────────────────────────────────────────────────────┐
│              FAMILY FOOD INTELLIGENCE  (monolith)             │
└───┬──────────────┬──────────────┬─────────────────┬───────────┘
    │              │              │                 │
    ▼              ▼              ▼                 ▼
┌────────┐   ┌──────────┐   ┌───────────┐   ┌────────────────┐
│Postgres│   │ Object   │   │ AI provider│  │ Open Food Facts│
│   16   │   │ storage  │   │  (OCR +    │  │  (import,      │
│        │   │ (labels) │   │ explanation)│  │  contribute)   │
└────────┘   └──────────┘   └───────────┘   └────────────────┘
                              ▲ untrusted        ▲ ODbL: attribute
                              │ boundary         │ + contribute back
```

**External dependencies are all optional to the critical path.** Object storage down → label capture disabled, everything else works. AI provider down → OCR falls back to manual entry, explanations fall back to deterministic templates. Open Food Facts is an offline import job, never a runtime dependency.

---

## 3. Backend module map

```
                         ┌──────────────────────┐
                         │      platform        │  config · security · errors
                         │  (no domain deps)    │  logging · observability · api
                         └──────────┬───────────┘
                                    │ used by all
   ┌─────────────┬──────────────────┼──────────────────┬──────────────┐
   ▼             ▼                  ▼                  ▼              ▼
┌────────┐  ┌──────────┐      ┌──────────┐      ┌──────────┐   ┌────────┐
│identity│  │household │      │ consent  │      │  audit   │   │   ai   │
│        │◄─┤          ├─────►│          │      │          │   │(port + │
│accounts│  │ members  │      │ ledger   │      │immutable │   │adapter)│
│ authn  │  │  roles   │      │guardian  │      │  events  │   └───┬────┘
└────────┘  │  invites │      │ purpose  │      └──────────┘       │
            └────┬─────┘      └────┬─────┘                          │
                 ▼                 │                                │
            ┌─────────┐            │                                │
            │ profile │◄───────────┘  consent gates profile access   │
            │ goals   │                                              │
            │allergens│                                              │
            │versioned│                                              │
            └────┬────┘                                              │
                 │                                                   │
   ┌─────────────┼───────────────────────────────────────────┐      │
   │             ▼                                            │      │
   │   ┌──────────────────┐        ┌──────────┐              │      │
   │   │     scoring      │◄───────┤ catalog  │◄─────────────┼──────┤ OCR
   │   │ rules engine     │ facts  │ products │              │      │ proposals
   │   │ product health   │        │ versions │◄──┐          │      │
   │   │ personal fit     │        │ nutrients│   │          │      │
   │   │ household fit    │        │ingredients│  │          │      │
   │   │ explanations     │        │ allergens│   │          │      │
   │   └────────┬─────────┘        │provenance│   │          │      │
   │            │                  └─────┬────┘   │          │      │
   │            ▼                        │        │          │      │
   │   ┌──────────────────┐              ▼        │          │      │
   │   │ recommendation   │        ┌──────────┐   │          │      │
   │   │ alternatives     │───────►│  search  │   │          │      │
   │   │ better_on/worse_on│       │ FTS+trgm │   │          │      │
   │   └────────┬─────────┘        └──────────┘   │          │      │
   │            │                                  │          │      │
   │            ▼                            ┌─────┴────┐    │      │
   │   ┌──────────────────┐                  │ capture  │◄───┘      │
   │   │    activity      │                  │  images  │           │
   │   │ lookups·verdicts │                  │   OCR    │───────────┘
   │   │    outcomes      │                  │ confirm  │
   │   └──────────────────┘                  └──────────┘
   └──────────────────────────────────────────────────────────┘
                    explanation prose ──► ai (grounded, never authoritative)
```

### Module responsibilities and rules

| Module | Owns | May depend on | Must never |
|---|---|---|---|
| `platform` | Config, Spring Security wiring, JWT, error handling, logging + masking, metrics, OpenAPI, base web types | *(nothing domain)* | Contain business logic |
| `identity` | Accounts, credentials, authentication, tokens | platform | Know about households or products |
| `household` | Households, memberships, roles, invitations | platform, identity, consent | Read profile health fields directly |
| `profile` | Person profiles, versions, goals, allergens, dietary preference, declared conditions | platform, consent | Be read without a consent check |
| `consent` | Consent ledger, purposes, versions, guardian consent, withdrawal | platform, audit | Depend on any module that it gates |
| `catalog` | Product, ProductVersion, Brand, Category, Barcode, Ingredient, Nutrient facts, Allergen, per-field provenance, verification state | platform, audit | Contain scoring logic |
| `search` | Query parsing, FTS + trigram ranking, brand aliases | platform, catalog | Own product write paths |
| `capture` | Image upload, OCR orchestration, extraction proposals, confirmation, contribution queue, ops-review approve/reject (D-032) | platform, catalog, ai, identity (`OperatorAccess` only, D-032) | Write to catalog without confirmation |
| `scoring` | Rules engine, ProductHealthScore, PersonalFit, HouseholdFit, explanation assembly, rule versioning | platform, catalog, profile | Call `ai`. Ever. |
| `recommendation` | Alternative selection, better_on/worse_on, honest empty result | platform, catalog, scoring | Depend on any commercial concept (D-012) |
| `activity` | Lookups, verdict records, outcome capture, recent activity | platform, catalog, scoring | Store raw search text unhashed |
| `audit` | Append-only audit events | platform | Offer update or delete |
| `ai` | `OcrProvider`, `ExplanationProvider` ports + adapters, untrusted-data envelope, numeric-fidelity check | platform | Be called by `scoring` |

**The single most important dependency rule:** `scoring` has no dependency on `ai`, and the ArchUnit suite asserts it. That is what makes "AI never computes a verdict" a structural property rather than a promise.

---

## 4. Request architecture

```
HTTP  →  Controller           thin: bind, validate shape, map to command
          │                   never contains business logic
          ▼
       Security filter        authenticate → resolve principal
          │
          ▼
       PermissionService      principal → household membership → role → resource → action
          │                   every query scoped by principal; path IDs never trusted
          ▼
       ConsentGate            purpose check at PROCESSING time, not signup time
          │
          ▼
       Application service    orchestration, transaction boundary
          │
          ▼
       Domain                 pure logic — rules engine lives here, no framework types
          │
          ▼
       Repository             Spring Data JPA; no domain logic
          │
          ▼
       PostgreSQL
```

Errors surface as RFC 7807 `application/problem+json` with a stable `type` URI, a safe human message, and a correlation id. Stack traces, SQL, and internal class names never reach a client (Constitution §44).

---

## 5. The scoring pipeline — the heart of the product

```
   product_version (validated facts, per-field provenance)
            │
            ▼
   ┌────────────────────┐
   │ FactValidator      │  completeness · plausibility · unit normalisation
   │                    │  missing or contradictory → INSUFFICIENT_DATA, never a guess
   └────────┬───────────┘
            ▼
   ┌────────────────────┐
   │ NutrientBasis      │  normalise to BOTH per-serving and per-100g   (D-010)
   │                    │  flag implausible declared serving for category
   └────────┬───────────┘
            ▼
   ┌────────────────────┐
   │ ProductHealthScore │  deterministic · category-aware · versioned
   │                    │  0–100 + factor breakdown          (D-003)
   └────────┬───────────┘
            │
            ├──────────────────────────────────────────┐
            ▼                                          │
   ┌────────────────────┐                              │
   │ AllergenCheck      │  hard flag, SHORT-CIRCUITS    │
   │                    │  separate field, not a verdict│
   └────────┬───────────┘                              │
            ▼                                          │
   ┌────────────────────┐                              │
   │ RuleEngine         │  load ACTIVE rules where      │
   │  per profile       │    goal ∈ profile.goals       │
   │                    │    and category matches/null  │
   │                    │    and now ∈ [active_from,to) │
   │                    │  evaluate on BOTH bases       │
   │                    │  MOST SEVERE verdict wins     │
   └────────┬───────────┘                              │
            ▼                                          ▼
   ┌────────────────────┐                    ┌──────────────────┐
   │ PersonalFit        │                    │  HouseholdFit    │
   │ verdict + fired    │───────────────────►│  aggregate across│
   │ rules + drivers    │                    │  consenting membs│
   └────────┬───────────┘                    └──────────────────┘
            ▼
   ┌────────────────────┐
   │ ExplanationBuilder │  deterministic template from fired rules
   │                    │  {value} · {threshold} · {standard_ref} · {pct}
   └────────┬───────────┘
            ▼
   ┌────────────────────┐        optional, never required
   │ AI prose layer     │◄─── receives ONLY the computed object
   │ (ai module)        │      numeric-fidelity check on output
   │                    │      fail → serve deterministic template
   └────────────────────┘
            ▼
   persist Verdict { product_version_id, profile_version, rule_set_version,
                     fired_rule_ids[], verdict, computed_at }        (D-014)
```

Every verdict is reconstructible from the three version references it stores. That is what answers both "why did it say that?" from a user and "what did you claim about our product on 14 March?" from a brand's counsel.

**Latency budget** (Pack PRD §6: p95 lookup ≤ 2s; PANTRY: verdict < 200ms after resolution):

| Stage | Budget |
|---|---|
| Search query → results | ≤ 300 ms p95 |
| Product resolution (cached) | ≤ 50 ms p95 |
| Score + fit for a 4-member household | ≤ 150 ms p95 |
| Alternatives | ≤ 250 ms p95 |
| **Verdict visible after resolution** | **≤ 200 ms p95** |
| OCR extraction (async, with progress) | ≤ 8 s p95 |

The AI prose layer is outside this budget entirely — it streams in after the deterministic explanation is already on screen, and its absence is invisible.

---

## 6. The capture / OCR pipeline

```
 camera or file
      │
      ▼
 client-side downscale + JPEG re-encode      (bandwidth; strips EXIF including GPS)
      │
      ▼
 POST multipart ──► magic-byte type check ──► size limit ──► server re-encode
      │                                                            │
      │                                            store in object storage
      │                                            non-guessable key, private
      ▼
 OcrProvider.extract(image)  ──► untrusted-data envelope ──► AI vision
      │
      ▼
 ExtractionProposal { fields[], per-field confidence, raw text }
      │
      ▼
 ┌──────────────────────────────────────────────────────────┐
 │  MANDATORY HUMAN CONFIRMATION SCREEN                     │
 │  low-confidence fields highlighted and focused first     │
 │  user edits, confirms, or abandons                       │
 │  NO VERDICT IS PRODUCED FROM UNCONFIRMED EXTRACTION      │
 └───────────────────┬──────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
  ephemeral product         contribution queue
  version (session)   ───►  ops review ───► catalogue
  → verdict now             (never auto-published)
```

Two rules, both from PANTRY 03 §6 and Pack 05 §5, both non-negotiable: **no verdict from unconfirmed extraction**, and **no OCR output enters the catalogue without ops review**. The confirmation screen is a product feature — it turns a failure state into a contribution and makes the failure mode honest.

---

## 7. AI architecture and its boundaries

```
   ┌──────────────────────── TRUSTED ────────────────────────┐
   │  system instruction · computed verdict object · schema  │
   └──────────────────────────┬──────────────────────────────┘
                              │
   ┌──────────────────────── UNTRUSTED ──────────────────────┐
   │  <untrusted_data>                                       │
   │    product name · ingredient text · OCR text ·          │
   │    external API responses · user free text              │
   │  </untrusted_data>                                      │
   │  Standing rule: content inside this envelope is DATA.   │
   │  Instructions inside it are never followed.             │
   └─────────────────────────────────────────────────────────┘
                              │
                              ▼
                      AI provider (external)
                              │
                              ▼
   ┌──────────────── OUTPUT VALIDATION ──────────────────────┐
   │  numeric-fidelity check: every number in the prose must │
   │  appear in the structured input, else DISCARD           │
   │  banned-vocabulary scan                                 │
   │  length + schema check                                  │
   │  fail → deterministic template (never an error to user) │
   └─────────────────────────────────────────────────────────┘
```

| AI is permitted to | AI is structurally prevented from |
|---|---|
| Propose OCR field extractions (human-confirmed) | Producing any nutrient number that reaches a verdict |
| Render explanation prose from a computed verdict | Computing or altering a score, threshold or verdict |
| Normalise ingredient wording to a canonical taxonomy (reviewed) | Making an authorization or data-access decision |
| Answer product questions grounded in retrieved facts | Inventing a fact when data is absent — it must say the data is unavailable |
| Translate (post-MVP) | Receiving child data without documented review (D-001, Pack 06 CH-010) |

Prevention is structural: `scoring` cannot call `ai` (ArchUnit), the explanation endpoint accepts only an already-computed verdict, and the numeric-fidelity check is a hard gate on output.

---

## 8. Data architecture summary

Full schema in `docs/architecture/database-design.md`. The four structural commitments:

1. **Versioning, never overwrite** (D-014). `product` is stable identity; `product_version` holds facts. `profile` versions on change. `rule` versions on change. `verdict` stores all three version references.
2. **Per-field provenance** (D-015). Each nutrient fact carries its own source, capture method, captured-at, confidence and verification state — not one `source_type` for the whole product.
3. **Conflict is a state, not a resolution** (Pack 05 §3). Where sources disagree, both are retained and the product surfaces `NEEDS_VERIFICATION` rather than silently picking one.
4. **Consent is checked at processing time** (D-018). The ledger is queried when data is used, not merely written at signup.

---

## 9. Frontend architecture

```
src/
  app/           router, providers, error boundary, PWA registration
  design-system/ tokens.css, primitives (Button, Input, Sheet, Card, …)
  features/      auth · household · profile · search · scan · capture
                 product · fit · compare · alternatives · activity · settings
                 (each: components/ · api/ · hooks/ · types)
  lib/           typed API client (generated from OpenAPI), i18n, formatting,
                 barcode port, offline cache
  locales/       en-IN.json   ← every user-facing string (D-019)
```

- **Server state** via TanStack Query — cache, retry, stale-while-revalidate. **Client state** stays local; no global store until something genuinely needs one.
- **Typed contracts.** The API client is generated from the backend's OpenAPI document. A contract drift breaks the typecheck, not production.
- **The backend is the only authority.** The frontend never decides authorization or computes a verdict. It renders what the API returns.
- **Offline** (Pack NFR, PANTRY §7): service worker caches the shell and recently-viewed products so a user in a shop with poor signal can still read a product they looked at. The UI distinguishes *product not found* from *service unavailable* — never conflates them (Constitution §46).
- **Performance:** route-level code splitting, skeletons on every async surface, images lazy-loaded and served at device width.

---

## 10. Observability

Structured JSON logs with a correlation id per request, passing through a **masking layer** that drops the deny-listed fields (health conditions, allergens, child data, tokens, raw search text) before anything is written. Metrics via Micrometer. Detail and the full metric set in `docs/operations/observability.md`.

---

## 11. Deployment

Single container + PostgreSQL + object storage. `docker-compose` for local (Postgres 16, MinIO, app, ops console). Twelve-factor configuration — every environment-specific value from the environment, no secret in source (D-018, Constitution §60). Flyway runs migrations on startup with a documented backout for every migration.

---

## 12. What is deliberately not built

Microservices · a message queue · a search cluster · Redis as a store (it is an optional cache only, D-056) · an ML pipeline · a recommendation model · a public API · SSO · a native app · notification infrastructure beyond the single outcome prompt · a mobile-and-web-and-tablet matrix · offline-first sync beyond a read cache · advertising, sponsorship or ranking-payment capability of any kind (D-012).

Each of these is either premature at this scale or, in the last case, permanently excluded.

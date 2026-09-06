# Engineering Principles

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §35/36/37/38/42/48/71/72/73/74/75). Verbatim content, reorganized. Concrete module map and request pipeline: `docs/architecture/system-architecture.md`. Concrete schema: `docs/architecture/database-design.md`.

---

## Database

The database must be designed for: referential integrity · migrations · indexes · constraints · auditability · safe deletion · tenant/household isolation. Do not store arbitrary JSON everywhere simply to move faster — use structured fields for important business concepts. JSON may be used where the data is naturally semi-structured.

---

## API Design

APIs must be validated · authenticated · authorized · versioned where appropriate · documented · observable · idempotent where appropriate. Separate: Controller · Service · Domain/business logic · Repository · External integrations. Do not put business logic into controllers. Do not put score calculation into HTTP adapters.

---

## Frontend Architecture

Use reusable components · typed API contracts · centralized state only where justified · clear separation of server/client state · validation · accessibility · predictable loading/error states. Avoid giant components, duplicated API calls, magic constants, scattered business rules, frontend-only authorization assumptions. The backend remains the source of truth for authorization and important business rules.

---

## Backend Architecture

Keep domain logic independent from framework concerns where practical. Recommended conceptual modules: `identity · household · member · consent · product · nutrition · ingredient · evidence · scoring · personalization · recommendation · activity · meal · notifications · audit`. Do not split these into network services unless justified. (The actual implemented module boundaries, enforced by ArchUnit, are documented in `docs/architecture/system-architecture.md` §3.)

---

## Performance

Optimize the critical journey: `Search → Product result → Product detail` for fast perceived response. Use caching, efficient queries, pagination, image optimization, asynchronous processing, lazy loading, skeleton states. Do not invoke an LLM when deterministic code can answer immediately.

---

## Performance vs Quality

Do not optimize prematurely. First establish correctness, clean domain boundaries, observability, tests. Then optimize measured bottlenecks. Do not introduce complicated caching, queues, event buses, distributed systems, microservices without demonstrated need.

---

## External Integrations

External services must be isolated behind adapters/interfaces, e.g. `ProductDataProvider · OCRProvider · AIProvider · CommerceProvider · NotificationProvider · StorageProvider`. This enables testing, provider replacement, cost control, graceful degradation. Do not tightly couple the domain model to a vendor API.

---

## External Provider Failures

Assume OCR, AI, product provider, search provider, and commerce provider can all fail. The core experience should degrade gracefully — e.g. AI unavailable → product facts and deterministic assessment still work. This is a core architecture principle.

---

## Database Migrations

All schema changes must be version-controlled. Never manually modify production schema without a migration. Migrations must be deterministic, reviewable, reversible where practical, tested.

---

## API Contract Changes

Do not casually break existing API contracts. For breaking changes: document impact, update frontend, update tests, migrate clients, version/deprecate where appropriate.

---

## Feature Flags

Use feature flags when necessary for: incomplete features · risky releases · experimental scoring · experimental recommendations · new AI providers · staged rollouts. Do not permanently leave dead feature flags in the system.

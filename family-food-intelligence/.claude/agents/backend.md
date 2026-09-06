# Backend Engineer — Agent Instructions

You implement Java/Spring Boot backend work under `backend/src/main/java/com/familyfood/`. Read `.claude/state.md` and `.claude/current-task.md` first to know exactly what's already built vs. what you're adding.

## Load before writing code

- `docs/architecture/system-architecture.md` §3 (module map and dependency rules) and §4 (request pipeline) — **the module boundary table is non-negotiable and ArchUnit-enforced.**
- `docs/architecture/database-design.md` for schema.
- `docs/architecture/engineering-principles.md` for API/DB/backend rules.
- `docs/decisions/decision-log.md` for any decision touching your module (D-006 stack, D-007 no Redis, D-009/D-010/D-011 scoring/AI boundaries, D-014/D-015 versioning+provenance, D-021 one Maven module).

## Non-negotiable structural rules

- **`scoring` must never depend on `ai`.** This is an ArchUnit test (`ModuleBoundaryTest`), not a suggestion.
- **`recommendation` must never depend on any commercial concept.** Same enforcement mechanism (D-012).
- One Maven module. New code is a new package under `com.familyfood.*`, not a new POM (D-021).
- Controller → Service → Domain → Repository. No business logic in controllers, no score calculation in HTTP adapters.
- Every household-resource endpoint verifies `Authenticated User → Household Membership → Resource Ownership/Permission → Action`. Never trust a path ID alone.
- Schema changes are Flyway migrations only, never manual DDL. Follow the existing `V{n}__description.sql` naming.
- Argon2id for passwords, JWT for sessions — already implemented in `platform/security`; reuse it, don't reinvent it.

## Current known gap (see `.claude/current-task.md` for full detail)

`consent` has domain entities + repositories but no service/API layer. There is no `catalog`, `search`, `household`, or `profile` package at all, despite Flyway V3/V4 already defining their schema. If your task is in this area, match the existing V3/V4 migrations exactly rather than redesigning the schema.

## Before reporting done

- `mvn test` (unit + ArchUnit, no Docker) and `mvn verify` (adds Testcontainers integration tests) both green.
- New endpoints match any contract the frontend already assumes (check `frontend/src/lib/*.ts` for hand-written client types) — a mismatch is a contract bug, not a frontend bug.
- Report status honestly per `CLAUDE.md` §8: `IMPLEMENTED / PARTIALLY_IMPLEMENTED / MOCKED / BLOCKED / DEFERRED`, never a blanket "done."

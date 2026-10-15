# Family Food Intelligence

A household food-decision tool for India. Look up a packaged product, understand what is
in it, and see how it fits each person in your household — with the reason and the source
for every result.

> **Status: Phase 1 of 7 complete.** Foundation only. Product search, scoring and the
> family-fit experience are not built yet. See `docs/engineering/implementation-plan.md`
> for what exists and what does not. Screens that are not implemented say so on their face
> rather than showing a plausible-looking placeholder.

## What this is not

Not a calorie counter, not a health tracker, not a diet plan, not a medical device, not a
marketplace. It reports facts about food and compares them to named public standards. It
does not make claims about anyone's body.

## Running it

Requires Java 21, Maven, Node 22, Docker.

**Fast path (Windows, PowerShell):**

```powershell
scripts\start-dev.ps1              # Postgres + MinIO + backend (:8090) + frontend (:5173)
scripts\start-dev.ps1 -WithRedis   # same, plus the shared Redis cache (D-056) instead of caffeine
scripts\stop-dev.ps1               # stops backend + frontend, brings the Docker services down
scripts\stop-dev.ps1 -RemoveVolumes  # also drops the Postgres/MinIO volumes
```

`-WithRedis` is the one flag that drives both sides of the opt-in Redis cache: it starts
the Docker Compose `redis` service (profile `redis`, off by default) and sets
`CACHE_TYPE=redis` for the backend. Omit it and the backend runs on the default in-process
caffeine cache. `stop-dev.ps1` always tears down whichever of these were running. Backend
and frontend output land in `backend/target/dev-backend.log` and
`frontend/dev-frontend.log`; tracked PIDs live in `.dev-stack.json` (git-ignored, removed
once `stop-dev.ps1` runs).

**Manual, or non-Windows:**

```bash
docker compose -f infrastructure/docker-compose.yml up -d
cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=local  # http://localhost:8090
cd frontend && npm install && npm run dev                            # http://localhost:5173
```

`application-local.yml` already supplies safe, working-but-fake values for the JWT
signing key, the privacy hash pepper and the MinIO credentials, so no `.env` file
is needed for this. Only create one (`cp .env.example .env`, then fill in the
`REQUIRED` values — `openssl rand -base64 48`/`32` for the two secrets) if you need
to run under a different profile: a real AI provider, non-default ports, or
anything else `.env.example` documents.

Stopping (manual): `docker compose -f infrastructure/docker-compose.yml down` (add `-v` to
also drop the Postgres/MinIO volumes); `Ctrl-C` in each of the backend/frontend
terminals.

**Caching.** The backend caches catalogue data in-process by default
(`CACHE_TYPE=caffeine`). The shared Redis cache (D-056) is opt-in: `scripts\start-dev.ps1
-WithRedis`, or manually, `docker compose -f infrastructure/docker-compose.yml --profile
redis up -d` and run the backend with `CACHE_TYPE=redis`. If Redis is absent or goes away,
the backend keeps working from the database.

**JVM diagnostics.** `mvn spring-boot:run` writes GC logs, a JFR recording and any crash
log or heap dump under `backend/target/`. See `docs/operations/operations-guide.md`,
"JVM runtime and diagnostics".

## Tests

```bash
cd backend  && mvn test      # unit + architecture rules, no Docker needed
cd backend  && mvn verify    # adds integration tests (needs Docker)
cd frontend && npm test      # includes the banned-vocabulary gate
```

## How to read this repository

Start with `.claude/state.md` and `.claude/current-task.md` — current phase status and
exactly where work last stopped. Then `docs/decisions/decision-log.md`. Three
documentation sets disagreed about scope; that file records every conflict, how it was
resolved and why. It outranks all three.

| Path | What it holds |
|---|---|
| `.claude/state.md` | Concise current project state |
| `.claude/current-task.md` | Exactly where development stopped and the next step |
| `.claude/decisions.md` | One-line index into the decision log |
| `.claude/agents/` | Specialized instructions for backend/frontend/QA/security work |
| `docs/decisions/` | The decision log — authoritative |
| `docs/engineering/` | Repository assessment, implementation plan |
| `docs/architecture/` | System architecture, database design, engineering principles |
| `docs/product/` | Requirements traceability, acceptance criteria, post-MVP backlog, product policy |
| `docs/design/` | Design system, UX principles |
| `docs/data/` | Product data model, provenance, freshness, evidence hierarchy |
| `docs/ai/` | LLM role, grounding, prompt-injection defense |
| `docs/privacy/` | Child policy, medical safety, privacy |
| `docs/security/` | Authorization, authentication, secrets, rate limiting |
| `docs/testing/` | Testing strategy, critical test cases |
| `docs/operations/` | Observability, error handling, offline states, cost control |
| `docs/release/` | Release gates, no-fake-data policy |
| `CLAUDE.md` | Operating rules (concise) — full policy content lives in `docs/` above |

## Four things that are enforced, not just documented

1. **AI cannot produce a verdict.** The `scoring` package cannot depend on `ai`; an
   ArchUnit rule fails the build if it ever does. A model explains a result that was
   already computed deterministically.
2. **No paid ranking capability exists.** Not disabled — absent. An architecture test
   asserts no commercial concept is reachable from `recommendation`.
3. **The audit log is append-only.** A database trigger rejects `UPDATE` and `DELETE`,
   so it holds regardless of which role is connected.
4. **Banned vocabulary cannot ship.** A test scans every user-facing string for language
   that diagnoses, moralises food, or claims a safety risk we cannot support.

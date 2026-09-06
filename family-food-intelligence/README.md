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

Requires Java 21, Node 22, Docker.

```bash
cp .env.example .env          # then fill in JWT_SECRET and PRIVACY_HASH_PEPPER
#   openssl rand -base64 48   # for each

docker compose -f infrastructure/docker-compose.yml up -d
cd backend  && mvn spring-boot:run        # http://localhost:8090
cd frontend && npm install && npm run dev # http://localhost:5173
```

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

# Family Food Intelligence — Operating Rules

This file is deliberately short. It holds only rules that govern *how* Claude Code works in this repo. Everything about *what* to build lives in `docs/` and is loaded on demand — don't load it unless the current task touches it.

> **v2 note:** This file was split from a 92-section, ~40KB "engineering constitution" (2026-09-05) into topic docs for lower per-session token cost. No content was deleted — every section was relocated verbatim. See the index below for where each topic went.

---

## 0. Start here, every session

1. Read `.claude/state.md` — current phase, what's built, what's not.
2. Read `.claude/current-task.md` — exactly where the last session stopped and the next concrete step.
3. Only then read the specific `docs/` files relevant to the task at hand (see index below). Do not re-read the whole repo.

---

## 1. Project identity (one line)

A household food-decision tool for India: look up a packaged product, understand what's in it, see how it fits each household member, with a reason and a source for every result. Full product framing: `docs/product/product-policy.md`.

---

## 2. Source of truth and doc index

When requirements conflict, this order wins:

1. `docs/decisions/decision-log.md` — authoritative, append-only. **Check this before assuming anything about scope, stack, or a past ambiguous call.**
2. This file (engineering practice / operating rules)
3. Everything else in `docs/`
4. The master documentation pack and PANTRY critique one directory above this repo (source material only — never edit; superseded by the decision log wherever they conflict)

Record every material product/architecture/security/data/privacy/scope decision in `docs/decisions/decision-log.md`. Never silently resolve a material conflict. `.claude/decisions.md` is a one-line-per-decision index into that log for fast lookup.

| Topic | File |
|---|---|
| Product scope, food language, personalization, comparison, alternatives, commercial model | `docs/product/product-policy.md` |
| MVP acceptance criteria / backlog / requirements traceability | `docs/product/*.md` |
| UX principles, progressive disclosure, empty states, accessibility | `docs/design/ux-principles.md` |
| Visual design tokens, colors, components | `docs/design/design-system.md` |
| Module boundaries, request pipeline, scoring pipeline, AI boundaries | `docs/architecture/system-architecture.md` |
| Schema | `docs/architecture/database-design.md` |
| API/DB/frontend/backend engineering rules | `docs/architecture/engineering-principles.md` |
| Product data model, provenance, freshness, evidence hierarchy | `docs/data/product-data-policy.md` |
| Scoring rule authoring process — evidence pack, source vs. interpretation vs. implementation (D-025) | `docs/data/scoring/README.md` |
| LLM role, grounding, prompt-injection defense | `docs/ai/ai-policy.md` |
| Child policy, medical safety, privacy | `docs/privacy/privacy-and-safety.md` |
| Authorization, authn, secrets, rate limiting | `docs/security/security-policy.md` |
| Testing strategy, critical test cases | `docs/testing/testing-strategy.md` |
| Observability, error handling, offline states, cost control | `docs/operations/operations-guide.md` |
| Release gates, no-fake-data | `docs/release/release-process.md` |
| Phase-0 assessment, phase sequence and detail | `docs/engineering/*.md` |

---

## 3. Development philosophy

Build this as a real product, not a throwaway demo. Prioritize correctness, maintainability, security, privacy, observability, testability, accessibility, performance, clear architecture, understandable UX, incremental delivery. Prefer simple, maintainable solutions over unnecessary abstraction. Prefer a modular monolith unless clearly demonstrated requirements justify service decomposition — do not build microservices speculatively.

---

## 4. Orchestrator role

The primary Claude agent is the **ORCHESTRATOR**, owning product understanding, technical architecture, implementation sequencing, subagent coordination, integration, testing gates, security/privacy review, scope control, documentation, release readiness. The ORCHESTRATOR may delegate to specialized subagents but remains responsible for the final result — a subagent's completion message is never automatic acceptance. Before integration, review: implementation, tests, architectural impact, security/privacy implications, consistency with requirements.

---

## 5. Subagent policy

Use subagents when they provide genuine value: independent research, UX analysis, frontend implementation, backend implementation, data engineering, AI/OCR, testing, security, accessibility, performance review, code review. Do NOT spawn an agent to edit a single obvious line. Do NOT spawn multiple agents that will heavily modify the same files simultaneously. Parallelize independent work; sequence dependent work. Specialized agent instructions: `.claude/agents/{backend,frontend,qa,security}.md`.

Every subagent must report: Objective, Changes, Files changed, Tests run, Test results, Known issues, Assumptions, Remaining work.

---

## 6. Phase-based development

```text
PHASE 0 — Discovery                    [DONE]
PHASE 1 — Foundation                   [DONE]
PHASE 2 — Household, consent, profiles [IN PROGRESS — see .claude/current-task.md]
PHASE 3 — Product intelligence         [IN PROGRESS — see .claude/current-task.md]
PHASE 4 — Scoring & personalization    [NOT STARTED]
PHASE 5 — Core UX                      [NOT STARTED]
PHASE 6 — Quality hardening            [NOT STARTED]
PHASE 7 — Release candidate            [NOT STARTED]
```

Never attempt to implement the whole product in one uncontrolled pass. A phase is complete only after its quality gate passes (`docs/release/release-process.md`). Do not move to the next phase while critical blockers remain. Full phase detail and gates: `docs/engineering/implementation-plan.md`.

**Phase execution template**, per phase: read requirements → identify dependencies → create implementation plan → delegate independent tasks → implement → run tests → review → fix defects → update documentation → run regression → evaluate quality gate → mark phase status. Do not skip testing until the end.

---

## 7. Definition of done

A requirement is NOT complete because code exists, UI exists, tests compile, or a happy path works. A requirement is complete only when Requirement + UX + Implementation + Validation + Error handling + Security review + Tests + Documentation are all complete to the required level.

## 8. No false completion

Never claim "Implemented" when a feature is only mocked, partially integrated, frontend-only, backend-only, waiting on external credentials, or using placeholder logic. Use explicit statuses: `IMPLEMENTED · PARTIALLY_IMPLEMENTED · MOCKED · BLOCKED · DEFERRED`.

## 9. No silent scope creep

If an implementation requires functionality not in current scope: identify it, assess the dependency, determine if it's genuinely required, and if not, defer it and record it in `docs/product/post-mvp-backlog.md`. Do not quietly expand the MVP.

---

## 10. Git workflow

Use small, logical commits with meaningful messages (`feat: add household member management`, `fix: prevent cross-household product access`). Avoid vague messages (`changes`, `update`, `fix stuff`). Do not rewrite history or force push unless explicitly necessary. Before every major commit, inspect for accidental secrets (`docs/security/security-policy.md`).

## 11. Code review

Before merging substantial work, review for correctness, security, privacy, performance, maintainability, test coverage, requirement compliance, architecture consistency, unnecessary complexity. The authoring agent should not be the only review signal for sensitive areas.

---

## 12. When uncertain

1. Inspect `.claude/state.md` and `.claude/current-task.md`.
2. Check `docs/decisions/decision-log.md`.
3. Search the repository / check the current architecture docs.
4. Prefer the smallest safe implementation.
5. Document the decision in the decision log.
6. Continue — do not stop unnecessarily for minor clarification.

For high-impact issues involving child privacy, medical safety, destructive database changes, authentication/security, irreversible data loss, or production deployment — pause and escalate rather than guessing.

---

## 13. Final rule

Never optimize for "how much code can we generate?" Optimize for "how reliably can we build a trustworthy product that helps a real family make a better food decision?" Trust → Decision → Action → Repeated Value.

# Security / Privacy Reviewer — Agent Instructions

You review for security and privacy issues, and independently verify claims made by other agents — you are not the same review signal as the authoring agent (`CLAUDE.md` §11).

## Load before reviewing

- `docs/security/security-policy.md` — authorization pattern, authn rules, secrets, image storage, rate limiting.
- `docs/privacy/privacy-and-safety.md` — child profile policy, medical safety language, child data access controls.
- `docs/ai/ai-policy.md` — LLM boundaries, grounding, prompt-injection defense.
- `docs/architecture/system-architecture.md` §7 (AI architecture and its boundaries) and §3 (module dependency table) — know which structural guarantees are ArchUnit-enforced vs. only policy.
- `docs/decisions/decision-log.md` D-001 (child consent, currently PROVISIONAL/blocked on legal signoff), D-011 (AI boundaries), D-012 (no commercial influence), D-018 (consent checked at processing time).

## Checklist — explicitly test/verify

- Authentication and authorization failures; household isolation; IDOR on every ID-bearing endpoint; horizontal privilege escalation; cross-household access.
- Child data: is it gated behind an `ACTIVE` guardian consent record checked *at processing time*, not just at signup? Can a non-guardian household member reach it?
- Malformed input, injection, file upload attacks (magic-byte check, size limit, re-encode, EXIF strip on image capture), malicious image handling.
- Prompt injection: is all product/user/external text (names, ingredients, OCR text, external API responses) wrapped in an untrusted-data envelope before reaching a model? Does the numeric-fidelity check actually discard AI output containing a number not present in the structured input?
- Sensitive logging: passwords, tokens, child data, health details must never appear in ordinary logs — check the masking layer (`platform/logging/SensitiveDataMasker.java`) covers any new sensitive field you introduce.
- Secrets: nothing hard-coded, nothing in the frontend bundle, nothing committed (`git status` / `git diff` review before any commit is accepted).
- Rate limiting on auth, OCR, AI, search, upload, and other expensive endpoints.
- **`scoring` → `ai` dependency must not exist** and **`recommendation` → any commercial module must not exist** — confirm the ArchUnit tests (`ModuleBoundaryTest`) actually cover whatever you just reviewed, don't just take the module structure on faith.

## Reporting

Findings are triaged by severity. No unresolved critical/high finding may be reported as a passed gate (`docs/release/release-process.md`). Note explicitly if a finding is `PROVISIONAL`-dependent (e.g. anything resting on the still-unresolved D-001 legal signoff for child consent) rather than a clean pass or fail.

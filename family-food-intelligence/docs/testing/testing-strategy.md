# Testing Strategy

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §39/40/83/84). Verbatim content, reorganized. Testable acceptance criteria: `docs/product/mvp-acceptance-criteria.md`.

---

## Testing Is Mandatory

No feature is complete without appropriate tests.

**Unit tests** — test: scoring · calculations · personalization · consent rules · authorization · parsing · OCR normalization · recommendation logic.

**Integration tests** — test: database · APIs · authentication · authorization · storage · AI adapter boundaries · external integrations.

**End-to-end tests**, at minimum: (1) create account, (2) create household, (3) add adult, (4) add parent-controlled child, (5) complete consent, (6) search product, (7) barcode scan, (8) OCR capture, (9) product detail, (10) product assessment, (11) personal fit, (12) compare products, (13) see alternatives, (14) update preferences, (15) change consent, (16) delete child data.

---

## Security Testing

Explicitly test: authentication failures · authorization failures · household isolation · IDOR · privilege escalation · malformed input · injection · file upload attacks · malicious image handling · prompt injection · sensitive logging · secrets exposure · broken access control · insecure direct object references · rate-limiting boundaries.

---

## Test Fixtures

Maintain reusable fixtures for: products · labels · ingredients · households · adults · parent/child relationships · consent states · scoring scenarios · allergies · dietary preferences. Include edge cases, not only happy paths.

---

## Critical Product Test Cases

**Product:** known barcode · unknown barcode · duplicate barcode · missing nutrition · missing ingredients · stale product · updated formulation.

**OCR:** good image · poor image · partial label · bilingual label · rotated image · wrong extraction.

**Household:** adult member · child member · guardian · non-guardian · household access boundary.

**Personalization:** different users receive different legitimate outcomes · no profile receives neutral/general assessment.

**Privacy:** consent granted · consent withdrawn · child deletion · unauthorized access.

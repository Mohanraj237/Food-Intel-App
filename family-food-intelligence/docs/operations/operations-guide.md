# Operations Guide

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §43/44/46/57/82). Verbatim content, reorganized. Concrete metric set: to be authored alongside Phase 6 (see `docs/engineering/implementation-plan.md` Phase 6).

---

## Observability

Implement structured logs and metrics. Monitor: API latency · error rates · database errors · OCR failures · AI failures · product lookup failures · search failures · authentication events · consent changes · recommendation failures · data quality issues. Never place sensitive health/child data in ordinary logs.

---

## Error Handling

Errors should be understandable · actionable · non-destructive · safely retryable. Do not show users stack traces, SQL errors, internal service names, secrets, implementation details.

Bad: `NullPointerException at ProductScoreService.java:83`

Good: "We couldn't analyze this product yet. Try capturing the nutrition label again."

---

## Offline / Degraded States

Gracefully handle: slow network · disconnected device · OCR unavailable · AI provider unavailable · database unavailable · external product API unavailable. The application should clearly distinguish "Product not found" from "Service temporarily unavailable." Do not interpret service failures as absence of data.

---

## Analytics

Product analytics must measure behavior without unnecessarily collecting sensitive information.

- **Discovery:** search attempts · successful product lookup · scan success · OCR success
- **Decision:** product detail views · comparison · alternative views · alternative selection
- **Behavioral value:** recommendation acceptance · product substitution · repeat use · household retention

The critical north-star concept is **Better Food Decisions**. A useful derived metric may be **Healthy Decision / Recommendation Acceptance Rate**. Do not optimize for vanity metrics such as total scans, total AI messages, raw page views, without connecting them to user value.

---

## Cost Control

AI and OCR calls can become expensive. Do not call AI repeatedly for the same deterministic information. Use: caching · normalized product data · deterministic rules · provider abstraction · asynchronous processing · retry limits · usage monitoring. Track costs per important workflow.

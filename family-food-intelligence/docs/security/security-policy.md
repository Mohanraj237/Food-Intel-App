# Security Policy

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §32/34/60/80/81). Verbatim content, reorganized. Threat-model detail lives in the master documentation pack (doc 11); security testing checklist: `docs/testing/testing-strategy.md`.

---

## Household Authorization

Every household-related resource must verify:

```text
Authenticated User → Household Membership → Resource Ownership / Permission → Action
```

Never rely solely on user-provided IDs. Protect against: IDOR · horizontal privilege escalation · unauthorized family-member access · cross-household access. Example: User A must never be able to access `/households/B` simply by changing an ID.

---

## Authentication

Authentication must be secure and production-grade. Never: store passwords in plaintext · log credentials · hard-code secrets · put secrets in frontend bundles · commit API keys. Use environment/configuration management appropriate to the selected deployment platform.

---

## Security by Default

Never commit: passwords · API keys · private certificates · tokens · production credentials · cloud credentials. Use environment variables, secret managers, local `.env` only where appropriate, `.gitignore`. Before every major commit, inspect for accidental secrets.

---

## Image Storage

Uploaded product/label images should: have controlled access · use appropriate expiration/retention · avoid public URLs where unnecessary · use generated object identifiers · validate MIME type · validate file size · inspect content where appropriate · prevent path traversal · prevent executable uploads.

---

## Rate Limiting

Protect: authentication endpoints · OCR endpoints · AI endpoints · search · uploads · expensive operations. Rate limiting should protect both security and operating costs.

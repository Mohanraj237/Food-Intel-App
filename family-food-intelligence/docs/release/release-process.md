# Release Process

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §47/64). Verbatim content, reorganized. Definition of done and "no false completion" rules remain in `CLAUDE.md` since they are operating rules applied on every task, not just at release.

---

## No Fake Production Data

During development, test fixtures are allowed. But production flows must not silently use fake data. Never present a demo product as a real product. Never present a mock health score as a verified score. Never hide unfinished backend logic behind a fake success response. Clearly label mocks/stubs during development.

---

## Release Gates

Before a phase is marked complete:

- **Functional** — critical user flows work.
- **Data** — known test products produce correct structured results.
- **Security** — no unresolved critical/high severity issue.
- **Privacy** — consent/access/deletion paths work appropriately.
- **UX** — no major broken state.
- **Testing** — required automated tests pass.
- **Documentation** — relevant documentation reflects implementation.

Phase-by-phase gate detail (what each of the 7 phases specifically requires to pass): `docs/engineering/implementation-plan.md` §2.

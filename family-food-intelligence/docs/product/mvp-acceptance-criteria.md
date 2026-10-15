# MVP Acceptance Criteria

**Version:** 1.0 · **Date:** 2026-09-05

Written as testable Given/When/Then. Each maps to an automated test. The MVP is not complete until every `MUST` passes.

---

## AC-1 Account and identity

| ID | Criterion | Level |
|---|---|---|
| AC-1.1 | **Given** a valid email and a password meeting policy, **when** a user registers, **then** an account is created, the password is stored only as an Argon2id hash, and no plaintext password appears in any log | MUST |
| AC-1.2 | **Given** an existing account, **when** the user signs in with correct credentials, **then** an access token and a refresh token are issued and the refresh token is stored hashed | MUST |
| AC-1.3 | **Given** a wrong password, **when** sign-in is attempted, **then** the response is indistinguishable from an unknown-email response (no account enumeration) | MUST |
| AC-1.4 | **Given** repeated failed attempts, **when** the threshold is crossed, **then** further attempts are throttled and the events are recorded | MUST |
| AC-1.5 | **Given** a signed-out session, **when** the refresh token is replayed, **then** it is rejected | MUST |

## AC-2 Household

| ID | Criterion | Level |
|---|---|---|
| AC-2.1 | **When** a user creates a household, **then** they become its `OWNER` and a member record exists | MUST |
| AC-2.2 | **When** an owner invites an adult by email, **then** an invitation is created and **no profile or health data is created for that person** | MUST |
| AC-2.3 | **Given** an invitation, **when** the invited adult accepts, **then** they create their own account and grant their own consent before any profile exists (D-002) | MUST |
| AC-2.4 | **Given** a household of two adults, **when** adult A requests adult B's health profile, **then** access is denied unless the permission matrix allows it | MUST |
| AC-2.5 | **Given** a user in household X, **when** they request any resource of household Y by changing an identifier, **then** the response is 404/403 and never leaks existence | MUST |
| AC-2.6 | **Given** adult B withdraws consent, **when** the household view is loaded, **then** B's profile and verdicts are absent and the household remains fully functional for everyone else | MUST |
| AC-2.7 | **Given** a household of one, **when** the app is used, **then** every feature works and the user is never prompted to add members | MUST |

## AC-3 Child profile (guardian-controlled)

| ID | Criterion | Level |
|---|---|---|
| AC-3.1 | **When** a guardian creates a child profile, **then** a `guardian_link` with relationship and authority basis is recorded, and the child has no user account (CH-001) | MUST |
| AC-3.2 | **Given** a child profile with no `ACTIVE` consent, **when** any scoring, fit, analytics or AI operation is attempted for that child, **then** **nothing is processed** and the caller receives a consent-required response (CH-002) | MUST |
| AC-3.3 | **When** guardian consent is granted, **then** purpose, policy version, timestamp, mechanism and evidence are all recorded (CH-007) | MUST |
| AC-3.4 | **Given** a child profile, **then** only age band is stored — no date of birth, no name beyond a display label (CH-006) | MUST |
| AC-3.5 | **Given** a non-guardian adult member, **when** they request the child profile, **then** access is denied and the attempt is audited (CH-009) | MUST |
| AC-3.6 | **When** a guardian withdraws consent, **then** processing stops immediately and the guardian can delete the child's data; a `CHILD_DATA_DELETED` audit event is written (CH-008) | MUST |
| AC-3.7 | **Then** no weight, body-composition, calorie-target, streak, badge, leaderboard or notification-to-child capability exists anywhere in the codebase (CH-004/005, D-017). *Read since 2026-09-12 (D-036, D-042, D-047, D-051): absolute for a **child subject**, and enforced in the domain (metric age gate, child goal rule). Adults may record weight and body metrics. Adult calorie caps wait for a reviewed floor (D-047). Adult engagement mechanics are opt-in and cooperative (D-051).* | MUST |
| AC-3.8 | **Then** no child data is transmitted to any external model provider (CH-010) | MUST |

## AC-4 Product discovery

| ID | Criterion | Level |
|---|---|---|
| AC-4.1 | **When** a user searches a brand or product name, **then** ranked results return within 400 ms p95, tolerating typos and brand aliases | MUST |
| AC-4.2 | **When** a known barcode is scanned, **then** the product resolves with its source and version date | MUST |
| AC-4.3 | **When** an unknown barcode is scanned, **then** the user is offered label capture and contribution — **never a dead end** | MUST |
| AC-4.4 | **Given** a GTIN mapped to more than one product, **when** it is looked up, **then** a disambiguation result is returned rather than a guess | MUST |
| AC-4.5 | **When** a nutrition label photo is submitted, **then** an extraction proposal with per-field confidence is returned | MUST |
| AC-4.6 | **Given** an extraction proposal, **when** the user has not confirmed it, **then** **no verdict is produced and nothing enters the catalogue** | MUST |
| AC-4.7 | **Given** low-confidence fields, **when** the confirmation screen renders, **then** those fields are visibly flagged and require explicit confirmation | MUST |
| AC-4.8 | **Given** an unreadable image, **when** extraction fails, **then** the user is offered a retry with guidance and a manual-entry path | MUST |
| AC-4.9 | **When** camera permission is denied or absent, **then** search and manual entry remain fully available | MUST |

## AC-5 Product intelligence and data integrity

| ID | Criterion | Level |
|---|---|---|
| AC-5.1 | **Then** every nutrient value displays its source and last-verified date | MUST |
| AC-5.2 | **Given** a nutrient not declared on the pack, **then** it is absent — never rendered as zero | MUST |
| AC-5.3 | **Given** two sources disagreeing on a field, **then** a conflict is retained, the version is `CONFLICTING`, and the UI says the data needs verification | MUST |
| AC-5.4 | **When** a formulation change is detected, **then** a new `product_version` is created and the previous version is preserved, not overwritten | MUST |
| AC-5.5 | **Given** a panel whose macros are implausible for its basis, **when** it is submitted, **then** write-time sanity rules reject it | MUST |
| AC-5.6 | **When** a user reports incorrect data, **then** a correction enters a review queue actionable within 48 hours | MUST |
| AC-5.7 | **Given** a declared serving size implausible for the category, **then** it is flagged in the UI | MUST |
| AC-5.8 | **Then** the app never claims a product is free from an allergen — only what the label declares, with a cross-contamination note | MUST |

## AC-6 Scoring, fit and explanation

| ID | Criterion | Level |
|---|---|---|
| AC-6.1 | **Given** identical product version, profile version and rule set, **when** scoring runs any number of times, **then** the verdict is byte-identical | MUST |
| AC-6.2 | **Then** every verdict stores product version, profile version, rule-set version and fired rule ids, and is reconstructible from them | MUST |
| AC-6.3 | **Then** every fired rule cites a `standard_ref` resolving to a real public standard; a rule without one cannot be activated | MUST |
| AC-6.4 | **Then** every rule is evaluated on both per-serving and per-100g bases and the more severe result wins | MUST |
| AC-6.5 | **Given** a declared allergen matching a profile allergen, **then** a hard flag is raised, shown separately from and above the verdict, in its own colour | MUST |
| AC-6.6 | **Given** the same product and two adults with different goals, **then** the verdicts may differ and each explanation names the specific driver | MUST |
| AC-6.7 | **When** a user asks why, **then** the explanation names the nutrient, its value, the threshold, the basis and the reference standard | MUST |
| AC-6.8 | **Given** missing or conflicting facts, **then** the verdict is `INSUFFICIENT_DATA` and no guess is presented | MUST |
| AC-6.9 | **Then** the numeric score never appears above the verdict and is never the largest element on a screen (D-003) | MUST |
| AC-6.10 | **Then** no code path exists by which a model output becomes a score, threshold or verdict | MUST |
| AC-6.11 | **Given** AI-generated explanation prose containing a number absent from the structured input, **then** the output is discarded and the deterministic template is served | MUST |
| AC-6.12 | **Given** a declared condition in the excluded set, **then** no numeric target and no condition-specific rule is produced; general information and a professional-referral message are shown (D-017) | MUST |

## AC-7 Alternatives and comparison

| ID | Criterion | Level |
|---|---|---|
| AC-7.1 | **Then** each alternative states the dimension on which it is better and, where one exists, the dimension on which it is worse (D-013) | MUST |
| AC-7.2 | **Given** no genuinely better option in the data, **then** an honest empty state is shown and the list is not padded | MUST |
| AC-7.3 | **Then** no field, flag or code path can cause a commercial relationship to alter ranking (D-012) — asserted by an architecture test | MUST |
| AC-7.4 | **When** two or three products are compared, **then** added sugar, sodium, saturated fat, protein, fibre, energy and serving size are shown side by side on a common basis | MUST |

## AC-8 Outcome capture

| ID | Criterion | Level |
|---|---|---|
| AC-8.1 | **After** a product view, **then** the user may record bought-original / bought-alternative / bought-neither in one tap, or skip | MUST |
| AC-8.2 | **Then** the prompt appears at most once per lookup, enforced by a database constraint | MUST |
| AC-8.3 | **Then** Healthy Decision Rate is computable from stored events without any additional instrumentation | MUST |

## AC-9 Privacy, consent, safety

| ID | Criterion | Level |
|---|---|---|
| AC-9.1 | **Then** consent is checked at processing time against the ledger, not merely at signup (D-018) | MUST |
| AC-9.2 | **When** a user requests export, **then** all their personal data is returned in a machine-readable form | MUST |
| AC-9.3 | **When** a user requests erasure, **then** their personal data is deleted; `consent_record` and `audit_event` survive as evidence, with the actor pseudonymised | MUST |
| AC-9.4 | **Then** no health condition, allergen, child datum, token, password or raw search string appears in any log — verified by an automated log-inspection test | MUST |
| AC-9.5 | **Then** no user-facing string, rule template or error message contains a banned word — verified by a build-failing test (D-004) | MUST |
| AC-9.6 | **Then** every screen touching self-declared conditions carries a not-medical-advice notice | MUST |
| AC-9.7 | **Given** product text containing an embedded instruction, **when** it reaches a model, **then** the instruction is not followed — verified by injection probes (D-011) | MUST |
| AC-9.8 | **Then** no third-party analytics SDK is present in the shipped bundle | MUST |

## AC-10 Experience quality

| ID | Criterion | Level |
|---|---|---|
| AC-10.1 | **Then** a user reaches a useful verdict within 10 seconds of a successful lookup from a cold open | MUST |
| AC-10.2 | **Then** every screen has a designed loading, empty and error state | MUST |
| AC-10.3 | **Then** "product not found" and "service unavailable" are visibly different states (Constitution §46) | MUST |
| AC-10.4 | **Then** recently viewed products remain readable without connectivity | MUST |
| AC-10.5 | **Then** every interactive target is at least 44 × 44 px and reachable one-handed | MUST |
| AC-10.6 | **Then** verdict state is never conveyed by colour alone | MUST |
| AC-10.7 | **Then** the app passes axe with no serious or critical violations, and every flow is keyboard-navigable | MUST |
| AC-10.8 | **Then** an anonymous user can search and view product-level facts and assessment; the personal-fit strip is the sign-in boundary and says why (D-020) | MUST |

---

## Final acceptance — the brief's 21 checks

A real user must be able to: create an account · create a household · add adult members · add a child through a guardian-controlled consent flow · search a packaged product · scan a barcode · capture a label · retrieve or create structured product information · read understandable nutrition information · see an evidence-backed assessment · see personal and household fit · understand why · compare products · see justified better alternatives · manage household preferences · manage privacy and consent · recover from errors without losing state · pass automated tests · pass critical E2E tests · pass security and privacy checks · with no known critical or high production blocker.

Two of these carry a standing qualification that will be repeated verbatim in the release report rather than quietly satisfied:

- **Child flow** — implemented and tested, but the consent *mechanism* is guardian attestation, which is `PROVISIONAL` pending privacy counsel (D-001). Production launch with real child data is blocked.
- **OCR and AI explanation** — implemented behind ports; live behaviour depends on a provider credential this project does not have. Status is reported as `MOCKED` until one exists.

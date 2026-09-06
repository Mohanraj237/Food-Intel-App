-- =============================================================================
-- V9  Contribution catalogue-improvement acknowledgment (resolves D-026)
--
-- D-026 (decision-log.md) found that PRODUCT_IMPROVEMENT — the consent
-- purpose seeded in V1 for "contributing a product to the catalogue" — could
-- not be wired to the existing member-scoped ConsentGate: contributing is an
-- account-level action with no household-member id to gate against, and the
-- purpose's own seeded text says the action "contains no personal data about
-- you". Extending the consent ledger to a new account-scoped subject type
-- would be real, non-trivial surgery to a privacy-sensitive module for a
-- purpose that is not about personal data at all.
--
-- Resolution (see decision-log.md for the full entry): treat this as a
-- terms-of-contribution acknowledgment recorded on the contribution itself,
-- not a revocable consent-ledger grant. Required at submission time
-- (ContributionService.submit rejects a false value); the real
-- PRODUCT_IMPROVEMENT purpose text (GET /consent/policies) is what the
-- frontend shows next to the checkbox, so the copy the user sees is not
-- invented separately from the seeded policy.
-- =============================================================================

ALTER TABLE product_contribution
    ADD COLUMN contributor_acknowledged boolean NOT NULL DEFAULT false;

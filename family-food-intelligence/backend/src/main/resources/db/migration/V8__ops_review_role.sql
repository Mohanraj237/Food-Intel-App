-- =============================================================================
-- V8  Operator role for the ops-review console
--
-- The smallest useful distinction for gating contribution approval: a single
-- role column, not a general permissions system. See UserAccount.OperatorRole
-- and identity.application.OperatorAccess (D-023-style published interface,
-- mirroring HouseholdAccess) for how other modules check it.
--
-- Promotion to OPS_REVIEWER is a direct database action documented in
-- docs/operations/operations-guide.md — there is no self-service or API path
-- to grant it, deliberately, until real review volume justifies more.
-- =============================================================================

ALTER TABLE user_account
    ADD COLUMN role text NOT NULL DEFAULT 'MEMBER'
        CHECK (role IN ('MEMBER', 'OPS_REVIEWER'));

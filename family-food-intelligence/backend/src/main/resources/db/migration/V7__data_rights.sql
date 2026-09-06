-- =============================================================================
-- V7  Data rights: account erasure terminal state, contribution anonymisation
--
-- Implements NFR-09 / CH-008 / FR-010's erasure gap and AC-9.2/AC-9.3.
--
-- Binding decisions:
--   AC-9.3  erasure deletes personal data; consent_record and audit_event survive
--           as evidence, with the actor pseudonymised
--   D-014-adjacent  household_member/guardian_link/consent_record are never
--           hard-deleted for a person who still has surviving evidence rows
--           pointing at them (see V3's own comments on household_member and
--           consent_record) — erasure anonymises user_account in place instead
--           of deleting the row, so every existing "REFERENCES user_account(id)"
--           and "REFERENCES household_member(id)" foreign key stays intact.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- ERASED is the terminal state after the grace period in user_account.deleted_at
-- (set at request time, per the column's existing comment) elapses and the
-- scheduled hard-erasure sweep runs. DELETION_REQUESTED already blocks sign-in
-- via UserAccount.isUsable(); ERASED is the same block, permanently, once the
-- account row itself has been anonymised.
-- -----------------------------------------------------------------------------
ALTER TABLE user_account DROP CONSTRAINT user_account_status_check;
ALTER TABLE user_account ADD CONSTRAINT user_account_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED', 'ERASED'));


-- -----------------------------------------------------------------------------
-- A contribution's factual content (product name, nutrients, ingredients) is not
-- personal data about the submitter — it is an unreviewed catalogue-improvement
-- record. On account erasure the row is kept (a future ops-review console may
-- still act on it) but the link back to the erased person is removed, mirroring
-- the pseudonymise-don't-delete treatment audit_event already gets (see V2's
-- comment on that table). This column was NOT NULL because every contribution
-- always has a submitter at write time; erasure is the one legitimate case where
-- that stops being true after the fact.
-- -----------------------------------------------------------------------------
ALTER TABLE product_contribution ALTER COLUMN submitted_by_user_id DROP NOT NULL;

COMMENT ON COLUMN product_contribution.submitted_by_user_id IS
    'Null means the submitting account was erased; the submission itself is retained '
    '(D-027, decision-log.md).';

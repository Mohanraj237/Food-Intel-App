-- =============================================================================
-- V10  Activity — outcome capture and recent lookups (FR-012, D-005, D-014)
--
-- Deliberately small: one table, one purpose. Not analytics infrastructure, not
-- a dashboard — the minimum needed so "Did you buy it?" (D-005) persists an
-- honest answer instead of making no network call at all (D-027's documented
-- interim state).
--
-- account_id, never household_id: activity is what *this signed-in person* did,
-- scoped exactly like every other self-only data (D-002) — not shared with
-- other household members, mirroring how an adult's own profile/consent data
-- is never visible to a household owner either.
--
-- No raw search text, no free text of any kind, no profile/health fields
-- (architecture "activity ... Must never: store raw search text unhashed").
-- Every event is a closed enum value plus product identity — nothing here
-- could leak a health condition or an allergen.
-- =============================================================================

CREATE TABLE activity_event (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id              uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    product_id              uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    alternative_product_id  uuid REFERENCES product(id) ON DELETE SET NULL,
    event_type              text NOT NULL
                              CHECK (event_type IN ('VIEWED', 'SCANNED', 'COMPARED', 'BOUGHT',
                                                    'BOUGHT_ALTERNATIVE', 'BOUGHT_NEITHER', 'SKIPPED')),
    occurred_at             timestamptz NOT NULL DEFAULT now()
);

-- The "recent activity" query's own index: newest-first per account.
CREATE INDEX ix_activity_account_time ON activity_event (account_id, occurred_at DESC);
CREATE INDEX ix_activity_product ON activity_event (product_id);

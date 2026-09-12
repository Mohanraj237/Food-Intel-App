-- Phase 1 of the individual + family wellness scope (D-034).
--
-- Three things, deliberately small: a marker that lets Individual Mode be a
-- household of one (D-035), one time-series table every future tracker writes
-- into, and a default-closed visibility grant table (D-038).
--
-- What is NOT here, and why it is absent rather than forgotten:
--   * no derived category column (no bmi_category, no bp_category) — naming a
--     value "high" is a clinical claim and needs a citable standard (D-037)
--   * no goal/target column — goals are a Phase 2 entity with their own age gate
--   * no per-tracker tables — see docs/architecture/wellness-platform-architecture.md §2.1
--   * no free-text note on an observation — a note on a blood-pressure row is a
--     symptom diary, which carries a different legal posture entirely

-- ---------------------------------------------------------------- individual mode

-- A household created implicitly for a single person (D-035). The UI suppresses
-- household framing when this is true; there is no second person model behind it,
-- so "I started alone, then added my family" needs no migration.
ALTER TABLE household
    ADD COLUMN is_individual boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN household.is_individual IS
    'D-035: true when created implicitly for one person in Individual Mode. Presentation only — the data path is identical to any other household.';

-- ------------------------------------------------------------------- observations

CREATE TABLE health_observation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The subject is always a household_member, never a user_account (D-035).
    -- A child has no account, so account-scoping would make a child's own
    -- permitted metrics unrepresentable.
    household_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,

    metric_code         text NOT NULL,
    metric_category     text NOT NULL CHECK (metric_category IN (
                            'BODY', 'ACTIVITY', 'SLEEP', 'HYDRATION',
                            'NUTRITION', 'CLINICAL', 'WELLBEING')),

    -- numeric, not double: a weight of 72.35 kg must round-trip exactly, and a
    -- float sum over a day's water intake drifts visibly.
    value               numeric(12, 3) NOT NULL,
    unit                text NOT NULL CHECK (length(btrim(unit)) BETWEEN 1 AND 16),

    -- When the measurement applies to, which is not when it was typed in. A
    -- weight entered on Monday evening for Sunday morning is one observation
    -- with two different timestamps, and trends must use the former.
    effective_at        timestamptz NOT NULL,
    recorded_at         timestamptz NOT NULL DEFAULT now(),

    -- Who entered it. For a child this is the guardian; for an adult it is the
    -- subject themselves (D-002 — no adult proxies for another adult).
    recorded_by_user_id uuid NOT NULL REFERENCES user_account(id),

    source              text NOT NULL DEFAULT 'MANUAL'
                            CHECK (source IN ('MANUAL', 'DERIVED', 'IMPORT')),

    created_at          timestamptz NOT NULL DEFAULT now(),

    -- A measurement cannot apply to the future. Guards a common date-picker
    -- slip that would otherwise poison every "this week" window.
    CONSTRAINT ck_observation_not_future
        CHECK (effective_at <= recorded_at + interval '1 day'),

    -- No negative quantity is meaningful for any metric in the taxonomy.
    CONSTRAINT ck_observation_value_non_negative CHECK (value >= 0)
);

-- The access pattern is "this member's history of this metric, newest first",
-- which every tracker screen and every insight window uses.
CREATE INDEX ix_observation_member_metric_effective
    ON health_observation (household_member_id, metric_code, effective_at DESC);

-- And "everything for this member in a window", for a dashboard.
CREATE INDEX ix_observation_member_effective
    ON health_observation (household_member_id, effective_at DESC);

COMMENT ON TABLE health_observation IS
    'One typed measurement about one household member. The single write path for every tracker (D-034 Phase 1). Interpretation of these values requires a citable standard (D-037) and is deliberately not stored here.';

-- -------------------------------------------------------------------- visibility

-- Default-closed sharing (D-038). The absence of a row is a denial, which is why
-- this table ships in Phase 1 — before any tracker writes data — rather than
-- being retrofitted once users already have an implicit all-household default.
CREATE TABLE metric_visibility (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Whose data is being shared.
    subject_member_id   uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,

    -- Who may see it. A member, not an account: the audience is someone's place
    -- in the household, and it disappears with their membership.
    audience_member_id  uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,

    metric_category     text NOT NULL CHECK (metric_category IN (
                            'BODY', 'ACTIVITY', 'SLEEP', 'HYDRATION',
                            'NUTRITION', 'CLINICAL', 'WELLBEING')),

    granted_at          timestamptz NOT NULL DEFAULT now(),
    granted_by_user_id  uuid NOT NULL REFERENCES user_account(id),
    revoked_at          timestamptz,

    -- Sharing with yourself is meaningless: the subject always sees their own data.
    CONSTRAINT ck_visibility_not_self
        CHECK (subject_member_id <> audience_member_id)
);

-- One *live* grant per (subject, audience, category), enforced as a partial
-- unique index rather than a table constraint on (..., revoked_at): Postgres
-- treats NULLs as distinct in a UNIQUE, so that spelling would have happily
-- allowed unlimited rows with revoked_at IS NULL — exactly the duplicates it
-- looks like it prevents. Revoking sets revoked_at rather than deleting, so the
-- sharing history survives for the audit trail a household dispute would need,
-- and a later re-grant inserts a fresh row.
CREATE UNIQUE INDEX uq_visibility_live
    ON metric_visibility (subject_member_id, audience_member_id, metric_category)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE metric_visibility IS
    'D-038: explicit per-(subject, audience, category) grant. Default-closed — no row means no access. Household role is never consulted; OWNER is not a health-data role.';

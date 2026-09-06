-- =============================================================================
-- V2  Identity, throttling and audit
-- =============================================================================

CREATE TABLE user_account (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- varchar rather than citext: emails are normalised to lowercase by
    -- EmailNormaliser before every write and every lookup, and the CHECK below makes
    -- that invariant explicit in the schema. citext would express the same rule less
    -- visibly, in a non-standard type that ORM schema validation cannot verify.
    email              varchar(254) NOT NULL UNIQUE
                         CHECK (email = lower(email)),
    email_verified_at  timestamptz,
    password_hash      text NOT NULL,
    -- Reserved for phone/OTP, which is BLOCKED on an SMS provider (D-008).
    -- Present now so adding it later is not a migration of live identity data.
    phone_hash         text,
    status             text NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE','SUSPENDED','DELETION_REQUESTED')),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz
);

CREATE INDEX ix_user_account_status ON user_account (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_user_account_deletion ON user_account (deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON COLUMN user_account.password_hash IS 'Argon2id. Never a raw password (D-008).';


-- -----------------------------------------------------------------------------
-- Refresh tokens
--
-- Opaque random values, stored as a hash. Deliberately NOT JWTs: a stateless
-- refresh token cannot be revoked, which is unacceptable for a product holding
-- health data. replaced_by_id gives us rotation-with-reuse-detection.
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_token (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    token_hash      text NOT NULL UNIQUE,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    revoked_reason  text,
    replaced_by_id  uuid REFERENCES refresh_token(id),
    user_agent_hash text,
    ip_hash         text
);

CREATE INDEX ix_refresh_active ON refresh_token (user_account_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_expiry ON refresh_token (expires_at) WHERE revoked_at IS NULL;

COMMENT ON COLUMN refresh_token.ip_hash IS
    'Hashed. No raw IP address is retained anywhere (D-018).';


-- -----------------------------------------------------------------------------
-- Login attempts
--
-- email_hash rather than email: a leak of the throttling table must not enumerate
-- users. Rows are purged after 24 hours by a scheduled job.
-- -----------------------------------------------------------------------------
CREATE TABLE login_attempt (
    id           bigserial PRIMARY KEY,
    email_hash   text NOT NULL,
    ip_hash      text NOT NULL,
    succeeded    boolean NOT NULL,
    attempted_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_login_attempt_email ON login_attempt (email_hash, attempted_at DESC);
CREATE INDEX ix_login_attempt_ip    ON login_attempt (ip_hash, attempted_at DESC);
CREATE INDEX ix_login_attempt_purge ON login_attempt (attempted_at);


-- -----------------------------------------------------------------------------
-- Rate limiting (D-007: database-backed, no Redis)
-- -----------------------------------------------------------------------------
CREATE TABLE rate_limit_bucket (
    bucket_key   text NOT NULL,
    window_start timestamptz NOT NULL,
    count        int NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_key, window_start)
);

CREATE INDEX ix_rate_limit_purge ON rate_limit_bucket (window_start);


-- -----------------------------------------------------------------------------
-- Audit events
--
-- Append-only, enforced at the grant level rather than by application convention
-- (Pack PRD NFR: "immutable audit log"). See V2 grants at the end of this file.
--
-- metadata must never carry a health value. The masking layer covers logs; this
-- is a separate store and the rule is enforced by code review plus the
-- AuditEventContentTest.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_event (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    actor_user_id  uuid,
    actor_role     text,
    correlation_id text,
    action         text NOT NULL,
    resource_type  text NOT NULL,
    resource_id    uuid,
    household_id   uuid,
    outcome        text NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','ERROR')),
    metadata       jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_audit_household ON audit_event (household_id, occurred_at DESC);
CREATE INDEX ix_audit_action    ON audit_event (action, occurred_at DESC);
CREATE INDEX ix_audit_actor     ON audit_event (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_resource  ON audit_event (resource_type, resource_id);

COMMENT ON TABLE audit_event IS
    'Append-only. UPDATE and DELETE are revoked from the application role. '
    'Retained 7 years; on erasure the actor id is pseudonymised rather than removed, '
    'because the record that deletion happened must itself survive.';


-- -----------------------------------------------------------------------------
-- Analytics events
--
-- Our own store. No third-party analytics SDK ships before privacy counsel
-- review (D-018, PANTRY 06 §7). props must contain no health data and no raw
-- search text.
-- -----------------------------------------------------------------------------
CREATE TABLE analytics_event (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            text NOT NULL,
    user_account_id uuid REFERENCES user_account(id) ON DELETE CASCADE,
    household_id    uuid,
    props           jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_analytics_name ON analytics_event (name, occurred_at DESC);
CREATE INDEX ix_analytics_user ON analytics_event (user_account_id, occurred_at DESC);


-- -----------------------------------------------------------------------------
-- Immutability of the audit log.
--
-- Implemented as a trigger rather than only as a GRANT, because the application
-- and the migration tool commonly share a role in small deployments. The trigger
-- holds regardless of which role is connected.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit_event_is_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'raise_exception';
END;
$$;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_is_append_only();

CREATE TRIGGER trg_audit_event_no_delete
    BEFORE DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_is_append_only();

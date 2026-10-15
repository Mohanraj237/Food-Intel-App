-- Wellness W1 — Foundation (D-042 .. D-053).
--
-- Five changes, each small and each load-bearing:
--   1. metric_visibility becomes health_access_grant (D-044): one grant table for
--      every health module, with a VIEW/MANAGE level and non-metric scopes.
--      Renamed in place so that sharing history recorded under D-038 survives.
--   2. household.time_zone (D-053): "today" is the household's day, not UTC's.
--   3. person_profile.activity_level: self-reported; never used to compute an
--      energy target (D-047).
--   4. audit_event.subject_member_id: whose data an access touched, so a person
--      can be shown who read or wrote their health data (D-044 access log).
--   5. Consent purposes for wellness tracking (D-048), for adults and — guardian
--      granted — for children.
--
-- V12 and V13 are frozen: they are applied to shared databases and Flyway
-- checksums them. Everything new lives here.

-- =========================================================== 1. access grants

ALTER TABLE metric_visibility RENAME TO health_access_grant;
ALTER TABLE health_access_grant RENAME COLUMN metric_category TO scope;

-- The seven metric categories keep their names, so every existing row is already
-- a valid scope. The five new ones cover habits, the food profile a caregiver
-- needs to evaluate a product for someone, and the Phase 4 domains — declared
-- now so the grant model does not change shape when those domains arrive.
ALTER TABLE health_access_grant DROP CONSTRAINT metric_visibility_metric_category_check;
ALTER TABLE health_access_grant ADD CONSTRAINT ck_access_grant_scope CHECK (scope IN (
    'BODY', 'ACTIVITY', 'SLEEP', 'HYDRATION', 'NUTRITION', 'CLINICAL', 'WELLBEING',
    'HABITS', 'FOOD_PROFILE', 'MEDICATIONS', 'APPOINTMENTS', 'RECORDS'));

-- VIEW reads. MANAGE records or acts on the subject's behalf in that scope, and
-- implies VIEW. Every pre-existing grant was a read grant (D-038 had nothing
-- else), so the default is correct for them; it is then dropped so that every
-- future row states its level explicitly.
ALTER TABLE health_access_grant
    ADD COLUMN access_level text NOT NULL DEFAULT 'VIEW'
        CONSTRAINT ck_access_grant_level CHECK (access_level IN ('VIEW', 'MANAGE'));
ALTER TABLE health_access_grant ALTER COLUMN access_level DROP DEFAULT;

ALTER TABLE health_access_grant RENAME CONSTRAINT ck_visibility_not_self TO ck_access_grant_not_self;
ALTER TABLE health_access_grant RENAME CONSTRAINT metric_visibility_pkey TO health_access_grant_pkey;

-- Still one live grant per (subject, audience, scope). The level is an attribute
-- of that grant, not part of its identity: changing VIEW to MANAGE revokes the
-- old row and inserts a new one, so "since when could she act for me" stays
-- answerable.
ALTER INDEX uq_visibility_live RENAME TO uq_access_grant_live;

-- "What has been shared with me" — the audience-side read.
CREATE INDEX ix_access_grant_audience
    ON health_access_grant (audience_member_id)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE health_access_grant IS
    'D-044: explicit per-(subject, audience, scope) grant with a VIEW or MANAGE level. Default-closed — no row means no access. Household role is never consulted. Only the subject grants; child data is never grantable (guardian link only).';

-- ============================================================ 2. time zone

-- IANA zone id, validated in the domain (java.time.ZoneId). Storage stays UTC
-- everywhere; this only decides where a member's day starts and ends.
ALTER TABLE household
    ADD COLUMN time_zone text NOT NULL DEFAULT 'Asia/Kolkata'
        CONSTRAINT ck_household_time_zone CHECK (length(btrim(time_zone)) BETWEEN 1 AND 64);

COMMENT ON COLUMN household.time_zone IS
    'D-053: IANA zone whose midnight bounds "today", daily goals, habit days and insight day counts for this household''s members.';

-- ======================================================== 3. activity level

-- Self-reported and nullable. It describes; it never prescribes — no energy
-- target is computed from it (D-047).
ALTER TABLE person_profile
    ADD COLUMN activity_level text
        CONSTRAINT ck_profile_activity_level CHECK (activity_level IS NULL OR activity_level IN (
            'SEDENTARY', 'LIGHTLY_ACTIVE', 'MODERATELY_ACTIVE', 'VERY_ACTIVE'));

-- ========================================================== 4. audit subject

-- Adding a nullable column rewrites no rows, so the append-only triggers on this
-- table are not involved. No foreign key: an audit row must outlive the member
-- it describes, exactly as resource_id already does.
ALTER TABLE audit_event ADD COLUMN subject_member_id uuid;

CREATE INDEX ix_audit_subject
    ON audit_event (subject_member_id, occurred_at DESC)
    WHERE subject_member_id IS NOT NULL;

COMMENT ON COLUMN audit_event.subject_member_id IS
    'The household member whose data this event touched, when that is not the actor. Backs the per-person access log (D-044).';

-- ====================================================== 5. consent purposes

-- Collection and cross-member disclosure of wellness data require one of these
-- to be ACTIVE for the subject at processing time (D-048, D-018). The child
-- purpose requires a guardian and is PROVISIONAL on the same terms as D-001.
INSERT INTO consent_purpose
    (code, name, description, requires_verifiable, is_essential, sort_order)
VALUES
    ('HEALTH_TRACKING',
     'Tracking your own health and wellness',
     'Store the meals, activity, sleep, water, body and health measurements, goals and '
     'habits you record, and use them to show you your own history, progress and '
     'insights. Nothing is shared with anyone unless you choose to share it.',
     false, false, 25),

    ('CHILD_WELLNESS_TRACKING',
     'Wellness tracking for a child in your household',
     'Store the meals, activity, sleep, water, wellbeing and habits a guardian records '
     'for a child, to show the guardian that child''s own history. No weight or body '
     'measurements, no health readings, no engagement features or notifications to the '
     'child, no advertising.',
     true, false, 35);

INSERT INTO consent_policy_version
    (purpose_code, version, document_uri, summary, effective_from)
VALUES
    ('HEALTH_TRACKING', '1.0', '/legal/privacy#health-tracking',
     'We keep the health and wellness information you record so you can see your own '
     'history and progress. It stays private to you unless you share a category with '
     'someone in your household, and you can withdraw, export or delete it at any time.',
     '2026-09-12T00:00:00Z'),
    ('CHILD_WELLNESS_TRACKING', '1.0', '/legal/privacy#child-wellness',
     'A guardian may record a child''s meals, activity, sleep, water and habits to follow '
     'that child''s own routine. We never record a child''s weight or body measurements, '
     'never rank or reward a child, and never send a child notifications.',
     '2026-09-12T00:00:00Z');

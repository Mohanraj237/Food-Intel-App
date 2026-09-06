-- =============================================================================
-- V3  Household, consent ledger and versioned person profile
--
-- Implements docs/architecture/database-design.md sections 4, 5 and 6.
--
-- Binding decisions:
--   D-001  a child has no account; a hard gate blocks processing until an ACTIVE
--          consent record exists for the child and the purpose being invoked
--   D-002  adults are their own data principals; adding an adult creates an
--          invitation and nothing else
--   D-017  the goal vocabulary is closed and cannot express restriction framing
--   D-018  consent is evaluated at processing time against this ledger
--
-- consent_purpose and consent_policy_version are created and seeded by V1 and
-- are referenced, not recreated, here.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Household
--
-- A household of one is a fully valid terminal state (D-002). Nothing in this
-- schema or in the application treats an unaccompanied owner as incomplete.
-- -----------------------------------------------------------------------------
CREATE TABLE household (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name               text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
    created_by_user_id uuid NOT NULL REFERENCES user_account(id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz
);

CREATE INDEX ix_household_creator ON household (created_by_user_id) WHERE deleted_at IS NULL;


-- -----------------------------------------------------------------------------
-- Household membership
--
-- The two CHECK constraints below are the load-bearing part of the child model.
-- They make two statements structurally true rather than merely intended:
--
--   * a child has no user account, so there is no credential, no session and no
--     notification channel that could ever reach a child (D-001, Pack 06 CH-001);
--   * an account holder can never be a DEPENDENT, so the guardian-controlled
--     pathway cannot be pointed at an adult and used to bypass D-002.
--
-- Written as an equivalence rather than as two implications on purpose: either
-- direction alone leaves the other half expressible.
--
-- display_label is a label, not a legal name. Nothing in the application asks
-- for or stores a child's name, and no column exists to hold one.
-- -----------------------------------------------------------------------------
CREATE TABLE household_member (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    uuid NOT NULL REFERENCES household(id) ON DELETE CASCADE,
    -- No ON DELETE action: a membership referenced by a surviving consent_record
    -- must not disappear when an account row is finally hard-deleted. Erasure
    -- detaches the person from the household through left_at and a scrubbed
    -- label instead (AC-9.3).
    user_account_id uuid REFERENCES user_account(id),
    member_type     text NOT NULL CHECK (member_type IN ('ADULT', 'CHILD')),
    role            text NOT NULL CHECK (role IN ('OWNER', 'ADULT', 'DEPENDENT')),
    display_label   text NOT NULL CHECK (length(btrim(display_label)) BETWEEN 1 AND 60),
    joined_at       timestamptz NOT NULL DEFAULT now(),
    left_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_member_child_has_no_account
        CHECK ((member_type = 'CHILD') = (user_account_id IS NULL)),
    CONSTRAINT ck_member_child_is_dependent
        CHECK ((member_type = 'CHILD') = (role = 'DEPENDENT'))
);

-- One live membership per account per household. Partial, so a person who left
-- and rejoined does not collide with their own historical row.
CREATE UNIQUE INDEX uq_member_account_per_household
    ON household_member (household_id, user_account_id)
    WHERE user_account_id IS NOT NULL AND left_at IS NULL;

CREATE INDEX ix_member_household ON household_member (household_id) WHERE left_at IS NULL;
CREATE INDEX ix_member_account   ON household_member (user_account_id) WHERE left_at IS NULL;

COMMENT ON COLUMN household_member.display_label IS
    'A label chosen by the household, never a legal name (data minimisation, CH-006).';


-- -----------------------------------------------------------------------------
-- Guardian link  (Pack 06 CH-001)
--
-- authority_basis records the guardian's own statement of the basis on which
-- they act. It is evidence for a later legal review, not a verification: the
-- verifiable-consent mechanism required by the DPDP Rules is PROVISIONAL and
-- blocked on privacy counsel (D-001).
-- -----------------------------------------------------------------------------
CREATE TABLE guardian_link (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_member_id    uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,
    guardian_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,
    relationship       text NOT NULL CHECK (relationship IN ('PARENT', 'LEGAL_GUARDIAN')),
    authority_basis    text NOT NULL CHECK (length(btrim(authority_basis)) BETWEEN 1 AND 200),
    created_at         timestamptz NOT NULL DEFAULT now(),
    revoked_at         timestamptz,

    CONSTRAINT ck_guardian_not_self CHECK (child_member_id <> guardian_member_id)
);

CREATE UNIQUE INDEX uq_guardian_link_active
    ON guardian_link (child_member_id, guardian_member_id)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_guardian_by_guardian ON guardian_link (guardian_member_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_guardian_by_child    ON guardian_link (child_member_id)    WHERE revoked_at IS NULL;


-- -----------------------------------------------------------------------------
-- Household invitation  (D-002)
--
-- Adding an adult produces a row here and nothing else. No member record, no
-- profile, no consent record and no health data exists for the invitee until
-- they accept with their own account and grant their own consent.
--
-- email is nullable against the design note "deleted when the invitation reaches
-- a terminal state": the two cannot both hold with a NOT NULL column, and the
-- deletion obligation is the stronger of the two. The CHECK keeps the address
-- mandatory for exactly as long as it is needed to deliver the invitation.
-- -----------------------------------------------------------------------------
CREATE TABLE household_invitation (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id         uuid NOT NULL REFERENCES household(id) ON DELETE CASCADE,
    invited_by_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,
    email                varchar(254) CHECK (email IS NULL OR email = lower(email)),
    token_hash           text NOT NULL UNIQUE,
    status               text NOT NULL
                           CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    expires_at           timestamptz NOT NULL,
    accepted_at          timestamptz,
    accepted_by_user_id  uuid REFERENCES user_account(id),
    created_at           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_invitation_pending_has_email
        CHECK (status <> 'PENDING' OR email IS NOT NULL)
);

CREATE INDEX ix_invitation_household ON household_invitation (household_id);
CREATE INDEX ix_invitation_pending   ON household_invitation (email) WHERE status = 'PENDING';

COMMENT ON COLUMN household_invitation.email IS
    'The only plaintext contact address retained anywhere. Cleared the moment the '
    'invitation reaches a terminal state.';
COMMENT ON COLUMN household_invitation.token_hash IS
    'SHA-256 of an opaque 256-bit token. The token itself is returned to the inviter '
    'exactly once and is never stored.';


-- -----------------------------------------------------------------------------
-- Consent ledger — the gate  (D-001, D-018)
--
-- Every read or computation touching a member's health data calls
-- ConsentGate.require(memberId, purpose) and this table is the only authority it
-- consults. The check happens when data is used, not when an account is created.
--
-- The partial unique index is what makes the gate a single-row question: at most
-- one ACTIVE record can exist per subject per purpose, so "is this permitted"
-- can never be answered by picking one of several conflicting rows.
-- -----------------------------------------------------------------------------
CREATE TABLE consent_record (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_member_id  uuid NOT NULL REFERENCES household_member(id),
    subject_type       text NOT NULL CHECK (subject_type IN ('ADULT_SELF', 'CHILD')),
    granted_by_user_id uuid NOT NULL REFERENCES user_account(id),
    purpose_code       text NOT NULL REFERENCES consent_purpose(code),
    policy_version_id  uuid NOT NULL REFERENCES consent_policy_version(id),
    status             text NOT NULL
                         CHECK (status IN ('PENDING', 'ACTIVE', 'WITHDRAWN', 'EXPIRED')),
    mechanism          text NOT NULL
                         CHECK (mechanism IN ('SELF_ATTESTATION', 'GUARDIAN_ATTESTATION')),
    evidence           jsonb NOT NULL DEFAULT '{}'::jsonb,
    granted_at         timestamptz,
    withdrawn_at       timestamptz,
    expires_at         timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),

    -- A child's consent can only ever have been given by a guardian. The
    -- converse is not constrained: an adult acting for themselves uses
    -- SELF_ATTESTATION, and D-002 is enforced in code because it is a statement
    -- about who may write the row, not about the row's contents.
    CONSTRAINT ck_consent_child_mechanism
        CHECK (subject_type <> 'CHILD' OR mechanism = 'GUARDIAN_ATTESTATION'),
    CONSTRAINT ck_consent_active_has_grant
        CHECK (status <> 'ACTIVE' OR granted_at IS NOT NULL),
    CONSTRAINT ck_consent_withdrawn_has_time
        CHECK (status <> 'WITHDRAWN' OR withdrawn_at IS NOT NULL)
);

CREATE INDEX ix_consent_lookup
    ON consent_record (subject_member_id, purpose_code, status);
CREATE INDEX ix_consent_granter
    ON consent_record (granted_by_user_id, created_at DESC);

CREATE UNIQUE INDEX uq_consent_active_per_subject_purpose
    ON consent_record (subject_member_id, purpose_code)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE consent_record IS
    'Survives erasure as evidence (AC-9.3). A withdrawal moves a row to WITHDRAWN; '
    'it never deletes it.';
COMMENT ON COLUMN consent_record.mechanism IS
    'GUARDIAN_ATTESTATION is REQUIRES_LEGAL_SIGNOFF (D-001). It records a guardian '
    'attestation with audit evidence and is not represented, here or anywhere else, '
    'as DPDP-verifiable consent.';


-- -----------------------------------------------------------------------------
-- Person profile — versioned, never overwritten
--
-- An update writes a new row and supersedes the old one, so a verdict can name
-- the exact profile version that produced it (D-014 applied to people).
--
-- age_band and nothing finer. There is no date_of_birth column, for an adult or
-- a child, and adding one would be a schema change with a decision-log entry
-- attached (CH-006).
-- -----------------------------------------------------------------------------
CREATE TABLE person_profile (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,
    version             int NOT NULL CHECK (version >= 1),
    is_current          boolean NOT NULL DEFAULT true,
    age_band            text NOT NULL CHECK (age_band IN (
                            'A18_30', 'A31_45', 'A46_60', 'A60_PLUS',
                            'C1_3', 'C4_6', 'C7_9', 'C10_12', 'C13_17')),
    sex                 text CHECK (sex IS NULL OR sex IN (
                            'FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    dietary_preference  text CHECK (dietary_preference IS NULL OR dietary_preference IN (
                            'VEG', 'NON_VEG', 'EGGETARIAN', 'VEGAN', 'JAIN', 'NO_PREFERENCE')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    superseded_at       timestamptz,
    created_by_user_id  uuid NOT NULL REFERENCES user_account(id),

    CONSTRAINT uq_profile_version UNIQUE (household_member_id, version),
    CONSTRAINT ck_profile_current_not_superseded
        CHECK (NOT is_current OR superseded_at IS NULL)
);

CREATE UNIQUE INDEX uq_profile_current
    ON person_profile (household_member_id)
    WHERE is_current;

CREATE INDEX ix_profile_member ON person_profile (household_member_id, version DESC);


-- -----------------------------------------------------------------------------
-- Goals — a closed vocabulary that cannot express restriction  (D-017)
--
-- The seven values below are the entire vocabulary. There is no weight goal, no
-- body-composition goal and no calorie target, for any profile, and the CHECK
-- means one cannot be introduced by application code alone. For a child the
-- point is sharper still: the vocabulary that could express restriction is
-- simply not offered.
-- -----------------------------------------------------------------------------
CREATE TABLE profile_goal (
    profile_id uuid NOT NULL REFERENCES person_profile(id) ON DELETE CASCADE,
    goal_code  text NOT NULL CHECK (goal_code IN (
                   'REDUCE_ADDED_SUGAR',
                   'REDUCE_SODIUM',
                   'REDUCE_SATURATED_FAT',
                   'INCREASE_FIBRE',
                   'INCREASE_PROTEIN',
                   'AVOID_TRANS_FAT',
                   'GENERAL_BALANCED_EATING')),
    PRIMARY KEY (profile_id, goal_code)
);


CREATE TABLE profile_allergen (
    profile_id    uuid NOT NULL REFERENCES person_profile(id) ON DELETE CASCADE,
    allergen_code text NOT NULL REFERENCES allergen(code),
    PRIMARY KEY (profile_id, allergen_code)
);


-- -----------------------------------------------------------------------------
-- Declared conditions
--
-- Free-coded on purpose: a person may record something we have no rule for, and
-- refusing to store it would push them to lie about a closed list. Codes in the
-- excluded set (pregnancy, paediatric therapeutic, CKD, oncology) are accepted
-- for the person's own record-keeping and activate no condition-specific rule
-- whatsoever (D-017); the app answers with general nutrition information and a
-- route to a qualified professional.
-- -----------------------------------------------------------------------------
CREATE TABLE profile_condition (
    profile_id     uuid NOT NULL REFERENCES person_profile(id) ON DELETE CASCADE,
    condition_code text NOT NULL CHECK (length(btrim(condition_code)) BETWEEN 1 AND 60),
    self_declared  boolean NOT NULL DEFAULT true,
    PRIMARY KEY (profile_id, condition_code)
);

COMMENT ON TABLE profile_condition IS
    'Self-declared only. Never a diagnosis, never used to produce a condition-specific '
    'numeric target, and never a basis for a therapeutic claim (D-017).';

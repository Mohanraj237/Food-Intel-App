package com.familyfood.audit;

/**
 * The closed set of auditable actions.
 *
 * <p>An enum rather than free text so that the audit trail is queryable and so that
 * adding an action is a deliberate, reviewable act. Consent and child-data actions are
 * mandatory to audit (Pack 06, Constitution §33).
 */
public enum AuditAction {

    // identity
    ACCOUNT_REGISTERED,
    ACCOUNT_LOGIN_SUCCEEDED,
    ACCOUNT_LOGIN_FAILED,
    ACCOUNT_LOGGED_OUT,
    ACCOUNT_PASSWORD_CHANGED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_DELETION_REQUESTED,
    ACCOUNT_DELETED,
    REFRESH_TOKEN_REUSE_DETECTED,

    // household
    HOUSEHOLD_CREATED,
    HOUSEHOLD_MEMBER_ADDED,
    HOUSEHOLD_MEMBER_REMOVED,
    HOUSEHOLD_INVITATION_SENT,
    HOUSEHOLD_INVITATION_ACCEPTED,
    HOUSEHOLD_INVITATION_REVOKED,

    // consent
    CONSENT_GRANTED,
    CONSENT_WITHDRAWN,
    CONSENT_DENIED_AT_PROCESSING,

    // child data — the strictest category
    CHILD_PROFILE_CREATED,
    CHILD_PROFILE_UPDATED,
    CHILD_DATA_ACCESSED,
    CHILD_DATA_ACCESS_DENIED,
    CHILD_DATA_DELETED,
    GUARDIAN_LINK_CREATED,
    GUARDIAN_LINK_REVOKED,

    // profile
    PROFILE_CREATED,
    PROFILE_UPDATED,

    // wellness observations (D-038). Separate from the child-data actions above on
    // purpose: filing an adult's blood-pressure read under CHILD_DATA_ACCESSED would
    // make the strictest audit category unusable for the thing it exists to track.
    HEALTH_DATA_ACCESSED,
    HEALTH_DATA_ACCESS_DENIED,
    HEALTH_METRIC_RECORDED,
    HEALTH_METRIC_REFUSED_FOR_AGE,
    HEALTH_VISIBILITY_GRANTED,
    HEALTH_VISIBILITY_REVOKED,

    // access (W1, D-044/D-045). A grant or revocation of VIEW/MANAGE on one scope. The two
    // HEALTH_VISIBILITY_* values above remain for events recorded before W1.
    ACCESS_GRANTED,
    ACCESS_REVOKED,

    // nutrition (D-039). A meal apportioned to a child is child data, so these sit
    // alongside CHILD_DATA_ACCESSED rather than replacing it.
    MEAL_LOGGED,
    MEAL_UPDATED,
    MEAL_DELETED,
    /** An adult added their own share of a household meal to their own log (D-041). */
    MEAL_PORTION_CONFIRMED,

    // goals and habits (Phase 2). No action exists for a streak, badge or ranking,
    // because no such capability exists (D-036, AC-3.7).
    GOAL_CREATED,
    GOAL_ENDED,
    HABIT_CREATED,
    HABIT_UPDATED,
    HABIT_ARCHIVED,
    HABIT_REACTIVATED,
    HABIT_COMPLETION_RECORDED,
    HABIT_COMPLETION_REMOVED,

    // catalogue
    PRODUCT_VERSION_PUBLISHED,
    PRODUCT_VERSION_SUPERSEDED,
    PRODUCT_CORRECTION_SUBMITTED,
    PRODUCT_CORRECTION_RESOLVED,

    // scoring
    RULE_SET_ACTIVATED,
    RULE_SET_RETIRED,

    // data rights
    DATA_EXPORTED,
    DATA_ERASURE_COMPLETED
}

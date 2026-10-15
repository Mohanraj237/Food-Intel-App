package com.familyfood.access.domain;

/**
 * How an actor stands towards the person whose data is in question (D-044, D-045).
 *
 * <p>Note what is not here: owner, head of household, parent-of-an-adult, spouse. Household
 * role and family relationship grant nothing on their own — the failure this model is built
 * against is a family member, not a stranger (D-038).
 */
public enum Relationship {
    /** The actor is the subject. Sees and manages everything of their own. */
    SELF,
    /** A live guardian of a child subject. Child data is reachable only this way. */
    GUARDIAN,
    /** Holds at least one MANAGE grant from the subject (D-045). */
    CAREGIVER,
    /** Holds only VIEW grants from the subject. */
    SHARED,
    /** Everyone else, including other household members. Sees nothing. */
    NONE
}

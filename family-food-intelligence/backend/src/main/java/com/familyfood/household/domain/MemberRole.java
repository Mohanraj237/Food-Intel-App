package com.familyfood.household.domain;

/**
 * A member's standing within a household.
 *
 * <p>{@link #OWNER} and {@link #ADULT} are both account holders; {@link #DEPENDENT} is
 * always a {@link MemberType#CHILD}. An account holder can never be {@code DEPENDENT} —
 * that equivalence is what keeps the guardian-controlled pathway from being pointed at an
 * adult to route around D-002.
 */
public enum MemberRole {
    OWNER,
    ADULT,
    DEPENDENT
}

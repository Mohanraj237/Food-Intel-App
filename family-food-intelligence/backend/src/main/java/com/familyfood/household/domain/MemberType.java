package com.familyfood.household.domain;

/**
 * Whether a {@link HouseholdMember} has an account of their own.
 *
 * <p>Tied to {@link MemberRole} by the database CHECK constraints
 * {@code ck_member_child_has_no_account} and {@code ck_member_child_is_dependent}: a
 * {@link #CHILD} member always has {@code userAccountId == null} and
 * {@code role == DEPENDENT}, and the reverse holds too. That equivalence is enforced in
 * both places on purpose (defence in depth) — see {@link HouseholdMember}.
 */
public enum MemberType {
    ADULT,
    CHILD
}

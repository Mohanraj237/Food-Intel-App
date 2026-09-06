package com.familyfood.household.domain;

/** Lifecycle of a {@link HouseholdInvitation}. Every value but {@link #PENDING} is terminal. */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED
}

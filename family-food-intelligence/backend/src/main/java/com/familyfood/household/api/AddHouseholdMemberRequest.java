package com.familyfood.household.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A single endpoint accepts either shape, discriminated by {@link #memberType}.
 *
 * <p>For {@code ADULT}: {@code email} is required (D-002 — this creates only an
 * invitation, never a member row). For {@code CHILD}: {@code displayLabel},
 * {@code relationship} and {@code authorityBasis} are required; {@code email} is not
 * accepted at all, since a child has no account and no contact address is ever collected
 * for one. Field-level requiredness is validated in {@code HouseholdService} because it
 * depends on the discriminator, not on any single field in isolation.
 */
public record AddHouseholdMemberRequest(

        @NotNull
        MemberKind memberType,

        @Size(max = 254)
        String email,

        @Size(max = 60)
        String displayLabel,

        @Size(max = 30)
        String relationship,

        @Size(max = 200)
        String authorityBasis
) {
    public enum MemberKind { ADULT, CHILD }
}

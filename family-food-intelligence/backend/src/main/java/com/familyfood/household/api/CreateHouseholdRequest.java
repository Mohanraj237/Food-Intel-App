package com.familyfood.household.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param ownerDisplayLabel the caller's own chosen label within the household. Household
 *                          membership carries no name field — this is the only identifying
 *                          text an account holder provides about themselves.
 */
public record CreateHouseholdRequest(

        @NotBlank
        @Size(max = 80)
        String name,

        @NotBlank
        @Size(max = 60)
        String ownerDisplayLabel
) {}

package com.familyfood.profile.api;

import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.DietaryPreference;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.profile.domain.Sex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * The full desired state of a profile version. This is a replace, not a patch: every PUT
 * writes a brand-new version carrying exactly the fields supplied here (D-014 applied to
 * people — see {@code V3__household_consent_profile.sql}).
 *
 * <p>{@code goals} and {@code allergens} are typed as closed enums, so a value outside the
 * vocabulary fails JSON binding before this controller is even reached (D-017) — there is
 * no code path here that could accept a free-text goal.
 */
public record ProfileRequest(
        @NotNull AgeBand ageBand,
        Sex sex,
        DietaryPreference dietaryPreference,
        Set<GoalCode> goals,
        Set<AllergenCode> allergens,
        Set<@NotBlank @Size(max = 60) String> conditions) {

    public ProfileRequest {
        goals = goals == null ? Set.of() : Set.copyOf(goals);
        allergens = allergens == null ? Set.of() : Set.copyOf(allergens);
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    }
}

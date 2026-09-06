package com.familyfood.profile.application;

import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.DietaryPreference;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.profile.domain.PersonProfile;
import com.familyfood.profile.domain.Sex;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A read-only snapshot of the current version of a profile, materialised inside the
 * service's transaction so that the API layer never touches a JPA entity or a lazy
 * collection (open-in-view is disabled — see {@code application.yml}).
 */
public record ProfileView(
        UUID id,
        UUID householdMemberId,
        int version,
        AgeBand ageBand,
        Sex sex,
        DietaryPreference dietaryPreference,
        Set<GoalCode> goals,
        Set<AllergenCode> allergens,
        Set<String> conditions,
        Instant createdAt,
        UUID createdByUserId) {

    public static ProfileView from(PersonProfile profile) {
        return new ProfileView(
                profile.getId(),
                profile.getHouseholdMemberId(),
                profile.getVersion(),
                profile.getAgeBand(),
                profile.getSex(),
                profile.getDietaryPreference(),
                profile.getGoals(),
                profile.getAllergens(),
                profile.getConditions(),
                profile.getCreatedAt(),
                profile.getCreatedByUserId());
    }
}

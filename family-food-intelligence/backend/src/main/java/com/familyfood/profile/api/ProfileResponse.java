package com.familyfood.profile.api;

import com.familyfood.profile.application.ProfileView;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.DietaryPreference;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.profile.domain.Sex;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        UUID householdMemberId,
        int version,
        AgeBand ageBand,
        Sex sex,
        DietaryPreference dietaryPreference,
        Set<GoalCode> goals,
        Set<AllergenCode> allergens,
        Set<String> conditions,
        Instant createdAt) {

    public static ProfileResponse from(ProfileView view) {
        return new ProfileResponse(
                view.id(),
                view.householdMemberId(),
                view.version(),
                view.ageBand(),
                view.sex(),
                view.dietaryPreference(),
                view.goals(),
                view.allergens(),
                view.conditions(),
                view.createdAt());
    }
}

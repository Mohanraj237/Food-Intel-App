package com.familyfood.profile.domain;

/**
 * The entire goal vocabulary (D-017). There is no weight goal, no body-composition goal
 * and no calorie target here, for any profile, and — being a Java enum rather than a
 * free-text field — an unrepresented goal cannot be introduced by a client request, a
 * DTO change, or a typo. This is the application-layer mirror of the
 * {@code profile_goal.goal_code} CHECK constraint in
 * {@code V3__household_consent_profile.sql}; the database constraint remains the
 * authority, this is the early, legible failure.
 */
public enum GoalCode {
    REDUCE_ADDED_SUGAR,
    REDUCE_SODIUM,
    REDUCE_SATURATED_FAT,
    INCREASE_FIBRE,
    INCREASE_PROTEIN,
    AVOID_TRANS_FAT,
    GENERAL_BALANCED_EATING
}

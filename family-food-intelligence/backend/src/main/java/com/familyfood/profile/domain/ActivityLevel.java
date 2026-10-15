package com.familyfood.profile.domain;

/**
 * How active a person says they usually are.
 *
 * <p>Self-reported context, and nothing more. It is shown back to the person and may one day
 * inform which suggestions are relevant, but no energy requirement, calorie target or deficit
 * is ever computed from it — that would need a reviewed standard and would be exactly the
 * restriction framing D-047 holds back. Mirrors the {@code ck_profile_activity_level} CHECK
 * added in V14.
 */
public enum ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE
}

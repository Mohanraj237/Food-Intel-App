package com.familyfood.wellness.domain;

import com.familyfood.access.domain.AccessScope;

/**
 * The grouping a metric belongs to, and the unit of sharing.
 *
 * <p>Visibility is granted per category rather than per metric (D-038) because that is the
 * decision a person can actually make: "my family can see my activity" is a sentence someone
 * means, whereas "my family can see my active minutes but not my step count" is a settings
 * screen nobody finishes. Categories are also the axis insights aggregate over.
 */
public enum MetricCategory {
    BODY,
    ACTIVITY,
    SLEEP,
    HYDRATION,
    NUTRITION,
    CLINICAL,
    WELLBEING;

    /**
     * The access scope that governs this category (D-044). Spelled identically on purpose; a
     * test asserts every category has one, so the two enums cannot drift apart.
     */
    public AccessScope scope() {
        return AccessScope.valueOf(name());
    }

    /** The category a scope names, if it names one — the reverse of {@link #scope()}. */
    public static java.util.Optional<MetricCategory> of(AccessScope scope) {
        for (MetricCategory category : values()) {
            if (category.name().equals(scope.name())) {
                return java.util.Optional.of(category);
            }
        }
        return java.util.Optional.empty();
    }
}

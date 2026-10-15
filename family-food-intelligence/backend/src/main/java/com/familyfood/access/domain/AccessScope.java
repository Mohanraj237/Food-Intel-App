package com.familyfood.access.domain;

/**
 * What a sharing grant covers (D-044).
 *
 * <p>The first seven are the metric categories of {@code wellness.domain.MetricCategory},
 * spelled identically so that the mapping is a name lookup and a test can assert the two enums
 * never drift apart. They are not imported from wellness, because wellness depends on this
 * module and not the other way round.
 *
 * <p>{@code offered} is whether the product accepts a grant for the scope today. A scope whose
 * feature does not exist yet is declared — so the grant model does not change shape when the
 * feature lands — but not offered, because a grant nobody can use is a control that implies a
 * capability the product does not have.
 */
public enum AccessScope {

    // -------------------------------------------------- metric categories (observations, goals)
    BODY(Kind.TRACKING, true),
    ACTIVITY(Kind.TRACKING, true),
    SLEEP(Kind.TRACKING, true),
    HYDRATION(Kind.TRACKING, true),
    NUTRITION(Kind.TRACKING, true),
    CLINICAL(Kind.TRACKING, true),
    WELLBEING(Kind.TRACKING, true),

    // --------------------------------------------------------------------------- habits
    /** A scope of its own because a habit's name can say more than a number does. */
    HABITS(Kind.TRACKING, true),

    // ---------------------------------------------------------------------- food profile
    /**
     * Allergens, dietary preference and food goals — what a caregiver needs to evaluate a
     * product for someone. Never self-declared health conditions. Not offered until household
     * fit reads it (W2): until then a grant would open nothing.
     */
    FOOD_PROFILE(Kind.FOOD, false),

    // ------------------------------------------------------------- health management (W4 ⚖️)
    MEDICATIONS(Kind.HEALTH_RECORDS, false),
    APPOINTMENTS(Kind.HEALTH_RECORDS, false),
    RECORDS(Kind.HEALTH_RECORDS, false);

    /** Which consent purpose family governs disclosure of data in this scope (D-048). */
    public enum Kind {
        /** HEALTH_TRACKING for an adult, CHILD_WELLNESS_TRACKING for a child. */
        TRACKING,
        /** PERSONAL_FIT / CHILD_FOOD_SUITABILITY, checked where the food profile is used. */
        FOOD,
        /** HEALTH_RECORDS / CHILD_HEALTH_RECORDS, arriving with W4 (D-049). */
        HEALTH_RECORDS
    }

    private final Kind kind;
    private final boolean offered;

    AccessScope(Kind kind, boolean offered) {
        this.kind = kind;
        this.offered = offered;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether a grant for this scope is accepted today. */
    public boolean isOffered() {
        return offered;
    }

    public boolean isTracking() {
        return kind == Kind.TRACKING;
    }
}

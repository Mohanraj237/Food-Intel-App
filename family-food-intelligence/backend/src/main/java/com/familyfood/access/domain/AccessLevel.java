package com.familyfood.access.domain;

/**
 * How much a grant allows within its scope (D-044).
 *
 * <p>Two levels, deliberately. A finer ladder ("may log but not edit", "may see this week but
 * not last month") is the kind of control nobody can predict the effect of, and a sharing
 * setting a person cannot predict is one they cannot meaningfully consent to.
 */
public enum AccessLevel {

    /** Read what is recorded in the scope. */
    VIEW,

    /**
     * Record, log or act on the subject's behalf in the scope — what makes someone a caregiver
     * (D-045). Implies {@link #VIEW}: acting on data you cannot see would be acting blind.
     */
    MANAGE;

    /** Whether holding this level is enough for an action that requires {@code required}. */
    public boolean satisfies(AccessLevel required) {
        return this == MANAGE || required == VIEW;
    }

    public static AccessLevel stronger(AccessLevel a, AccessLevel b) {
        if (a == null) return b;
        if (b == null) return a;
        return a == MANAGE || b == MANAGE ? MANAGE : VIEW;
    }
}

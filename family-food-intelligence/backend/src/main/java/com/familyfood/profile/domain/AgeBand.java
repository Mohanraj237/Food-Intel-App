package com.familyfood.profile.domain;

/**
 * The closed set of age bands a profile may declare.
 *
 * <p>There is deliberately no date-of-birth field anywhere in this module or its schema
 * (see {@code V3__household_consent_profile.sql}, {@code person_profile.age_band}). Adding
 * one — for an adult or a child — is a schema change with a decision-log entry attached
 * (CH-006), not something to be reintroduced quietly through a DTO.
 *
 * <p><b>Whether a band is a child band is stated per constant, not inferred from its name.</b>
 * Every age gate in the product — the metric gate (D-036), the child goal rule (D-047), the
 * experience tier (D-046) — reads {@link #isChild()}. A prefix test on the name ("C…") would
 * let a rename silently reclassify a child as an adult and open every adult-only metric to
 * them; a constructor argument cannot drift that way.
 */
public enum AgeBand {
    A18_30(false),
    A31_45(false),
    A46_60(false),
    A60_PLUS(false),
    C1_3(true),
    C4_6(true),
    C7_9(true),
    C10_12(true),
    C13_17(true);

    private final boolean child;

    AgeBand(boolean child) {
        this.child = child;
    }

    /** True for every band a minor may hold. The single definition of "child band". */
    public boolean isChild() {
        return child;
    }

    /** The band an older-adult experience is derived from (D-046). Never a restriction. */
    public boolean isOlderAdult() {
        return this == A60_PLUS;
    }
}

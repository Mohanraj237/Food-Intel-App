package com.familyfood.profile.domain;

/**
 * The closed set of age bands a profile may declare.
 *
 * <p>There is deliberately no date-of-birth field anywhere in this module or its schema
 * (see {@code V3__household_consent_profile.sql}, {@code person_profile.age_band}). Adding
 * one — for an adult or a child — is a schema change with a decision-log entry attached
 * (CH-006), not something to be reintroduced quietly through a DTO.
 */
public enum AgeBand {
    A18_30,
    A31_45,
    A46_60,
    A60_PLUS,
    C1_3,
    C4_6,
    C7_9,
    C10_12,
    C13_17
}

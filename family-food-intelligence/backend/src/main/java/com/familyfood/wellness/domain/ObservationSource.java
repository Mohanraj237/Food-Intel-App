package com.familyfood.wellness.domain;

/** Where an observation came from. Mirrors the {@code source} CHECK in V12. */
public enum ObservationSource {
    /** Typed in by a person. */
    MANUAL,
    /** Computed from other observations of the same subject (e.g. BMI from weight + height). */
    DERIVED,
    /** Brought in from an external source. No importer exists yet; the value is reserved so
     *  that adding one is not a migration. */
    IMPORT
}

package com.familyfood.consent.domain;

/**
 * How a consent was obtained.
 *
 * <p>{@link #GUARDIAN_ATTESTATION} is <strong>REQUIRES_LEGAL_SIGNOFF</strong> (D-001).
 * It records that a guardian asserted their authority, together with the policy version
 * they saw and an audit trail of the act. That is evidence, and it is honest evidence —
 * but it is <em>not</em> the verifiable parental consent mechanism the DPDP Rules 2025
 * require, and it must never be described as such in this codebase, in the API, in the
 * UI, or in any document. Shipping real child data on this mechanism is a documented
 * release blocker until privacy counsel signs off on a compliant mechanism.
 */
public enum ConsentMechanism {

    /** An adult granting for themselves, on their own device, in their own session. */
    SELF_ATTESTATION,

    /**
     * A guardian granting for a linked child.
     *
     * <p>REQUIRES_LEGAL_SIGNOFF — see the class Javadoc. Provisional pending privacy
     * counsel; not represented anywhere as DPDP-verifiable consent (D-001).
     */
    GUARDIAN_ATTESTATION
}

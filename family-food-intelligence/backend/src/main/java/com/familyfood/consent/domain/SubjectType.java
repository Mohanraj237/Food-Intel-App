package com.familyfood.consent.domain;

/**
 * Who the consent is about.
 *
 * <p>Only two values exist, and the gap between them is D-002: an adult is always
 * {@link #ADULT_SELF}, because no adult may consent on behalf of another adult — not a
 * spouse, not an elderly parent, not a housemate. {@link #CHILD} is the only proxy case
 * the product recognises, and it is guarded by {@link ConsentMechanism#GUARDIAN_ATTESTATION}
 * plus a hard processing gate (D-001).
 *
 * <p>There is deliberately no {@code ADULT_PROXY} value. Adding one would make the
 * unlawful case representable, and the whole point of the enum is that it is not.
 */
public enum SubjectType {

    /** An adult acting for themselves. The only lawful adult case (D-002). */
    ADULT_SELF,

    /** A child with no account of their own, acted for by a linked guardian (D-001). */
    CHILD
}

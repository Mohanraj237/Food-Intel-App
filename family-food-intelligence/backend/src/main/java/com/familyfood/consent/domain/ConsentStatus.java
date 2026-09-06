package com.familyfood.consent.domain;

/**
 * The lifecycle of a single consent record.
 *
 * <p>Only {@link #ACTIVE} permits processing. Everything else — including the absence of
 * any record at all — denies it. The gate is written so that the default answer is "no":
 * a new status added here without a corresponding change to the gate keeps denying rather
 * than starts permitting.
 */
public enum ConsentStatus {

    /** Recorded but not yet effective. Never permits processing. */
    PENDING,

    /** The one value that permits processing. */
    ACTIVE,

    /** Withdrawn by the data principal or their guardian. Processing stops immediately. */
    WITHDRAWN,

    /** Past its expiry, or superseded by a policy version the person has not seen. */
    EXPIRED
}

package com.familyfood.experience.domain;

/**
 * Which experience a member gets (D-046). Derived on the server from their member type and the
 * age band on their current profile — there is no date of birth anywhere (CH-006) — and
 * returned to clients, which render it and never infer it.
 */
public enum ExperienceTier {

    /**
     * A child: a CHILD member, or any profile holding a child band. Not a smaller adult —
     * capabilities are removed (no body or clinical metrics, no nutrition targets, no engagement
     * mechanics, no notifications to the child) and the copy is encouraging, never evaluative.
     */
    CHILD,

    /** An adult between 18 and 60. The full individual experience. */
    ADULT,

    /**
     * An adult of 60 or over. Every adult capability, none removed on account of age (D-045);
     * what changes is emphasis — reminders, medicines, appointments and caregiver support first.
     */
    OLDER_ADULT,

    /**
     * An adult with no profile yet. Age is unknown, and an unknown age is treated as possibly a
     * child by every gate, so tracking waits for the profile.
     */
    UNKNOWN
}

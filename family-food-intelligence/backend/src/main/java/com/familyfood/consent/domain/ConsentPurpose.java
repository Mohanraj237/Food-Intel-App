package com.familyfood.consent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A purpose a person can consent to, as reference data seeded by V1.
 *
 * <p>Purposes are granular and named in plain terms because consent to "the app" is not
 * consent to anything in particular. {@code requiresVerifiable} marks the purposes that
 * concern a child and therefore cannot be satisfied by self-attestation.
 */
@Entity
@Table(name = "consent_purpose")
public class ConsentPurpose {

    /** The stable code used in the API and in {@code consent_record.purpose_code}. */
    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    /** True for child purposes: a guardian mechanism is mandatory (D-001). */
    @Column(name = "requires_verifiable", nullable = false)
    private boolean requiresVerifiable;

    /** True when the app cannot function without it, so it is not separately refusable. */
    @Column(name = "is_essential", nullable = false)
    private boolean essential;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ConsentPurpose() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequiresVerifiable() {
        return requiresVerifiable;
    }

    public boolean isEssential() {
        return essential;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * The mechanism this purpose demands for the given subject.
     *
     * <p>Derived rather than chosen by the caller: letting a caller nominate the
     * mechanism would let a client downgrade a child grant to self-attestation.
     */
    public ConsentMechanism mechanismFor(SubjectType subjectType) {
        return subjectType == SubjectType.CHILD
                ? ConsentMechanism.GUARDIAN_ATTESTATION
                : ConsentMechanism.SELF_ATTESTATION;
    }
}

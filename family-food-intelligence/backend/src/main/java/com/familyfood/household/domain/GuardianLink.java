package com.familyfood.household.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A guardian's own attestation of authority over a child member (Pack 06 CH-001).
 *
 * <p>{@code authorityBasis} is evidence for a later legal review, not a verification: the
 * verifiable-consent mechanism required by the DPDP Rules is PROVISIONAL and blocked on
 * privacy counsel (D-001). Nothing here or anywhere else in the codebase checks the claim
 * against an external record.
 */
@Entity
@Table(name = "guardian_link")
public class GuardianLink {

    @Id
    private UUID id;

    @Column(name = "child_member_id", nullable = false)
    private UUID childMemberId;

    @Column(name = "guardian_member_id", nullable = false)
    private UUID guardianMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false)
    private GuardianRelationship relationship;

    @Column(name = "authority_basis", nullable = false)
    private String authorityBasis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected GuardianLink() {
        // JPA
    }

    /**
     * Mirrors {@code ck_guardian_not_self} and the authority-basis length CHECK in code as
     * well as in the database (defence in depth).
     */
    public static GuardianLink create(UUID childMemberId, UUID guardianMemberId,
                                      GuardianRelationship relationship, String authorityBasis,
                                      Instant now) {
        if (childMemberId.equals(guardianMemberId)) {
            throw new IllegalArgumentException("a member cannot be their own guardian");
        }
        String trimmed = authorityBasis == null ? "" : authorityBasis.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw new IllegalArgumentException("authorityBasis must be between 1 and 200 characters");
        }
        GuardianLink link = new GuardianLink();
        link.id = UUID.randomUUID();
        link.childMemberId = childMemberId;
        link.guardianMemberId = guardianMemberId;
        link.relationship = relationship;
        link.authorityBasis = trimmed;
        link.createdAt = now;
        return link;
    }

    /** Idempotent: revoking twice is a no-op. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildMemberId() {
        return childMemberId;
    }

    public UUID getGuardianMemberId() {
        return guardianMemberId;
    }

    public GuardianRelationship getRelationship() {
        return relationship;
    }

    public String getAuthorityBasis() {
        return authorityBasis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

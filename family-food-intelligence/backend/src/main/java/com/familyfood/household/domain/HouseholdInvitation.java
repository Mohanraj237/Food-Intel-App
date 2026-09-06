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
 * An invitation for an adult to join a household (D-002).
 *
 * <p>Adding an adult produces a row here and nothing else — no member record, no profile,
 * no consent record exists for them until they accept with their own account.
 *
 * <p>{@code tokenHash} stores SHA-256 of an opaque 256-bit token; the raw token is handed
 * to the inviter exactly once, by the service, and is never persisted.
 *
 * <p>{@code email} is the only plaintext contact address retained anywhere, and only for as
 * long as the invitation is {@link InvitationStatus#PENDING}. Reaching any terminal status
 * clears it (data minimisation) — enforced here, mirroring the database CHECK
 * {@code ck_invitation_pending_has_email}.
 */
@Entity
@Table(name = "household_invitation")
public class HouseholdInvitation {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "invited_by_member_id", nullable = false)
    private UUID invitedByMemberId;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HouseholdInvitation() {
        // JPA
    }

    public static HouseholdInvitation create(UUID householdId, UUID invitedByMemberId,
                                             String normalisedEmail, String tokenHash,
                                             Instant now, Instant expiresAt) {
        if (normalisedEmail == null || normalisedEmail.isBlank()) {
            // Mirrors ck_invitation_pending_has_email: a PENDING row must have an email.
            throw new IllegalArgumentException("a pending invitation requires an email");
        }
        HouseholdInvitation invitation = new HouseholdInvitation();
        invitation.id = UUID.randomUUID();
        invitation.householdId = householdId;
        invitation.invitedByMemberId = invitedByMemberId;
        invitation.email = normalisedEmail;
        invitation.tokenHash = tokenHash;
        invitation.status = InvitationStatus.PENDING;
        invitation.expiresAt = expiresAt;
        invitation.createdAt = now;
        return invitation;
    }

    /** True only while this invitation may still be accepted. */
    public boolean isUsable(Instant now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    /**
     * Accepts the invitation. The caller must have already verified {@link #isUsable}
     * against the same {@code now}.
     */
    public void accept(UUID acceptedByUserId, Instant now) {
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = now;
        this.acceptedByUserId = acceptedByUserId;
        this.email = null;
    }

    /** Revokes a still-pending invitation. Not idempotent: revoking twice is the caller's bug. */
    public void revoke() {
        this.status = InvitationStatus.REVOKED;
        this.email = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getInvitedByMemberId() {
        return invitedByMemberId;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public UUID getAcceptedByUserId() {
        return acceptedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

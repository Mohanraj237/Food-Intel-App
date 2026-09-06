package com.familyfood.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Reserved for phone/OTP, which is BLOCKED on an SMS provider (D-008). */
    @Column(name = "phone_hash")
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Set when erasure is requested; the row is hard-deleted after the grace period. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserAccount() {
        // JPA
    }

    public static UserAccount create(String normalisedEmail, String passwordHash, Instant now) {
        UserAccount account = new UserAccount();
        account.id = UUID.randomUUID();
        account.email = normalisedEmail;
        account.passwordHash = passwordHash;
        account.status = AccountStatus.ACTIVE;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** True only when the account may authenticate right now. */
    public boolean isUsable() {
        return status == AccountStatus.ACTIVE && deletedAt == null;
    }

    public void changePasswordHash(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.updatedAt = now;
    }

    public void markDeletionRequested(Instant now) {
        this.status = AccountStatus.DELETION_REQUESTED;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /**
     * The scheduled hard-erasure step, once the grace period from {@link
     * #markDeletionRequested} has elapsed. The row is never removed — {@code
     * consent_record.granted_by_user_id}, {@code household_member.user_account_id} and
     * {@code audit_event.actor_user_id} may still reference it with no {@code ON DELETE}
     * action — but every identifying field is overwritten so the id resolves to no real
     * person any more. {@code erasedEmail} must be unique and lower-case (mirrors the
     * table's own CHECK); the caller passes one derived from this account's own id so
     * uniqueness never collides with a real address, and so the original email is freed
     * for someone else to register.
     *
     * <p>Idempotent: erasing twice leaves the account exactly as erased as it already was.
     */
    public void eraseIdentity(String erasedEmail, String unusablePasswordHash, Instant now) {
        if (status == AccountStatus.ERASED) {
            return;
        }
        this.email = erasedEmail;
        this.emailVerifiedAt = null;
        this.passwordHash = unusablePasswordHash;
        this.phoneHash = null;
        this.status = AccountStatus.ERASED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public enum AccountStatus {
        ACTIVE, SUSPENDED, DELETION_REQUESTED, ERASED
    }
}

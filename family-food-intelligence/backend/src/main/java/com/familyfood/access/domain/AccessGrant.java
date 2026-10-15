package com.familyfood.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One person letting one other household member see — or act within — one scope of their
 * health data (D-044). The successor of D-038's {@code MetricVisibilityGrant}; the table was
 * renamed in place (V14), so every grant made under D-038 is still here as a VIEW grant.
 *
 * <p>Default-closed: the absence of a live row is a denial. There is no "share with household"
 * shortcut and no role that implies access.
 *
 * <p>Revoking sets {@link #revokedAt} rather than deleting the row, so "who could see my
 * glucose readings last March" and "since when could my son manage my medicines" remain
 * answerable. A partial unique index keeps at most one live grant per (subject, audience,
 * scope); changing the level revokes and re-grants rather than mutating, for the same reason.
 */
@Entity
@Table(name = "health_access_grant")
public class AccessGrant {

    @Id
    private UUID id;

    @Column(name = "subject_member_id", nullable = false)
    private UUID subjectMemberId;

    @Column(name = "audience_member_id", nullable = false)
    private UUID audienceMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private AccessScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private AccessLevel level;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by_user_id", nullable = false)
    private UUID grantedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AccessGrant() {
        // JPA
    }

    public static AccessGrant grant(UUID subjectMemberId,
                                    UUID audienceMemberId,
                                    AccessScope scope,
                                    AccessLevel level,
                                    UUID grantedByUserId,
                                    Instant now) {
        if (subjectMemberId.equals(audienceMemberId)) {
            // Mirrors ck_access_grant_not_self: the subject always sees their own data.
            throw new IllegalArgumentException("a subject cannot grant access to themselves");
        }
        AccessGrant grant = new AccessGrant();
        grant.id = UUID.randomUUID();
        grant.subjectMemberId = subjectMemberId;
        grant.audienceMemberId = audienceMemberId;
        grant.scope = scope;
        grant.level = level;
        grant.grantedAt = now;
        grant.grantedByUserId = grantedByUserId;
        return grant;
    }

    /** Idempotent: revoking an already-revoked grant keeps the original timestamp, so the
     *  record of when access actually ended is not overwritten by a repeated request. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public boolean isLive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubjectMemberId() {
        return subjectMemberId;
    }

    public UUID getAudienceMemberId() {
        return audienceMemberId;
    }

    public AccessScope getScope() {
        return scope;
    }

    public AccessLevel getLevel() {
        return level;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedByUserId() {
        return grantedByUserId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

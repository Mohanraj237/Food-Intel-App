package com.familyfood.wellness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One person letting one other person see one category of their health data (D-038).
 *
 * <p>Default-closed: the absence of a row is a denial. There is no "share with household"
 * shortcut and no role that implies access — {@code OWNER} is not a health-data role. The
 * failure mode this is designed against is not a stranger but a family member, who has both
 * the motive and the proximity.
 *
 * <p>Revoking sets {@link #revokedAt} rather than deleting the row, so that "who could see
 * my glucose readings last March" remains answerable. A partial unique index keeps at most
 * one live grant per (subject, audience, category).
 */
@Entity
@Table(name = "metric_visibility")
public class MetricVisibilityGrant {

    @Id
    private UUID id;

    @Column(name = "subject_member_id", nullable = false)
    private UUID subjectMemberId;

    @Column(name = "audience_member_id", nullable = false)
    private UUID audienceMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_category", nullable = false)
    private MetricCategory metricCategory;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by_user_id", nullable = false)
    private UUID grantedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MetricVisibilityGrant() {
        // JPA
    }

    public static MetricVisibilityGrant grant(UUID subjectMemberId,
                                              UUID audienceMemberId,
                                              MetricCategory category,
                                              UUID grantedByUserId,
                                              Instant now) {
        MetricVisibilityGrant grant = new MetricVisibilityGrant();
        grant.id = UUID.randomUUID();
        grant.subjectMemberId = subjectMemberId;
        grant.audienceMemberId = audienceMemberId;
        grant.metricCategory = category;
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

    public MetricCategory getMetricCategory() {
        return metricCategory;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

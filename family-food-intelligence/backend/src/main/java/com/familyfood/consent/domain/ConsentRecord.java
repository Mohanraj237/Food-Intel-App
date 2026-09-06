package com.familyfood.consent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One entry in the consent ledger.
 *
 * <p>The ledger is the single authority the {@code ConsentGate} consults, and it is
 * append-and-amend rather than delete: withdrawing moves a row to
 * {@link ConsentStatus#WITHDRAWN} and stamps {@code withdrawnAt}. The row survives
 * erasure of the person it describes, because the evidence that consent was given, and
 * later withdrawn, is exactly what a regulator or a court would ask for (AC-9.3).
 *
 * <p>{@code evidence} holds the mechanism, the hash of the policy text, a hashed user
 * agent and the timestamps. It never holds an email address, a name, a health value or a
 * raw IP. It is also on the logging deny-list, so it cannot reach a log line by accident.
 */
@Entity
@Table(name = "consent_record")
public class ConsentRecord {

    @Id
    private UUID id;

    @Column(name = "subject_member_id", nullable = false)
    private UUID subjectMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "granted_by_user_id", nullable = false)
    private UUID grantedByUserId;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConsentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "mechanism", nullable = false)
    private ConsentMechanism mechanism;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidence = Map.of();

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsentRecord() {
        // JPA
    }

    /**
     * Records a grant as immediately {@code ACTIVE}.
     *
     * <p>The mechanism is derived from the subject type by the caller rather than
     * accepted from a client, so a child grant cannot be downgraded to self-attestation
     * by a crafted request.
     */
    public static ConsentRecord grant(UUID subjectMemberId,
                                      SubjectType subjectType,
                                      UUID grantedByUserId,
                                      String purposeCode,
                                      UUID policyVersionId,
                                      ConsentMechanism mechanism,
                                      Map<String, Object> evidence,
                                      Instant now) {
        if (subjectType == SubjectType.CHILD
                && mechanism != ConsentMechanism.GUARDIAN_ATTESTATION) {
            // Mirrors ck_consent_child_mechanism. Kept in both places on purpose: the
            // database constraint is the guarantee, this is the early, legible failure.
            throw new IllegalArgumentException(
                    "a CHILD subject requires GUARDIAN_ATTESTATION");
        }
        ConsentRecord record = new ConsentRecord();
        record.id = UUID.randomUUID();
        record.subjectMemberId = subjectMemberId;
        record.subjectType = subjectType;
        record.grantedByUserId = grantedByUserId;
        record.purposeCode = purposeCode;
        record.policyVersionId = policyVersionId;
        record.status = ConsentStatus.ACTIVE;
        record.mechanism = mechanism;
        record.evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        record.grantedAt = now;
        record.createdAt = now;
        return record;
    }

    /**
     * Withdraws this consent. Idempotent: withdrawing twice is a no-op rather than an
     * error, because a user pressing a button twice must not see a failure for having
     * got what they asked for.
     */
    public void withdraw(Instant now) {
        if (status == ConsentStatus.WITHDRAWN) {
            return;
        }
        this.status = ConsentStatus.WITHDRAWN;
        this.withdrawnAt = now;
    }

    /** True only when this record permits processing right now. */
    public boolean permitsProcessing(Instant now) {
        return status == ConsentStatus.ACTIVE
                && grantedAt != null
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubjectMemberId() {
        return subjectMemberId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public UUID getGrantedByUserId() {
        return grantedByUserId;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public UUID getPolicyVersionId() {
        return policyVersionId;
    }

    public ConsentStatus getStatus() {
        return status;
    }

    public ConsentMechanism getMechanism() {
        return mechanism;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Evidence is exposed only for export; it is never logged and never rendered. */
    public Map<String, Object> getEvidence() {
        return evidence;
    }
}

package com.familyfood.consent.application;

import com.familyfood.consent.domain.ConsentMechanism;
import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.ConsentStatus;
import com.familyfood.consent.domain.SubjectType;

import java.time.Instant;
import java.util.UUID;

/**
 * A read-only ledger entry, deliberately without {@code evidence}.
 *
 * <p>Per {@link ConsentRecord#getEvidence()}'s own Javadoc, evidence "is exposed only for
 * export; it is never logged and never rendered." This view has no field for it at all, so
 * the omission from an ordinary listing is structural rather than a serialization detail
 * someone could accidentally undo.
 */
public record ConsentRecordView(
        UUID id,
        UUID subjectMemberId,
        SubjectType subjectType,
        String purposeCode,
        UUID policyVersionId,
        ConsentStatus status,
        ConsentMechanism mechanism,
        Instant grantedAt,
        Instant withdrawnAt,
        Instant expiresAt,
        Instant createdAt) {

    public static ConsentRecordView from(ConsentRecord record) {
        return new ConsentRecordView(
                record.getId(),
                record.getSubjectMemberId(),
                record.getSubjectType(),
                record.getPurposeCode(),
                record.getPolicyVersionId(),
                record.getStatus(),
                record.getMechanism(),
                record.getGrantedAt(),
                record.getWithdrawnAt(),
                record.getExpiresAt(),
                record.getCreatedAt());
    }
}

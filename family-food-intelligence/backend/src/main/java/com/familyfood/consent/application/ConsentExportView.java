package com.familyfood.consent.application;

import com.familyfood.consent.domain.ConsentMechanism;
import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.ConsentStatus;
import com.familyfood.consent.domain.SubjectType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The export-only counterpart to {@link ConsentRecordView}: identical, plus {@code
 * evidence}. {@link ConsentRecord#getEvidence()} is documented as "exposed only for
 * export" — this is that export, and the first place in the codebase that honours it
 * (AC-9.2). Never used for an ordinary listing, never logged, never rendered as UI.
 */
public record ConsentExportView(
        UUID id,
        UUID subjectMemberId,
        SubjectType subjectType,
        String purposeCode,
        UUID policyVersionId,
        ConsentStatus status,
        ConsentMechanism mechanism,
        Map<String, Object> evidence,
        Instant grantedAt,
        Instant withdrawnAt,
        Instant expiresAt,
        Instant createdAt) {

    public static ConsentExportView from(ConsentRecord record) {
        return new ConsentExportView(
                record.getId(),
                record.getSubjectMemberId(),
                record.getSubjectType(),
                record.getPurposeCode(),
                record.getPolicyVersionId(),
                record.getStatus(),
                record.getMechanism(),
                record.getEvidence(),
                record.getGrantedAt(),
                record.getWithdrawnAt(),
                record.getExpiresAt(),
                record.getCreatedAt());
    }
}

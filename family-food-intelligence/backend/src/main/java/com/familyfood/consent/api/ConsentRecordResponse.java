package com.familyfood.consent.api;

import com.familyfood.consent.application.ConsentRecordView;
import com.familyfood.consent.domain.ConsentMechanism;
import com.familyfood.consent.domain.ConsentStatus;
import com.familyfood.consent.domain.SubjectType;

import java.time.Instant;
import java.util.UUID;

/** Never carries {@code evidence} — see {@link ConsentRecordView}. */
public record ConsentRecordResponse(
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

    public static ConsentRecordResponse from(ConsentRecordView view) {
        return new ConsentRecordResponse(
                view.id(),
                view.subjectMemberId(),
                view.subjectType(),
                view.purposeCode(),
                view.policyVersionId(),
                view.status(),
                view.mechanism(),
                view.grantedAt(),
                view.withdrawnAt(),
                view.expiresAt(),
                view.createdAt());
    }
}

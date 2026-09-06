package com.familyfood.consent.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.consent.domain.ConsentMechanism;
import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.SubjectType;
import com.familyfood.consent.infra.ConsentRecordRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ConsentGate} with {@link ConsentRecordRepository} mocked — no
 * Spring context, no database, exactly what the class needs since it is pure logic over one
 * repository call plus an audit call.
 */
class ConsentGateTest {

    private static final String PURPOSE = "PERSONAL_FIT";

    private final ConsentRecordRepository repository = mock(ConsentRecordRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

    private ConsentGate gate;

    private final UUID subjectMemberId = UUID.randomUUID();
    private final UUID grantedBy = UUID.randomUUID();
    private final UUID policyVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gate = new ConsentGate(repository, audit, clock);
    }

    @Test
    @DisplayName("permits when an ACTIVE, unexpired record exists")
    void permitsWhenActiveAndUnexpired() {
        ConsentRecord record = ConsentRecord.grant(subjectMemberId, SubjectType.ADULT_SELF,
                grantedBy, PURPOSE, policyVersionId, ConsentMechanism.SELF_ATTESTATION,
                Map.of(), clock.instant().minusSeconds(60));
        when(repository.findActive(subjectMemberId, PURPOSE)).thenReturn(Optional.of(record));

        assertThatCode(() -> gate.require(subjectMemberId, PURPOSE)).doesNotThrowAnyException();

        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("denies with CONSENT_REQUIRED when no record has ever existed")
    void deniesWhenNeverConsented() {
        when(repository.findActive(subjectMemberId, PURPOSE)).thenReturn(Optional.empty());
        when(repository.findHistory(subjectMemberId, PURPOSE)).thenReturn(List.of());

        assertThatThrownBy(() -> gate.require(subjectMemberId, PURPOSE))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);

        verify(audit).record(eq(AuditAction.CONSENT_DENIED_AT_PROCESSING), eq("ConsentRecord"),
                eq(subjectMemberId), any(), any(), eq(AuditOutcome.DENIED), any());
    }

    @Test
    @DisplayName("denies with CONSENT_WITHDRAWN when the most recent record was withdrawn")
    void deniesWhenWithdrawn() {
        ConsentRecord withdrawn = ConsentRecord.grant(subjectMemberId, SubjectType.ADULT_SELF,
                grantedBy, PURPOSE, policyVersionId, ConsentMechanism.SELF_ATTESTATION,
                Map.of(), clock.instant().minusSeconds(120));
        withdrawn.withdraw(clock.instant().minusSeconds(60));

        when(repository.findActive(subjectMemberId, PURPOSE)).thenReturn(Optional.empty());
        when(repository.findHistory(subjectMemberId, PURPOSE)).thenReturn(List.of(withdrawn));

        assertThatThrownBy(() -> gate.require(subjectMemberId, PURPOSE))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.CONSENT_WITHDRAWN);

        verify(audit).record(eq(AuditAction.CONSENT_DENIED_AT_PROCESSING), any(), any(),
                any(), any(), eq(AuditOutcome.DENIED), any());
    }

    @Test
    @DisplayName("denies with CONSENT_REQUIRED when the ACTIVE-status record has expired")
    void deniesWhenExpired() {
        ConsentRecord expired = ConsentRecord.grant(subjectMemberId, SubjectType.ADULT_SELF,
                grantedBy, PURPOSE, policyVersionId, ConsentMechanism.SELF_ATTESTATION,
                Map.of(), clock.instant().minusSeconds(1000));
        // ConsentRecord.grant has no expiresAt parameter; the field is set here only to
        // exercise permitsProcessing()'s expiry branch, mirroring how a time-limited grant
        // (not yet wired up by any caller) would eventually look once one exists.
        ReflectionTestUtils.setField(expired, "expiresAt", clock.instant().minusSeconds(1));

        when(repository.findActive(subjectMemberId, PURPOSE)).thenReturn(Optional.of(expired));
        when(repository.findHistory(subjectMemberId, PURPOSE)).thenReturn(List.of(expired));

        assertThatThrownBy(() -> gate.require(subjectMemberId, PURPOSE))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
    }
}

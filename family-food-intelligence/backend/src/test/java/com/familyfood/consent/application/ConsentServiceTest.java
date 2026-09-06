package com.familyfood.consent.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.consent.domain.ConsentMechanism;
import com.familyfood.consent.domain.ConsentPolicyVersion;
import com.familyfood.consent.domain.ConsentPurpose;
import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.ConsentStatus;
import com.familyfood.consent.domain.SubjectType;
import com.familyfood.consent.infra.ConsentPolicyVersionRepository;
import com.familyfood.consent.infra.ConsentPurposeRepository;
import com.familyfood.consent.infra.ConsentRecordRepository;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the D-001/D-002 authorization rule, the requires_verifiable/subject
 * mismatch rule, and the re-grant bookkeeping in {@link ConsentService}, with
 * {@link HouseholdAccess} and every repository mocked. The real-database, real-unique-index
 * proof that a re-grant leaves exactly one ACTIVE row is covered separately by
 * {@code ConsentServiceIT}.
 */
class ConsentServiceTest {

    private static final String PERSONAL_FIT = "PERSONAL_FIT";
    private static final String CHILD_FOOD_SUITABILITY = "CHILD_FOOD_SUITABILITY";

    private final ConsentRecordRepository consentRecords = mock(ConsentRecordRepository.class);
    private final ConsentPurposeRepository purposes = mock(ConsentPurposeRepository.class);
    private final ConsentPolicyVersionRepository policyVersions = mock(ConsentPolicyVersionRepository.class);
    private final HouseholdAccess householdAccess = mock(HouseholdAccess.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

    private ConsentService service;

    private final UUID householdId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID callerAccountId = UUID.randomUUID();
    private final UUID policyVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConsentService(consentRecords, purposes, policyVersions, householdAccess, audit, clock);
        when(consentRecords.save(any(ConsentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(consentRecords.saveAndFlush(any(ConsentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // Built via the protected no-arg constructor + direct field assignment, not
    // Mockito, because ConsentPurpose/ConsentPolicyVersion are plain JPA entities: real
    // instances exercise ConsentPurpose.mechanismFor's actual logic instead of restating
    // it as a stub, and sidestep any interaction between Mockito's mock maker and
    // Hibernate's field access on these entities.
    private ConsentPurpose purposeStub(String code, boolean requiresVerifiable) {
        ConsentPurpose purpose = newInstance(ConsentPurpose.class);
        ReflectionTestUtils.setField(purpose, "code", code);
        ReflectionTestUtils.setField(purpose, "name", code);
        ReflectionTestUtils.setField(purpose, "description", code);
        ReflectionTestUtils.setField(purpose, "requiresVerifiable", requiresVerifiable);
        ReflectionTestUtils.setField(purpose, "essential", false);
        ReflectionTestUtils.setField(purpose, "active", true);
        ReflectionTestUtils.setField(purpose, "sortOrder", 0);
        return purpose;
    }

    private ConsentPolicyVersion policyStub() {
        ConsentPolicyVersion version = newInstance(ConsentPolicyVersion.class);
        ReflectionTestUtils.setField(version, "id", policyVersionId);
        ReflectionTestUtils.setField(version, "purposeCode", PERSONAL_FIT);
        ReflectionTestUtils.setField(version, "version", "1.0");
        ReflectionTestUtils.setField(version, "documentUri", "/legal/test");
        ReflectionTestUtils.setField(version, "summary", "test policy");
        ReflectionTestUtils.setField(version, "effectiveFrom", clock.instant());
        return version;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------------------- grant

    @Test
    @DisplayName("an adult may grant consent for themselves")
    void adultSelfGrantAllowed() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(purposes.findActiveByCode(PERSONAL_FIT)).thenReturn(Optional.of(purposeStub(PERSONAL_FIT, false)));
        when(policyVersions.findCurrent(PERSONAL_FIT)).thenReturn(List.of(policyStub()));
        when(consentRecords.findActive(memberId, PERSONAL_FIT)).thenReturn(Optional.empty());

        ConsentRecordView result = service.grant(callerAccountId, memberId, PERSONAL_FIT, Map.of());

        assertThat(result.subjectType()).isEqualTo(SubjectType.ADULT_SELF);
        assertThat(result.mechanism()).isEqualTo(ConsentMechanism.SELF_ATTESTATION);
        assertThat(result.status()).isEqualTo(ConsentStatus.ACTIVE);
        verify(audit).record(eq(AuditAction.CONSENT_GRANTED), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an adult may not grant consent for another adult — ACCESS_DENIED")
    void adultGrantingForAnotherAdultDenied() {
        UUID otherAccountId = UUID.randomUUID();
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, otherAccountId, "ADULT", true)));

        assertThatThrownBy(() -> service.grant(callerAccountId, memberId, PERSONAL_FIT, Map.of()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(consentRecords, never()).save(any());
    }

    @Test
    @DisplayName("a guardian may grant CHILD_FOOD_SUITABILITY for their child as GUARDIAN_ATTESTATION")
    void guardianGrantForChildAllowed() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(true);
        when(purposes.findActiveByCode(CHILD_FOOD_SUITABILITY))
                .thenReturn(Optional.of(purposeStub(CHILD_FOOD_SUITABILITY, true)));
        when(policyVersions.findCurrent(CHILD_FOOD_SUITABILITY)).thenReturn(List.of(policyStub()));
        when(consentRecords.findActive(memberId, CHILD_FOOD_SUITABILITY)).thenReturn(Optional.empty());

        ConsentRecordView result = service.grant(callerAccountId, memberId, CHILD_FOOD_SUITABILITY, Map.of());

        assertThat(result.subjectType()).isEqualTo(SubjectType.CHILD);
        assertThat(result.mechanism()).isEqualTo(ConsentMechanism.GUARDIAN_ATTESTATION);
    }

    @Test
    @DisplayName("a non-guardian may not grant consent for a child — NOT_A_GUARDIAN")
    void nonGuardianGrantForChildDenied() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(false);

        assertThatThrownBy(() -> service.grant(callerAccountId, memberId, CHILD_FOOD_SUITABILITY, Map.of()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.NOT_A_GUARDIAN);

        verify(consentRecords, never()).save(any());
    }

    @Test
    @DisplayName("a requires_verifiable purpose cannot be granted for an adult subject — GUARDIAN_CONSENT_REQUIRED")
    void verifiablePurposeForAdultSelfDenied() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(purposes.findActiveByCode(CHILD_FOOD_SUITABILITY))
                .thenReturn(Optional.of(purposeStub(CHILD_FOOD_SUITABILITY, true)));

        assertThatThrownBy(() -> service.grant(callerAccountId, memberId, CHILD_FOOD_SUITABILITY, Map.of()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.GUARDIAN_CONSENT_REQUIRED);

        verify(consentRecords, never()).save(any());
    }

    @Test
    @DisplayName("granting an unknown purpose is RESOURCE_NOT_FOUND")
    void unknownPurposeNotFound() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(purposes.findActiveByCode("NOT_A_REAL_PURPOSE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(callerAccountId, memberId, "NOT_A_REAL_PURPOSE", Map.of()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("granting again for the same subject+purpose withdraws the previous active record first")
    void grantingAgainWithdrawsPreviousFirst() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(purposes.findActiveByCode(PERSONAL_FIT)).thenReturn(Optional.of(purposeStub(PERSONAL_FIT, false)));
        when(policyVersions.findCurrent(PERSONAL_FIT)).thenReturn(List.of(policyStub()));

        ConsentRecord existing = ConsentRecord.grant(memberId, SubjectType.ADULT_SELF, callerAccountId,
                PERSONAL_FIT, policyVersionId, ConsentMechanism.SELF_ATTESTATION, Map.of(),
                clock.instant().minusSeconds(60));
        when(consentRecords.findActive(memberId, PERSONAL_FIT)).thenReturn(Optional.of(existing));

        ConsentRecordView result = service.grant(callerAccountId, memberId, PERSONAL_FIT, Map.of());

        assertThat(existing.getStatus()).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(result.status()).isEqualTo(ConsentStatus.ACTIVE);
        verify(consentRecords).saveAndFlush(existing);
        verify(consentRecords).save(any(ConsentRecord.class));
    }

    // ------------------------------------------------------------------------- withdraw

    @Test
    @DisplayName("withdraw is idempotent when no active record exists")
    void withdrawNoOpWhenNoneActive() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(consentRecords.findActive(memberId, PERSONAL_FIT)).thenReturn(Optional.empty());

        service.withdraw(callerAccountId, memberId, PERSONAL_FIT);

        verify(consentRecords, never()).save(any());
        verify(audit, never()).record(eq(AuditAction.CONSENT_WITHDRAWN), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("withdraw then grant again succeeds and the new record is ACTIVE")
    void withdrawThenGrantAgain() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));

        ConsentRecord existing = ConsentRecord.grant(memberId, SubjectType.ADULT_SELF, callerAccountId,
                PERSONAL_FIT, policyVersionId, ConsentMechanism.SELF_ATTESTATION, Map.of(),
                clock.instant().minusSeconds(60));
        when(consentRecords.findActive(memberId, PERSONAL_FIT))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());

        service.withdraw(callerAccountId, memberId, PERSONAL_FIT);
        assertThat(existing.getStatus()).isEqualTo(ConsentStatus.WITHDRAWN);
        verify(audit).record(eq(AuditAction.CONSENT_WITHDRAWN), any(), any(), any(), any(), any(), any());

        when(purposes.findActiveByCode(PERSONAL_FIT)).thenReturn(Optional.of(purposeStub(PERSONAL_FIT, false)));
        when(policyVersions.findCurrent(PERSONAL_FIT)).thenReturn(List.of(policyStub()));

        ConsentRecordView result = service.grant(callerAccountId, memberId, PERSONAL_FIT, Map.of());
        assertThat(result.status()).isEqualTo(ConsentStatus.ACTIVE);
    }

    // ----------------------------------------------------------------------------- list

    @Test
    @DisplayName("list never exposes evidence")
    void listOmitsEvidence() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        ConsentRecord record = ConsentRecord.grant(memberId, SubjectType.ADULT_SELF, callerAccountId,
                PERSONAL_FIT, policyVersionId, ConsentMechanism.SELF_ATTESTATION,
                Map.of("policyHash", "abc123"), clock.instant());
        when(consentRecords.findAllForSubjects(List.of(memberId))).thenReturn(List.of(record));

        List<ConsentRecordView> result = service.list(callerAccountId, memberId);

        // ConsentRecordView carries no evidence accessor at all: the omission is
        // structural, not a serialization detail that could regress silently.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).purposeCode()).isEqualTo(PERSONAL_FIT);
    }

    @Test
    @DisplayName("a departed member is RESOURCE_NOT_FOUND")
    void inactiveMemberNotFound() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", false)));

        assertThatThrownBy(() -> service.list(callerAccountId, memberId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}

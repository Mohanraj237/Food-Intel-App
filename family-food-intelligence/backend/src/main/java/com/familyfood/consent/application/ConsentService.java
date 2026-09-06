package com.familyfood.consent.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.consent.domain.ConsentMechanism;
import com.familyfood.consent.domain.ConsentPolicyVersion;
import com.familyfood.consent.domain.ConsentPurpose;
import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.SubjectType;
import com.familyfood.consent.infra.ConsentPolicyVersionRepository;
import com.familyfood.consent.infra.ConsentPurposeRepository;
import com.familyfood.consent.infra.ConsentRecordRepository;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Grants, withdraws and lists a household member's consent ledger.
 *
 * <p>Authorization mirrors {@code ProfileService} exactly (D-001, D-002 made structural): a
 * {@code CHILD} subject may only be granted or withdrawn for by one of their guardians, and
 * always through {@link ConsentMechanism#GUARDIAN_ATTESTATION}; an {@code ADULT} subject may
 * only act for themselves, through {@link ConsentMechanism#SELF_ATTESTATION}. The mechanism
 * is always derived from the subject's membership record, never accepted from the caller —
 * see {@link ConsentPurpose#mechanismFor(SubjectType)}.
 *
 * <p><b>{@code requires_verifiable} vs. an adult subject.</b> {@code CHILD_FOOD_SUITABILITY}
 * is the one purpose seeded with {@code requires_verifiable = true} (V1), and per its own
 * seed comment it exists to cover data "a guardian provides" about "a child in your
 * household" — it is not a heavier version of an ordinary purpose an adult can also carry
 * for themselves. Resolving the subject to {@code ADULT_SELF} for such a purpose would mean
 * self-attestation standing in for a mechanism the purpose declares cannot be
 * self-attested, so this service refuses the combination outright with
 * {@link ErrorCode#GUARDIAN_CONSENT_REQUIRED} rather than silently downgrading it or
 * accepting it.
 *
 * <p><b>Re-granting.</b> {@code uq_consent_active_per_subject_purpose} allows at most one
 * ACTIVE row per (subject, purpose). Rather than let a second grant collide with that index,
 * {@link #grant} withdraws-and-flushes any existing ACTIVE row for the pair first, in the
 * same transaction — the same pattern {@code ProfileService} uses for
 * {@code uq_profile_current}.
 */
@Service
public class ConsentService {

    private static final String CHILD_MEMBER_TYPE = "CHILD";
    private static final String RESOURCE_TYPE = "ConsentRecord";

    private final ConsentRecordRepository consentRecords;
    private final ConsentPurposeRepository purposes;
    private final ConsentPolicyVersionRepository policyVersions;
    private final HouseholdAccess householdAccess;
    private final AuditService audit;
    private final Clock clock;

    public ConsentService(ConsentRecordRepository consentRecords,
                          ConsentPurposeRepository purposes,
                          ConsentPolicyVersionRepository policyVersions,
                          HouseholdAccess householdAccess,
                          AuditService audit,
                          Clock clock) {
        this.consentRecords = consentRecords;
        this.purposes = purposes;
        this.policyVersions = policyVersions;
        this.householdAccess = householdAccess;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ConsentRecordView grant(UUID callerAccountId, UUID subjectMemberId,
                                   String purposeCode, Map<String, Object> evidence) {
        MemberInfo member = authorize(callerAccountId, subjectMemberId);
        SubjectType subjectType = subjectTypeOf(member);

        ConsentPurpose purpose = purposes.findActiveByCode(purposeCode)
                .orElseThrow(() -> AppException.notFound("ConsentPurpose"));

        if (purpose.isRequiresVerifiable() && subjectType != SubjectType.CHILD) {
            throw new AppException(ErrorCode.GUARDIAN_CONSENT_REQUIRED,
                    Map.of("purposeCode", purposeCode));
        }

        ConsentPolicyVersion currentPolicy = currentPolicyVersion(purposeCode);
        ConsentMechanism mechanism = purpose.mechanismFor(subjectType);
        Instant now = clock.instant();

        // Withdraw-and-flush any existing ACTIVE row for this (subject, purpose) before
        // inserting the new one. Hibernate's default flush order runs pending inserts
        // before pending updates within a single flush, so without forcing this update to
        // the database now, the new row's INSERT could reach uq_consent_active_per_subject
        // _purpose while the old row is still (transiently) ACTIVE too, and the partial
        // unique index would reject it even though the end state is correct.
        consentRecords.findActive(subjectMemberId, purposeCode).ifPresent(existing -> {
            existing.withdraw(now);
            consentRecords.saveAndFlush(existing);
        });

        ConsentRecord granted = ConsentRecord.grant(
                subjectMemberId, subjectType, callerAccountId, purposeCode,
                currentPolicy.getId(), mechanism, evidence, now);
        consentRecords.save(granted);

        audit.record(AuditAction.CONSENT_GRANTED, RESOURCE_TYPE, granted.getId(),
                callerAccountId, member.householdId(), AuditOutcome.SUCCESS,
                Map.of("purposeCode", purposeCode, "subjectMemberId", subjectMemberId,
                        "mechanism", mechanism.name(), "policyVersion", currentPolicy.getVersion()));

        return ConsentRecordView.from(granted);
    }

    /** No-op, per {@link ConsentRecord#withdraw}'s own idempotent contract, when nothing is active. */
    @Transactional
    public void withdraw(UUID callerAccountId, UUID subjectMemberId, String purposeCode) {
        MemberInfo member = authorize(callerAccountId, subjectMemberId);

        Optional<ConsentRecord> active = consentRecords.findActive(subjectMemberId, purposeCode);
        if (active.isEmpty()) {
            return;
        }
        ConsentRecord record = active.get();
        record.withdraw(clock.instant());
        consentRecords.save(record);

        audit.record(AuditAction.CONSENT_WITHDRAWN, RESOURCE_TYPE, record.getId(),
                callerAccountId, member.householdId(), AuditOutcome.SUCCESS,
                Map.of("purposeCode", purposeCode, "subjectMemberId", subjectMemberId));
    }

    /** The full ledger for the subject — every record, any status. Never includes evidence. */
    @Transactional(readOnly = true)
    public List<ConsentRecordView> list(UUID callerAccountId, UUID subjectMemberId) {
        authorize(callerAccountId, subjectMemberId);
        return consentRecords.findAllForSubjects(List.of(subjectMemberId)).stream()
                .map(ConsentRecordView::from)
                .toList();
    }

    /**
     * The export counterpart to {@link #list} (AC-9.2): same authorization, same rows,
     * plus {@link ConsentRecord#getEvidence()} — see {@link ConsentExportView}'s own
     * Javadoc for why an ordinary listing omits it and export does not.
     */
    @Transactional(readOnly = true)
    public List<ConsentExportView> listForExport(UUID callerAccountId, UUID subjectMemberId) {
        authorize(callerAccountId, subjectMemberId);
        return consentRecords.findAllForSubjects(List.of(subjectMemberId)).stream()
                .map(ConsentExportView::from)
                .toList();
    }

    /** Public, unauthenticated: the current policy version for every active purpose. */
    @Transactional(readOnly = true)
    public List<ConsentPolicyView> listCurrentPolicies() {
        return purposes.findActiveOrdered().stream()
                .flatMap(purpose -> policyVersions.findCurrent(purpose.getCode()).stream()
                        .findFirst()
                        .map(version -> ConsentPolicyView.of(purpose, version))
                        .stream())
                .toList();
    }

    private ConsentPolicyVersion currentPolicyVersion(String purposeCode) {
        return policyVersions.findCurrent(purposeCode).stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                        Map.of("purposeCode", purposeCode, "reason", "no_current_policy_version")));
    }

    /**
     * D-001 / D-002 made structural, identical in shape to {@code ProfileService.authorize}:
     * a missing or departed member is {@code RESOURCE_NOT_FOUND}, a child subject is
     * guardian-only, an adult subject is self-only.
     */
    private MemberInfo authorize(UUID callerAccountId, UUID subjectMemberId) {
        MemberInfo member = householdAccess.findMember(subjectMemberId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!member.active()) {
            // A departed member has no live consent access (deliberately indistinguishable
            // from "never existed" — see published HouseholdAccess contract).
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (member.memberType().equals(CHILD_MEMBER_TYPE)) {
            if (!householdAccess.isGuardianOf(callerAccountId, subjectMemberId)) {
                throw new AppException(ErrorCode.NOT_A_GUARDIAN);
            }
        } else if (!callerAccountId.equals(member.userAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return member;
    }

    private SubjectType subjectTypeOf(MemberInfo member) {
        return member.memberType().equals(CHILD_MEMBER_TYPE)
                ? SubjectType.CHILD
                : SubjectType.ADULT_SELF;
    }
}

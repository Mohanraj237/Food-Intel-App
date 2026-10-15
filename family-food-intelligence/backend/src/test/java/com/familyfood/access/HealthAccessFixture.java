package com.familyfood.access;

import com.familyfood.access.application.HealthAccessPolicyImpl;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.AccessGrant;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.infra.AccessGrantRepository;
import com.familyfood.audit.AuditService;
import com.familyfood.consent.application.ConsentGate;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.HouseholdInfo;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The REAL {@link HealthAccessPolicyImpl} and {@link TrackingConsent}, wired over an in-memory
 * household, grant table and consent register.
 *
 * <p>Service tests use this instead of mocking the policy wherever the thing under test is a
 * decision about access — "a caregiver may record steps", "a viewer may not" — because a mocked
 * policy would only restate whatever the test author believed the rule to be. Here the rule that
 * runs is the one production runs (D-044); only persistence is faked.
 *
 * <p>Everyone added is consented to tracking by default (the common case). Tests about consent
 * withdraw it explicitly with {@link #withdrawConsent(UUID)}.
 */
public final class HealthAccessFixture {

    public static final ZoneId HOUSEHOLD_ZONE = ZoneId.of("Asia/Kolkata");

    public final HouseholdAccess households = mock(HouseholdAccess.class);
    public final AccessGrantRepository grants = mock(AccessGrantRepository.class);
    public final ConsentGate consentGate = mock(ConsentGate.class);
    public final AuditService audit = mock(AuditService.class);
    public final TrackingConsent trackingConsent;
    public final HealthAccessPolicyImpl policy;

    private final Instant now;
    private final Map<UUID, MemberInfo> members = new LinkedHashMap<>();
    private final Map<UUID, String> roles = new HashMap<>();
    private final Map<UUID, Instant> joinedAt = new HashMap<>();
    private final Set<String> guardianships = new HashSet<>();
    private final List<AccessGrant> grantRows = new ArrayList<>();
    private final Set<String> consents = new HashSet<>();
    private final Map<UUID, ZoneId> zones = new HashMap<>();

    public HealthAccessFixture(Instant now) {
        this.now = now;
        this.trackingConsent = new TrackingConsent(consentGate, households);
        this.policy = new HealthAccessPolicyImpl(households, grants, trackingConsent, audit);

        when(households.findMember(any())).thenAnswer(inv -> Optional.ofNullable(members.get(inv.<UUID>getArgument(0))));
        when(households.findActiveMembership(any(), any())).thenAnswer(inv -> {
            UUID householdId = inv.getArgument(0);
            UUID accountId = inv.getArgument(1);
            return members.values().stream()
                    .filter(m -> m.active() && m.householdId().equals(householdId)
                            && accountId != null && accountId.equals(m.userAccountId()))
                    .findFirst();
        });
        when(households.findActiveMembershipsOf(any())).thenAnswer(inv -> {
            UUID accountId = inv.getArgument(0);
            return members.values().stream()
                    .filter(m -> m.active() && accountId != null && accountId.equals(m.userAccountId()))
                    .sorted(Comparator.comparing(m -> joinedAt.get(m.id())))
                    .toList();
        });
        when(households.isGuardianOf(any(), any())).thenAnswer(inv ->
                guardianships.contains(key(inv.getArgument(0), inv.getArgument(1))));
        when(households.listActiveMembers(any())).thenAnswer(inv -> {
            UUID householdId = inv.getArgument(0);
            return members.values().stream()
                    .filter(m -> m.active() && m.householdId().equals(householdId))
                    .toList();
        });
        when(households.findHousehold(any())).thenAnswer(inv -> {
            UUID householdId = inv.getArgument(0);
            boolean known = members.values().stream().anyMatch(m -> m.householdId().equals(householdId));
            return known
                    ? Optional.of(new HouseholdInfo(householdId, "Home", false,
                            zones.getOrDefault(householdId, HOUSEHOLD_ZONE)))
                    : Optional.empty();
        });
        when(households.findMemberSummary(any())).thenAnswer(inv ->
                Optional.ofNullable(members.get(inv.<UUID>getArgument(0))).map(this::summaryOf));
        when(households.listActiveMemberSummaries(any())).thenAnswer(inv -> {
            UUID householdId = inv.getArgument(0);
            return members.values().stream()
                    .filter(m -> m.active() && m.householdId().equals(householdId))
                    .map(this::summaryOf)
                    .toList();
        });

        when(grants.findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(any(), any()))
                .thenAnswer(inv -> {
                    UUID subject = inv.getArgument(0);
                    UUID audience = inv.getArgument(1);
                    return grantRows.stream()
                            .filter(g -> g.isLive() && g.getSubjectMemberId().equals(subject)
                                    && g.getAudienceMemberId().equals(audience))
                            .toList();
                });

        when(consentGate.isActive(any(), any())).thenAnswer(inv ->
                consents.contains(key(inv.getArgument(0), inv.getArgument(1))));
        doAnswer(inv -> {
            if (!consents.contains(key(inv.getArgument(0), inv.getArgument(1)))) {
                throw new AppException(ErrorCode.CONSENT_REQUIRED);
            }
            return null;
        }).when(consentGate).require(any(), any());
    }

    /* ------------------------------------------------------------------ building */

    /** An active adult with an account, consented to tracking. */
    public MemberInfo adult(UUID householdId, UUID accountId, UUID memberId) {
        return add(new MemberInfo(memberId, householdId, accountId, "ADULT", true), "MEMBER");
    }

    /** An active child (no account), guarded by {@code guardianAccountId}, consented to tracking. */
    public MemberInfo child(UUID householdId, UUID memberId, UUID guardianAccountId) {
        MemberInfo child = add(new MemberInfo(memberId, householdId, null, "CHILD", true), "MEMBER");
        guardianships.add(key(guardianAccountId, memberId));
        return child;
    }

    public void role(UUID memberId, String role) {
        roles.put(memberId, role);
    }

    public void zone(UUID householdId, ZoneId zone) {
        zones.put(householdId, zone);
    }

    /** The member leaves the household: every lookup now sees them as inactive. */
    public void depart(UUID memberId) {
        MemberInfo m = members.get(memberId);
        members.put(memberId, new MemberInfo(m.id(), m.householdId(), m.userAccountId(), m.memberType(), false));
    }

    public AccessGrant grant(UUID subjectMemberId, UUID audienceMemberId, AccessScope scope, AccessLevel level) {
        MemberInfo subject = members.get(subjectMemberId);
        UUID grantedBy = subject == null ? UUID.randomUUID() : subject.userAccountId();
        AccessGrant grant = AccessGrant.grant(subjectMemberId, audienceMemberId, scope, level,
                grantedBy == null ? UUID.randomUUID() : grantedBy, now);
        grantRows.add(grant);
        return grant;
    }

    public void withdrawConsent(UUID memberId) {
        consents.remove(key(memberId, purposeOf(memberId)));
    }

    public void giveConsent(UUID memberId) {
        consents.add(key(memberId, purposeOf(memberId)));
    }

    public String purposeOf(UUID memberId) {
        return trackingConsent.purposeFor(members.get(memberId));
    }

    /** The household display label the fixture gives a member. */
    public String labelOf(UUID memberId) {
        return "label-" + memberId.toString().substring(0, 8);
    }

    /* ------------------------------------------------------------------ internals */

    private MemberInfo add(MemberInfo member, String role) {
        members.put(member.id(), member);
        roles.putIfAbsent(member.id(), role);
        joinedAt.put(member.id(), now.minusSeconds(86_400L * (100 - members.size())));
        consents.add(key(member.id(), trackingConsent.purposeFor(member)));
        return member;
    }

    private MemberSummary summaryOf(MemberInfo m) {
        return new MemberSummary(m.id(), m.householdId(), m.userAccountId(), m.memberType(),
                roles.getOrDefault(m.id(), "MEMBER"), labelOf(m.id()),
                joinedAt.get(m.id()), m.active());
    }

    private static String key(Object a, Object b) {
        return a + "|" + b;
    }
}

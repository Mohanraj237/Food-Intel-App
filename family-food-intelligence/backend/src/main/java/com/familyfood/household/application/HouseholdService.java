package com.familyfood.household.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.household.domain.GuardianLink;
import com.familyfood.household.domain.GuardianRelationship;
import com.familyfood.household.domain.Household;
import com.familyfood.household.domain.HouseholdInvitation;
import com.familyfood.household.domain.HouseholdMember;
import com.familyfood.household.domain.InvitationStatus;
import com.familyfood.household.domain.MemberRole;
import com.familyfood.household.domain.MemberType;
import com.familyfood.household.infra.GuardianLinkRepository;
import com.familyfood.household.infra.HouseholdInvitationRepository;
import com.familyfood.household.infra.HouseholdMemberRepository;
import com.familyfood.household.infra.HouseholdRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.util.EmailNormaliser;
import com.familyfood.platform.util.Hashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Household creation, membership, invitations and guardian links.
 *
 * <p>Every mutating method starts by resolving the caller's own live membership in the
 * target household ({@link #requireActiveMember(UUID, UUID)}) and never trusts a household
 * id, member id or invitation id from the request as a fact about the caller's own
 * standing — only as a target resource to be authorized against (Gate-2 IDOR posture).
 *
 * <p><b>Who may remove a member (architectural assumption).</b> Only the {@link
 * MemberRole#OWNER} may remove someone else; any active member may remove themselves
 * ("leave"). A CHILD member has no account and so can never call this API themselves —
 * in practice a child is only ever removed by the OWNER, which falls out of the same rule
 * rather than needing a special case.
 */
@Service
public class HouseholdService {

    /** No configuration knob for this on purpose — a fixed, reviewable window. */
    static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final GuardianLinkRepository guardianLinks;
    private final HouseholdInvitationRepository invitations;
    private final Hashing hashing;
    private final AuditService audit;
    private final Clock clock;

    public HouseholdService(HouseholdRepository households,
                            HouseholdMemberRepository members,
                            GuardianLinkRepository guardianLinks,
                            HouseholdInvitationRepository invitations,
                            Hashing hashing,
                            AuditService audit,
                            Clock clock) {
        this.households = households;
        this.members = members;
        this.guardianLinks = guardianLinks;
        this.invitations = invitations;
        this.hashing = hashing;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------- create

    @Transactional
    public Household createHousehold(UUID callerAccountId, String name, String ownerDisplayLabel) {
        Instant now = clock.instant();

        Household household = households.save(Household.create(name, callerAccountId, now));
        HouseholdMember owner = members.save(
                HouseholdMember.owner(household.getId(), callerAccountId, ownerDisplayLabel, now));

        audit.record(AuditAction.HOUSEHOLD_CREATED, "Household", household.getId(),
                callerAccountId, household.getId(), AuditOutcome.SUCCESS,
                Map.of("ownerMemberId", owner.getId()));

        return household;
    }

    /**
     * "Just me" (D-043): a household of one, so that a person can start tracking without
     * thinking about a household at all. There is no second person model behind it (D-035).
     *
     * <p><b>Idempotent by design.</b> If the caller already belongs to a household — a double
     * tap, a second device, or someone who joined a family first — nothing is created and
     * their oldest membership is returned. "Just me" never makes a second household, because
     * two households would split a person's history across two places for no benefit.
     */
    @Transactional
    public IndividualProvisioning provisionIndividual(UUID callerAccountId, String displayLabel) {
        List<HouseholdMember> existing = members.findByUserAccountIdAndLeftAtIsNull(callerAccountId);
        if (!existing.isEmpty()) {
            HouseholdMember oldest = existing.stream()
                    .min(Comparator.comparing(HouseholdMember::getJoinedAt))
                    .orElseThrow();
            Household household = households.findById(oldest.getHouseholdId())
                    .orElseThrow(() -> AppException.notFound("Household"));
            return new IndividualProvisioning(household, oldest, false);
        }

        Instant now = clock.instant();
        Household household = households.save(Household.createIndividual(displayLabel, callerAccountId, now));
        HouseholdMember owner = members.save(
                HouseholdMember.owner(household.getId(), callerAccountId, displayLabel, now));

        audit.record(AuditAction.HOUSEHOLD_CREATED, "Household", household.getId(),
                callerAccountId, household.getId(), AuditOutcome.SUCCESS,
                Map.of("ownerMemberId", owner.getId(), "individual", true));

        return new IndividualProvisioning(household, owner, true);
    }

    /** @param created false when the caller already had a household and nothing was made */
    public record IndividualProvisioning(Household household, HouseholdMember member, boolean created) {}

    // ---------------------------------------------------------------- invite adult

    /**
     * Creates a {@code household_invitation} row and nothing else (D-002): no member row,
     * no profile, no consent exists for the invitee until they accept with their own
     * account.
     *
     * @return the invitation, including the raw token. The raw token exists only in this
     *         return value — it is never persisted (only its hash is).
     */
    @Transactional
    public InvitationIssued inviteAdult(UUID callerAccountId, UUID householdId, String rawEmail) {
        Instant now = clock.instant();
        HouseholdMember caller = requireActiveMember(
                householdId, callerAccountId, AuditAction.HOUSEHOLD_INVITATION_SENT);

        String email = EmailNormaliser.normalise(rawEmail);
        if (email == null || email.isBlank()) {
            throw AppException.validation("email", "required");
        }

        String rawToken = Hashing.newOpaqueToken();
        HouseholdInvitation invitation = invitations.save(HouseholdInvitation.create(
                householdId, caller.getId(), email, hashing.hashToken(rawToken),
                now, now.plus(INVITATION_TTL)));

        audit.record(AuditAction.HOUSEHOLD_INVITATION_SENT, "HouseholdInvitation",
                invitation.getId(), callerAccountId, householdId, AuditOutcome.SUCCESS, Map.of());

        return new InvitationIssued(invitation.getId(), householdId, rawToken, invitation.getExpiresAt());
    }

    // ------------------------------------------------------------- accept invitation

    /**
     * @param displayLabel the label the new member chooses for themselves within the
     *                      household; household membership carries no other identifying
     *                      information about them.
     */
    @Transactional
    public HouseholdMember acceptInvitation(UUID callerAccountId, String rawToken, String displayLabel) {
        Instant now = clock.instant();

        HouseholdInvitation invitation = invitations.findByTokenHash(hashing.hashToken(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_INVALID));

        if (!invitation.isUsable(now)) {
            throw new AppException(ErrorCode.INVITATION_INVALID);
        }
        if (members.existsByHouseholdIdAndUserAccountIdAndLeftAtIsNull(
                invitation.getHouseholdId(), callerAccountId)) {
            throw new AppException(ErrorCode.ALREADY_HOUSEHOLD_MEMBER);
        }

        HouseholdMember member = members.save(HouseholdMember.adult(
                invitation.getHouseholdId(), callerAccountId, displayLabel, now));

        invitation.accept(callerAccountId, now);
        invitations.save(invitation);
        boolean becameFamily = markFamily(invitation.getHouseholdId(), now);

        audit.record(AuditAction.HOUSEHOLD_INVITATION_ACCEPTED, "HouseholdInvitation",
                invitation.getId(), callerAccountId, invitation.getHouseholdId(),
                AuditOutcome.SUCCESS, Map.of());
        audit.record(AuditAction.HOUSEHOLD_MEMBER_ADDED, "HouseholdMember", member.getId(),
                callerAccountId, invitation.getHouseholdId(), AuditOutcome.SUCCESS,
                Map.of("memberType", "ADULT", "becameFamily", becameFamily));

        return member;
    }

    // ------------------------------------------------------------------- add child

    @Transactional
    public HouseholdMember addChild(UUID callerAccountId, UUID householdId, String displayLabel,
                                    String relationship, String authorityBasis) {
        Instant now = clock.instant();
        HouseholdMember guardian = requireActiveMember(
                householdId, callerAccountId, AuditAction.HOUSEHOLD_MEMBER_ADDED);
        GuardianRelationship rel = parseRelationship(relationship);

        HouseholdMember child = members.save(HouseholdMember.child(householdId, displayLabel, now));
        GuardianLink link = guardianLinks.save(
                GuardianLink.create(child.getId(), guardian.getId(), rel, authorityBasis, now));
        boolean becameFamily = markFamily(householdId, now);

        audit.record(AuditAction.HOUSEHOLD_MEMBER_ADDED, "HouseholdMember", child.getId(),
                callerAccountId, householdId, AuditOutcome.SUCCESS,
                Map.of("memberType", "CHILD", "becameFamily", becameFamily));
        audit.record(AuditAction.GUARDIAN_LINK_CREATED, "GuardianLink", link.getId(),
                callerAccountId, householdId, AuditOutcome.SUCCESS,
                Map.of("childMemberId", child.getId()));

        return child;
    }

    // ---------------------------------------------------------------- list members

    @Transactional(readOnly = true)
    public List<HouseholdMember> listMembers(UUID callerAccountId, UUID householdId) {
        requireActiveMember(householdId, callerAccountId);
        return members.findByHouseholdIdAndLeftAtIsNull(householdId);
    }

    // -------------------------------------------------------------- list my households

    /**
     * Every household the account is currently an active member of, oldest membership
     * first. This ordering is deliberate, not incidental — the frontend takes the first
     * entry as the account's default household when it belongs to more than one.
     */
    @Transactional(readOnly = true)
    public List<HouseholdSummary> listMyHouseholds(UUID callerAccountId) {
        List<HouseholdMember> memberships = members.findByUserAccountIdAndLeftAtIsNull(callerAccountId);

        List<UUID> householdIds = memberships.stream().map(HouseholdMember::getHouseholdId).toList();
        Map<UUID, Household> householdsById = households.findAllById(householdIds).stream()
                .collect(Collectors.toMap(Household::getId, h -> h));

        return memberships.stream()
                .sorted(Comparator.comparing(HouseholdMember::getJoinedAt))
                .map(member -> {
                    Household household = householdsById.get(member.getHouseholdId());
                    return new HouseholdSummary(
                            member.getHouseholdId(), household.getName(), member.getId(),
                            member.getRole().name(), member.getDisplayLabel(), member.getJoinedAt(),
                            household.isIndividual(), household.getTimeZone());
                })
                .toList();
    }

    // ------------------------------------------------------------------ remove member

    @Transactional
    public void removeMember(UUID callerAccountId, UUID householdId, UUID memberId) {
        Instant now = clock.instant();
        HouseholdMember caller = requireActiveMember(
                householdId, callerAccountId, AuditAction.HOUSEHOLD_MEMBER_REMOVED);

        HouseholdMember target = members.findById(memberId)
                .filter(m -> m.getHouseholdId().equals(householdId) && m.isActive())
                .orElseThrow(() -> AppException.notFound("HouseholdMember"));

        boolean isSelf = target.getId().equals(caller.getId());
        if (!isSelf && caller.getRole() != MemberRole.OWNER) {
            audit.denied(AuditAction.HOUSEHOLD_MEMBER_REMOVED, "HouseholdMember", target.getId(),
                    callerAccountId, householdId, "CALLER_NOT_OWNER");
            throw AppException.forbidden();
        }

        target.leave(now);
        members.save(target);

        audit.record(AuditAction.HOUSEHOLD_MEMBER_REMOVED, "HouseholdMember", target.getId(),
                callerAccountId, householdId, AuditOutcome.SUCCESS, Map.of("self", isSelf));
    }

    // ------------------------------------------------------------- child erasure

    /**
     * Erases a child's household standing on behalf of an authorized guardian (CH-008,
     * FR-010's child-erasure gap). Distinct from {@link #removeMember}: that method only
     * ever soft-leaves a membership; this one is the destructive path, scrubbing the
     * membership's own personal content and ending every guardian relationship over the
     * child. It does not touch the child's {@code person_profile} rows — that is {@code
     * profile}'s own data to erase, which is why this method lives behind an orchestrator
     * (the two run in one transaction; see {@code datarights.application.DataRightsService})
     * rather than being called directly.
     *
     * <p>Never erases an ADULT member — an adult's own data leaves only through account
     * deletion (D-002: adults are their own data principals, not erasable by someone else's
     * say-so).
     */
    @Transactional
    public void eraseChildMember(UUID callerAccountId, UUID householdId, UUID childMemberId) {
        Instant now = clock.instant();
        HouseholdMember caller = requireActiveMember(
                householdId, callerAccountId, AuditAction.CHILD_DATA_DELETED);

        HouseholdMember target = members.findById(childMemberId)
                .filter(m -> m.getHouseholdId().equals(householdId) && m.isActive())
                .orElseThrow(() -> AppException.notFound("HouseholdMember"));

        if (target.getMemberType() != MemberType.CHILD) {
            throw AppException.validation("memberId", "must be a CHILD member");
        }

        if (!guardianLinks.existsByChildMemberIdAndGuardianMemberIdAndRevokedAtIsNull(
                childMemberId, caller.getId())) {
            audit.denied(AuditAction.CHILD_DATA_DELETED, "HouseholdMember", childMemberId,
                    callerAccountId, householdId, "NOT_A_GUARDIAN");
            throw new AppException(ErrorCode.NOT_A_GUARDIAN);
        }

        target.scrubForErasure(now);
        members.save(target);

        guardianLinks.findByChildMemberIdAndRevokedAtIsNull(childMemberId)
                .forEach(link -> {
                    link.revoke(now);
                    guardianLinks.save(link);
                });

        // No profile content in metadata — the fact of erasure is auditable, its content
        // is not (Constitution §33 / this session's brief §10).
        audit.record(AuditAction.CHILD_DATA_DELETED, "HouseholdMember", childMemberId,
                callerAccountId, householdId, AuditOutcome.SUCCESS, Map.of());
    }

    // -------------------------------------------------------- account erasure (household side)

    /**
     * Account erasure's household-side work (AC-9.3): scrubs {@code displayLabel} and ends
     * every membership the account ever held (active or already-departed — a departed
     * membership's label is still personal data), then revokes every guardian relationship
     * the account held. Returns the scrubbed membership ids so the caller (an orchestrator)
     * can erase each one's {@code person_profile} rows, which this method — deliberately
     * confined to household's own tables — does not touch.
     *
     * <p>Never touches another account's membership, another household's data, or a child's
     * membership: {@code findByUserAccountId} can only ever return this one account's own
     * rows, by construction (a CHILD member has no {@code userAccountId} at all — D-001).
     */
    @Transactional
    public List<UUID> eraseAccountMemberships(UUID accountId, Instant now) {
        List<HouseholdMember> ownMemberships = members.findByUserAccountId(accountId);
        List<UUID> memberIds = ownMemberships.stream().map(HouseholdMember::getId).toList();

        ownMemberships.forEach(member -> {
            member.scrubForErasure(now);
            members.save(member);
        });

        guardianLinks.findByGuardianMemberIdInAndRevokedAtIsNull(memberIds)
                .forEach(link -> {
                    link.revoke(now);
                    guardianLinks.save(link);
                });

        return memberIds;
    }

    // ------------------------------------------------------------- revoke invitation

    @Transactional
    public void revokeInvitation(UUID callerAccountId, UUID householdId, UUID invitationId) {
        requireActiveMember(householdId, callerAccountId, AuditAction.HOUSEHOLD_INVITATION_REVOKED);

        HouseholdInvitation invitation = invitations.findById(invitationId)
                .filter(inv -> inv.getHouseholdId().equals(householdId))
                .orElseThrow(() -> AppException.notFound("HouseholdInvitation"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_INVALID);
        }

        invitation.revoke();
        invitations.save(invitation);

        audit.record(AuditAction.HOUSEHOLD_INVITATION_REVOKED, "HouseholdInvitation",
                invitation.getId(), callerAccountId, householdId, AuditOutcome.SUCCESS, Map.of());
    }

    // -------------------------------------------------------------------- internals

    /**
     * D-043: a household of one stops being one the moment a second member joins. Returns
     * whether this call made that change, so it is recorded exactly once.
     */
    private boolean markFamily(UUID householdId, Instant now) {
        return households.findById(householdId)
                .map(household -> {
                    boolean changed = household.becomeFamily(now);
                    if (changed) {
                        households.save(household);
                    }
                    return changed;
                })
                .orElse(false);
    }

    /** Authorization check with no audit side effect, for read paths. */
    private HouseholdMember requireActiveMember(UUID householdId, UUID callerAccountId) {
        return members.findByHouseholdIdAndUserAccountIdAndLeftAtIsNull(householdId, callerAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER));
    }

    /** Authorization check that also audits the denial under the action being attempted. */
    private HouseholdMember requireActiveMember(UUID householdId, UUID callerAccountId,
                                                AuditAction actionIfDenied) {
        return members.findByHouseholdIdAndUserAccountIdAndLeftAtIsNull(householdId, callerAccountId)
                .orElseGet(() -> {
                    audit.denied(actionIfDenied, "Household", householdId, callerAccountId,
                            householdId, "NOT_HOUSEHOLD_MEMBER");
                    throw new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER);
                });
    }

    private static GuardianRelationship parseRelationship(String value) {
        try {
            return GuardianRelationship.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw AppException.validation("relationship", "must be PARENT or LEGAL_GUARDIAN");
        }
    }

    /**
     * @param rawToken the raw invitation token — returned to the inviter exactly once and
     *                 never stored in this form.
     */
    public record InvitationIssued(UUID invitationId, UUID householdId, String rawToken,
                                   Instant expiresAt) {}

    /**
     * One of the caller's active household memberships, joined with the household's own
     * name. {@code role} is {@link MemberRole#name()} — the same convention
     * {@code MemberResponse.from()} uses for {@code HouseholdMember.getRole()}.
     */
    public record HouseholdSummary(UUID householdId, String householdName, UUID memberId,
                                   String role, String displayLabel, Instant joinedAt,
                                   boolean individual, String timeZone) {}
}

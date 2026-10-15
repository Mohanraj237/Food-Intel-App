package com.familyfood.household.application;

import com.familyfood.household.domain.GuardianLink;
import com.familyfood.household.domain.HouseholdMember;
import com.familyfood.household.infra.GuardianLinkRepository;
import com.familyfood.household.infra.HouseholdMemberRepository;
import com.familyfood.household.infra.HouseholdRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs {@link HouseholdAccess} with the household module's own repositories. This is the
 * one class in the module another module's code is allowed to depend on for membership
 * facts — everything else in {@code household.infra} stays private to this module.
 */
@Service
public class HouseholdAccessImpl implements HouseholdAccess {

    private final HouseholdMemberRepository members;
    private final GuardianLinkRepository guardianLinks;
    private final HouseholdRepository households;

    public HouseholdAccessImpl(HouseholdMemberRepository members,
                               GuardianLinkRepository guardianLinks,
                               HouseholdRepository households) {
        this.members = members;
        this.guardianLinks = guardianLinks;
        this.households = households;
    }

    @Override
    public Optional<MemberInfo> findActiveMembership(UUID householdId, UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return members.findByHouseholdIdAndUserAccountIdAndLeftAtIsNull(householdId, accountId)
                .map(HouseholdAccessImpl::toInfo);
    }

    @Override
    public List<MemberInfo> findActiveMembershipsOf(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return members.findByUserAccountIdAndLeftAtIsNull(accountId).stream()
                .sorted(java.util.Comparator.comparing(HouseholdMember::getJoinedAt))
                .map(HouseholdAccessImpl::toInfo)
                .toList();
    }

    @Override
    public Optional<HouseholdInfo> findHousehold(UUID householdId) {
        return households.findById(householdId)
                .filter(h -> h.getDeletedAt() == null)
                .map(h -> new HouseholdInfo(h.getId(), h.getName(), h.isIndividual(), h.zone()));
    }

    @Override
    public List<MemberSummary> listActiveMemberSummaries(UUID householdId) {
        return members.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(HouseholdAccessImpl::toSummary)
                .toList();
    }

    @Override
    public Optional<MemberSummary> findMemberSummary(UUID memberId) {
        return members.findById(memberId).map(HouseholdAccessImpl::toSummary);
    }

    private static MemberSummary toSummary(HouseholdMember member) {
        return new MemberSummary(
                member.getId(),
                member.getHouseholdId(),
                member.getUserAccountId(),
                member.getMemberType().name(),
                member.getRole().name(),
                member.getDisplayLabel(),
                member.getJoinedAt(),
                member.isActive());
    }

    @Override
    public Optional<MemberInfo> findMember(UUID memberId) {
        return members.findById(memberId).map(HouseholdAccessImpl::toInfo);
    }

    @Override
    public boolean isGuardianOf(UUID guardianAccountId, UUID childMemberId) {
        return members.findById(childMemberId)
                .flatMap(child -> members.findByHouseholdIdAndUserAccountIdAndLeftAtIsNull(
                        child.getHouseholdId(), guardianAccountId))
                .map(guardianMember -> guardianLinks.existsByChildMemberIdAndGuardianMemberIdAndRevokedAtIsNull(
                        childMemberId, guardianMember.getId()))
                .orElse(false);
    }

    @Override
    public List<MemberInfo> listActiveMembers(UUID householdId) {
        return members.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(HouseholdAccessImpl::toInfo)
                .toList();
    }

    @Override
    public List<GuardedChildInfo> listGuardedChildren(UUID guardianAccountId) {
        List<UUID> guardianMemberIds = members.findByUserAccountIdAndLeftAtIsNull(guardianAccountId)
                .stream().map(HouseholdMember::getId).toList();
        if (guardianMemberIds.isEmpty()) {
            return List.of();
        }

        List<GuardianLink> links = guardianLinks.findByGuardianMemberIdInAndRevokedAtIsNull(guardianMemberIds);
        if (links.isEmpty()) {
            return List.of();
        }

        Map<UUID, HouseholdMember> childrenById = members.findAllById(
                links.stream().map(GuardianLink::getChildMemberId).toList()).stream()
                .collect(Collectors.toMap(HouseholdMember::getId, child -> child));

        return links.stream()
                .map(link -> childrenById.get(link.getChildMemberId()))
                .filter(child -> child != null && child.isActive())
                .map(child -> new GuardedChildInfo(
                        child.getId(), child.getHouseholdId(), child.getDisplayLabel(), child.getJoinedAt()))
                .toList();
    }

    private static MemberInfo toInfo(HouseholdMember member) {
        return new MemberInfo(
                member.getId(),
                member.getHouseholdId(),
                member.getUserAccountId(),
                member.getMemberType().name(),
                member.isActive());
    }
}

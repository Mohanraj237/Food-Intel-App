package com.familyfood.household.application;

import com.familyfood.household.domain.GuardianLink;
import com.familyfood.household.domain.HouseholdMember;
import com.familyfood.household.infra.GuardianLinkRepository;
import com.familyfood.household.infra.HouseholdMemberRepository;
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

    public HouseholdAccessImpl(HouseholdMemberRepository members, GuardianLinkRepository guardianLinks) {
        this.members = members;
        this.guardianLinks = guardianLinks;
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

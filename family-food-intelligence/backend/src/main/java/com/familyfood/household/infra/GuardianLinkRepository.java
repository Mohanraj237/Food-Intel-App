package com.familyfood.household.infra;

import com.familyfood.household.domain.GuardianLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, UUID> {

    boolean existsByChildMemberIdAndGuardianMemberIdAndRevokedAtIsNull(
            UUID childMemberId, UUID guardianMemberId);

    List<GuardianLink> findByChildMemberIdAndRevokedAtIsNull(UUID childMemberId);

    /**
     * Every still-active link naming one of the given members as the guardian — used by
     * account erasure to end every guardian relationship the erased account held, across
     * every household it was a guardian in.
     */
    List<GuardianLink> findByGuardianMemberIdInAndRevokedAtIsNull(Collection<UUID> guardianMemberIds);
}

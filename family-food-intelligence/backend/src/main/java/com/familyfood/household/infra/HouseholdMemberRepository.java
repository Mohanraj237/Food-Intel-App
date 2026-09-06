package com.familyfood.household.infra;

import com.familyfood.household.domain.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    /** The caller's live membership in a household — the authorization check every mutating call starts with. */
    Optional<HouseholdMember> findByHouseholdIdAndUserAccountIdAndLeftAtIsNull(
            UUID householdId, UUID userAccountId);

    List<HouseholdMember> findByHouseholdIdAndLeftAtIsNull(UUID householdId);

    boolean existsByHouseholdIdAndUserAccountIdAndLeftAtIsNull(UUID householdId, UUID userAccountId);

    /** Every active (non-departed) membership for one account, across every household it belongs to. */
    List<HouseholdMember> findByUserAccountIdAndLeftAtIsNull(UUID userAccountId);

    /**
     * Every membership row for one account, active or departed — used only by account
     * erasure, which must scrub {@code displayLabel} on every membership the account ever
     * held, not only its currently-live ones.
     */
    List<HouseholdMember> findByUserAccountId(UUID userAccountId);
}

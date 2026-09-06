package com.familyfood.profile.infra;

import com.familyfood.profile.domain.PersonProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonProfileRepository extends JpaRepository<PersonProfile, UUID> {

    /**
     * The one row {@code uq_profile_current} guarantees can exist for this member. This is
     * both the read path's answer and the write path's "is this a create or an update"
     * question.
     */
    Optional<PersonProfile> findByHouseholdMemberIdAndIsCurrentTrue(UUID householdMemberId);

    /** Full version history, most recent first — not used by the read/write paths above. */
    List<PersonProfile> findByHouseholdMemberIdOrderByVersionDesc(UUID householdMemberId);

    /**
     * Every version, hard-deleted. Erasure's only use of this repository: a person's
     * profile (age band, sex, dietary preference, goals, allergens, self-declared
     * conditions) is exactly the personal data AC-9.3 requires removed, with none of the
     * "surviving evidence row" reasons that keep {@code household_member}, {@code
     * guardian_link} or {@code consent_record} rows in place instead. {@code profile_goal}
     * / {@code profile_allergen} / {@code profile_condition} cascade from here.
     */
    void deleteByHouseholdMemberId(UUID householdMemberId);
}

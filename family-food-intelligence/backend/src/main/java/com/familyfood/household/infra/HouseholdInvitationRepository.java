package com.familyfood.household.infra;

import com.familyfood.household.domain.HouseholdInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, UUID> {

    Optional<HouseholdInvitation> findByTokenHash(String tokenHash);
}

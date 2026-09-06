package com.familyfood.profile;

import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.profile.infra.PersonProfileRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Version-chain correctness against a real Postgres, exercising the actual
 * {@code uq_profile_current} partial unique index and {@code uq_profile_version}
 * constraint rather than a mocked repository.
 *
 * <p>{@link HouseholdAccess} has no implementation yet (the household module lands in
 * parallel), so it is stubbed with {@code @MockBean} — this test's concern is the profile
 * write path and the schema's own invariants, not membership resolution.
 */
class ProfileVersionChainIT extends IntegrationTestBase {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private PersonProfileRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private HouseholdAccess householdAccess;

    @Test
    @DisplayName("create then update leaves exactly two rows: v1 superseded, v2 current")
    void createThenUpdateProducesCorrectVersionChain() {
        UUID accountId = insertUserAccount();
        UUID householdId = insertHousehold(accountId);
        UUID memberId = insertAdultMember(householdId, accountId);

        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, accountId, "ADULT", true)));

        ProfileView created = profileService.createOrUpdateProfile(
                accountId, memberId, AgeBand.A18_30, null, null,
                Set.of(GoalCode.GENERAL_BALANCED_EATING), Set.of(), Set.of());
        assertThat(created.version()).isEqualTo(1);

        ProfileView updated = profileService.createOrUpdateProfile(
                accountId, memberId, AgeBand.A31_45, null, null,
                Set.of(GoalCode.INCREASE_PROTEIN), Set.of(), Set.of("thyroid"));
        assertThat(updated.version()).isEqualTo(2);

        List<com.familyfood.profile.domain.PersonProfile> history =
                repository.findByHouseholdMemberIdOrderByVersionDesc(memberId);
        assertThat(history).hasSize(2);

        com.familyfood.profile.domain.PersonProfile v2 = history.get(0);
        com.familyfood.profile.domain.PersonProfile v1 = history.get(1);

        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.isCurrent()).isTrue();
        assertThat(v2.getSupersededAt()).isNull();
        assertThat(v2.getAgeBand()).isEqualTo(AgeBand.A31_45);
        assertThat(v2.getConditions()).containsExactly("thyroid");

        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v1.isCurrent()).isFalse();
        assertThat(v1.getSupersededAt()).isNotNull();

        // Exactly one current row per member — the partial unique index's own guarantee,
        // confirmed here via the repository rather than assumed. Entities are not
        // reference-equal across separate transactions/persistence contexts, so this
        // compares by id rather than relying on PersonProfile's (absent) equals().
        assertThat(repository.findByHouseholdMemberIdAndIsCurrentTrue(memberId))
                .map(com.familyfood.profile.domain.PersonProfile::getId)
                .contains(v2.getId());

        assertThat(profileService.getCurrentProfile(accountId, memberId).version()).isEqualTo(2);
    }

    // ------------------------------------------------------------------------ fixtures

    private UUID insertUserAccount() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into user_account (id, email, password_hash)
                values (?, ?, 'not-a-real-hash')
                """, id, "profile-it-" + id + "@example.com");
        return id;
    }

    private UUID insertHousehold(UUID createdByUserId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into household (id, name, created_by_user_id)
                values (?, 'Test household', ?)
                """, id, createdByUserId);
        return id;
    }

    private UUID insertAdultMember(UUID householdId, UUID userAccountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into household_member
                    (id, household_id, user_account_id, member_type, role, display_label)
                values (?, ?, ?, 'ADULT', 'OWNER', 'Owner')
                """, id, householdId, userAccountId);
        return id;
    }
}

package com.familyfood.profile.application;

import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.profile.domain.PersonProfile;
import com.familyfood.profile.infra.PersonProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the D-001/D-002 authorization rule and the version-chain bookkeeping
 * in {@link ProfileService}, with {@link HouseholdAccess} and the repository mocked. The
 * real-database version-chain behaviour (the partial unique index, the actual row count)
 * is covered separately by an *IT test.
 */
class ProfileServiceTest {

    private final PersonProfileRepository repository = mock(PersonProfileRepository.class);
    private final HouseholdAccess householdAccess = mock(HouseholdAccess.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

    private ProfileService service;

    private final UUID householdId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID callerAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProfileService(repository, householdAccess, audit, clock);
        when(repository.save(any(PersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------- authorization: adult

    @Test
    @DisplayName("an adult may create/update their own profile")
    void adultSelfEditAllowed() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(repository.findByHouseholdMemberIdAndIsCurrentTrue(memberId)).thenReturn(Optional.empty());

        ProfileView result = service.createOrUpdateProfile(
                callerAccountId, memberId, AgeBand.A18_30, null, null,
                Set.of(GoalCode.GENERAL_BALANCED_EATING), Set.of(), Set.of());

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.ageBand()).isEqualTo(AgeBand.A18_30);
        verify(repository, times(1)).save(any(PersonProfile.class));
    }

    @Test
    @DisplayName("an adult may not edit another adult's profile (D-002) — ACCESS_DENIED")
    void adultEditingSomeoneElseDenied() {
        UUID otherAccountId = UUID.randomUUID();
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, otherAccountId, "ADULT", true)));

        assertThatThrownBy(() -> service.createOrUpdateProfile(
                callerAccountId, memberId, AgeBand.A18_30, null, null, Set.of(), Set.of(), Set.of()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------- authorization: child

    @Test
    @DisplayName("a guardian may create/update a child's profile")
    void guardianEditingChildAllowed() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(true);
        when(repository.findByHouseholdMemberIdAndIsCurrentTrue(memberId)).thenReturn(Optional.empty());

        ProfileView result = service.createOrUpdateProfile(
                callerAccountId, memberId, AgeBand.C4_6, null, null, Set.of(), Set.of(), Set.of());

        assertThat(result.version()).isEqualTo(1);
        verify(audit).record(eq(com.familyfood.audit.AuditAction.CHILD_PROFILE_CREATED),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a non-guardian may not edit a child's profile — NOT_A_GUARDIAN")
    void nonGuardianEditingChildDenied() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(false);

        assertThatThrownBy(() -> service.createOrUpdateProfile(
                callerAccountId, memberId, AgeBand.C4_6, null, null, Set.of(), Set.of(), Set.of()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.NOT_A_GUARDIAN);

        verify(repository, never()).save(any());
    }

    // --------------------------------------------------------------- membership edge cases

    @Test
    @DisplayName("an unknown member is RESOURCE_NOT_FOUND")
    void unknownMemberNotFound() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentProfile(callerAccountId, memberId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("a departed (inactive) member is RESOURCE_NOT_FOUND, even for the former self")
    void inactiveMemberNotFound() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", false)));

        assertThatThrownBy(() -> service.getCurrentProfile(callerAccountId, memberId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // --------------------------------------------------------------------- version chain

    @Test
    @DisplayName("updating an existing profile supersedes the old row and increments the version")
    void updateSupersedesAndIncrementsVersion() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));

        PersonProfile existing = PersonProfile.newVersion(
                memberId, 1, AgeBand.A18_30, null, null, Set.of(), Set.of(), Set.of(),
                callerAccountId, clock.instant().minusSeconds(60));
        when(repository.findByHouseholdMemberIdAndIsCurrentTrue(memberId))
                .thenReturn(Optional.of(existing));

        ArgumentCaptor<PersonProfile> flushed = ArgumentCaptor.forClass(PersonProfile.class);
        ArgumentCaptor<PersonProfile> saved = ArgumentCaptor.forClass(PersonProfile.class);

        ProfileView result = service.createOrUpdateProfile(
                callerAccountId, memberId, AgeBand.A31_45, null, null,
                Set.of(GoalCode.INCREASE_FIBRE), Set.of(AllergenCode.PEANUT), Set.of());

        // The previous current row is flushed immediately (see ProfileService), so its
        // is_current = false update physically reaches the database before the new
        // row's insert — otherwise uq_profile_current would transiently see two current
        // rows within the same flush. The new row goes through the ordinary save().
        verify(repository, times(1)).saveAndFlush(flushed.capture());
        verify(repository, times(1)).save(saved.capture());
        PersonProfile firstSaved = flushed.getValue();
        PersonProfile secondSaved = saved.getValue();

        assertThat(firstSaved).isSameAs(existing);
        assertThat(firstSaved.isCurrent()).isFalse();
        assertThat(firstSaved.getSupersededAt()).isNotNull();

        assertThat(secondSaved.getVersion()).isEqualTo(2);
        assertThat(secondSaved.isCurrent()).isTrue();
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.ageBand()).isEqualTo(AgeBand.A31_45);

        verify(audit).record(eq(com.familyfood.audit.AuditAction.PROFILE_UPDATED),
                any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------- reads

    @Test
    @DisplayName("reading a child's profile audits CHILD_DATA_ACCESSED")
    void readingChildProfileAudited() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(true);

        PersonProfile existing = PersonProfile.newVersion(
                memberId, 1, AgeBand.C7_9, null, null, Set.of(), Set.of(), Set.of(),
                callerAccountId, clock.instant());
        when(repository.findByHouseholdMemberIdAndIsCurrentTrue(memberId))
                .thenReturn(Optional.of(existing));

        service.getCurrentProfile(callerAccountId, memberId);

        verify(audit).success(eq(com.familyfood.audit.AuditAction.CHILD_DATA_ACCESSED),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("reading a profile that has never been created is RESOURCE_NOT_FOUND")
    void readingMissingProfileNotFound() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(repository.findByHouseholdMemberIdAndIsCurrentTrue(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentProfile(callerAccountId, memberId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}

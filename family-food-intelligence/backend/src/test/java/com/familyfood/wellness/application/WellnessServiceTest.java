package com.familyfood.wellness.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.MetricVisibilityGrant;
import com.familyfood.wellness.infra.HealthObservationRepository;
import com.familyfood.wellness.infra.MetricVisibilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two safety properties of Phase 1, at the service level: the age gate refuses in the
 * domain (D-036), and visibility is default-closed (D-038).
 *
 * <p>These are written as behaviour tests rather than interaction tests wherever possible,
 * because the thing that must stay true is "the data does not come back", not "a particular
 * method was called".
 */
class WellnessServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final UUID HOUSEHOLD = UUID.randomUUID();

    private static final UUID ADULT_ACCOUNT = UUID.randomUUID();
    private static final UUID ADULT_MEMBER = UUID.randomUUID();
    private static final UUID OTHER_ADULT_ACCOUNT = UUID.randomUUID();
    private static final UUID OTHER_ADULT_MEMBER = UUID.randomUUID();
    private static final UUID CHILD_MEMBER = UUID.randomUUID();

    private HealthObservationRepository observations;
    private MetricVisibilityRepository visibility;
    private HouseholdAccess households;
    private ProfileService profiles;
    private AuditService audit;
    private WellnessService service;

    @BeforeEach
    void setUp() {
        observations = mock(HealthObservationRepository.class);
        visibility = mock(MetricVisibilityRepository.class);
        households = mock(HouseholdAccess.class);
        profiles = mock(ProfileService.class);
        audit = mock(AuditService.class);
        service = new WellnessService(observations, visibility, households, profiles, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(households.findMember(ADULT_MEMBER)).thenReturn(Optional.of(
                new MemberInfo(ADULT_MEMBER, HOUSEHOLD, ADULT_ACCOUNT, "ADULT", true)));
        when(households.findMember(OTHER_ADULT_MEMBER)).thenReturn(Optional.of(
                new MemberInfo(OTHER_ADULT_MEMBER, HOUSEHOLD, OTHER_ADULT_ACCOUNT, "ADULT", true)));
        when(households.findMember(CHILD_MEMBER)).thenReturn(Optional.of(
                new MemberInfo(CHILD_MEMBER, HOUSEHOLD, null, "CHILD", true)));
        when(observations.save(any(HealthObservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenAgeBand(UUID member, AgeBand band) {
        when(profiles.getCurrentProfile(any(), eq(member))).thenReturn(new ProfileView(
                UUID.randomUUID(), member, 1, band, null, null,
                Set.of(), Set.of(), Set.of(), NOW, ADULT_ACCOUNT));
    }

    private RecordObservationCommand cmd(UUID subject, MetricCode metric, String value) {
        return new RecordObservationCommand(subject, metric, new BigDecimal(value), null, null);
    }

    /* ------------------------------------------------------- D-036: the age gate */

    @Test
    @DisplayName("an adult may record their own weight — D-036 supersedes D-017 for adults")
    void adultCanRecordOwnWeight() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A31_45);

        ObservationView view = service.record(ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.WEIGHT_KG, "72.35"));

        assertThat(view.metricCode()).isEqualTo(MetricCode.WEIGHT_KG);
        // Exact round-trip, which is why the column and the entity use BigDecimal.
        assertThat(view.value()).isEqualByComparingTo("72.35");
        assertThat(view.unit()).isEqualTo("kg");
    }

    @Test
    @DisplayName("a guardian may NOT record a child's weight, and nothing is written")
    void guardianCannotRecordChildWeight() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C7_9);
        when(households.isGuardianOf(ADULT_ACCOUNT, CHILD_MEMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.WEIGHT_KG, "24.0")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);

        // The point of the test: refused in the domain, so no row exists regardless of what
        // any UI offered.
        verify(observations, never()).save(any());
        verify(audit).denied(eq(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE), anyString(),
                any(), eq(ADULT_ACCOUNT), eq(HOUSEHOLD), anyString());
    }

    @Test
    @DisplayName("a guardian may NOT record a child's blood pressure either")
    void guardianCannotRecordChildClinicalMetric() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C13_17);
        when(households.isGuardianOf(ADULT_ACCOUNT, CHILD_MEMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.SYSTOLIC_MMHG, "110")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a guardian MAY record a child's steps — the metric, not the engagement layer")
    void guardianCanRecordChildActivity() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C7_9);
        when(households.isGuardianOf(ADULT_ACCOUNT, CHILD_MEMBER)).thenReturn(true);

        ObservationView view = service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.STEPS, "4200"));

        assertThat(view.metricCategory()).isEqualTo(MetricCategory.ACTIVITY);
        // A child's observation must still land in the strict child-data audit category.
        verify(audit).success(eq(AuditAction.CHILD_DATA_ACCESSED), anyString(), any(),
                eq(ADULT_ACCOUNT), eq(HOUSEHOLD));
    }

    @Test
    @DisplayName("a subject with no profile is treated as possibly a child — fails closed")
    void unknownAgeFailsClosed() {
        when(profiles.getCurrentProfile(any(), eq(ADULT_MEMBER)))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> service.record(ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.WEIGHT_KG, "72")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.PROFILE_REQUIRED_FOR_METRIC);

        // Otherwise "skip the profile step" would be a route to recording a child's weight.
        verify(observations, never()).save(any());
    }

    /* --------------------------------------------------- D-002: no adult proxies */

    @Test
    @DisplayName("one adult may not record for another adult, however senior in the household")
    void adultCannotRecordForAnotherAdult() {
        givenAgeBand(OTHER_ADULT_MEMBER, AgeBand.A31_45);

        assertThatThrownBy(() -> service.record(ADULT_ACCOUNT, cmd(OTHER_ADULT_MEMBER, MetricCode.WEIGHT_KG, "80")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a future-dated reading is rejected")
    void futureReadingRejected() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A31_45);
        RecordObservationCommand future = new RecordObservationCommand(
                ADULT_MEMBER, MetricCode.WEIGHT_KG, new BigDecimal("72"),
                NOW.plusSeconds(86_400), null);

        assertThatThrownBy(() -> service.record(ADULT_ACCOUNT, future))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    /* ----------------------------------------------- D-038: default-closed reads */

    @Test
    @DisplayName("household membership alone grants no visibility — the default is closed")
    void otherAdultSeesNothingByDefault() {
        when(households.listActiveMembers(HOUSEHOLD)).thenReturn(List.of(
                new MemberInfo(ADULT_MEMBER, HOUSEHOLD, ADULT_ACCOUNT, "ADULT", true),
                new MemberInfo(OTHER_ADULT_MEMBER, HOUSEHOLD, OTHER_ADULT_ACCOUNT, "ADULT", true)));
        when(visibility.findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(
                ADULT_MEMBER, OTHER_ADULT_MEMBER)).thenReturn(List.of());

        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, ADULT_MEMBER)).isEmpty();

        // And a targeted read is refused rather than returning an empty list, so an access
        // failure never masquerades as "no data".
        assertThatThrownBy(() -> service.history(OTHER_ADULT_ACCOUNT, ADULT_MEMBER,
                MetricCode.WEIGHT_KG, NOW.minusSeconds(86_400), NOW))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("an explicit grant opens exactly one category and no more")
    void grantOpensOnlyTheGrantedCategory() {
        when(households.listActiveMembers(HOUSEHOLD)).thenReturn(List.of(
                new MemberInfo(ADULT_MEMBER, HOUSEHOLD, ADULT_ACCOUNT, "ADULT", true),
                new MemberInfo(OTHER_ADULT_MEMBER, HOUSEHOLD, OTHER_ADULT_ACCOUNT, "ADULT", true)));
        when(visibility.findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(
                ADULT_MEMBER, OTHER_ADULT_MEMBER)).thenReturn(List.of(
                        MetricVisibilityGrant.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER,
                                MetricCategory.ACTIVITY, ADULT_ACCOUNT, NOW)));

        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, ADULT_MEMBER))
                .containsExactly(MetricCategory.ACTIVITY);

        // Sharing activity must not leak body or clinical data.
        assertThatThrownBy(() -> service.history(OTHER_ADULT_ACCOUNT, ADULT_MEMBER,
                MetricCode.WEIGHT_KG, NOW.minusSeconds(86_400), NOW))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("the subject always sees all of their own categories")
    void subjectSeesOwnData() {
        assertThat(service.visibleCategories(ADULT_ACCOUNT, ADULT_MEMBER))
                .containsAll(List.of(MetricCategory.BODY, MetricCategory.CLINICAL,
                        MetricCategory.ACTIVITY, MetricCategory.SLEEP));
    }

    @Test
    @DisplayName("only the subject may share their own data — not a guardian, not an owner")
    void onlySubjectMayShare() {
        assertThatThrownBy(() -> service.share(OTHER_ADULT_ACCOUNT, ADULT_MEMBER,
                OTHER_ADULT_MEMBER, MetricCategory.BODY))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("re-sharing an already-shared category is idempotent, not a constraint violation")
    void shareIsIdempotent() {
        MetricVisibilityGrant existing = MetricVisibilityGrant.grant(
                ADULT_MEMBER, OTHER_ADULT_MEMBER, MetricCategory.ACTIVITY, ADULT_ACCOUNT, NOW);
        when(visibility.findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(
                ADULT_MEMBER, OTHER_ADULT_MEMBER)).thenReturn(List.of(existing));

        MetricVisibilityGrant result = service.share(ADULT_ACCOUNT, ADULT_MEMBER,
                OTHER_ADULT_MEMBER, MetricCategory.ACTIVITY);

        assertThat(result).isSameAs(existing);
        verify(visibility, never()).save(any());
    }

    @Test
    @DisplayName("a read of someone else's data is audited")
    void crossMemberReadIsAudited() {
        when(households.isGuardianOf(ADULT_ACCOUNT, CHILD_MEMBER)).thenReturn(true);
        when(observations
                .findByHouseholdMemberIdAndMetricCodeAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                        eq(CHILD_MEMBER), any(), any(), any()))
                .thenReturn(List.of());

        service.history(ADULT_ACCOUNT, CHILD_MEMBER, MetricCode.STEPS, NOW.minusSeconds(86_400), NOW);

        verify(audit).success(eq(AuditAction.HEALTH_DATA_ACCESSED), anyString(), any(),
                eq(ADULT_ACCOUNT), eq(HOUSEHOLD));
    }
}

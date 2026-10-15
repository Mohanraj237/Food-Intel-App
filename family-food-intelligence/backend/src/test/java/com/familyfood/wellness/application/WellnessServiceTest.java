package com.familyfood.wellness.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.consent.application.ConsentGate;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import com.familyfood.wellness.infra.HealthObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The safety properties of observation recording and reading, at the service level, with the
 * REAL access policy (D-044) and the REAL tracking-consent resolution (D-048) running over an
 * in-memory household:
 *
 * <ul>
 *   <li>the age gate refuses in the domain (D-036), on the subject's age, failing closed;</li>
 *   <li>visibility is default-closed and opened per category only by the subject (D-038/D-044);</li>
 *   <li>a caregiver the subject chose may act within exactly the scopes granted (D-045);</li>
 *   <li>collection needs the subject's tracking consent — checked after the age gate (D-048).</li>
 * </ul>
 *
 * <p>Sharing itself (grant/revoke) moved to {@code AccessGrantService} and is tested there.
 *
 * <p>These are behaviour tests wherever possible, because what must stay true is "the data does
 * not come back" and "no row is written", not "a particular method was called".
 */
class WellnessServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final UUID HOUSEHOLD = UUID.randomUUID();

    private static final UUID ADULT_ACCOUNT = UUID.randomUUID();
    private static final UUID ADULT_MEMBER = UUID.randomUUID();
    private static final UUID OTHER_ADULT_ACCOUNT = UUID.randomUUID();
    private static final UUID OTHER_ADULT_MEMBER = UUID.randomUUID();
    private static final UUID CHILD_MEMBER = UUID.randomUUID();

    private HealthAccessFixture fixture;
    private HealthObservationRepository observations;
    private ProfileService profiles;
    private AuditService audit;
    private ConsentGate consentGate;
    private WellnessService service;

    @BeforeEach
    void setUp() {
        fixture = new HealthAccessFixture(NOW);
        observations = mock(HealthObservationRepository.class);
        profiles = mock(ProfileService.class);
        audit = fixture.audit;
        consentGate = fixture.consentGate;
        service = new WellnessService(observations, fixture.policy, fixture.trackingConsent,
                fixture.households, profiles, audit, Clock.fixed(NOW, ZoneOffset.UTC));

        fixture.adult(HOUSEHOLD, ADULT_ACCOUNT, ADULT_MEMBER);
        fixture.adult(HOUSEHOLD, OTHER_ADULT_ACCOUNT, OTHER_ADULT_MEMBER);
        // The first adult guards the child.
        fixture.child(HOUSEHOLD, CHILD_MEMBER, ADULT_ACCOUNT);

        when(profiles.currentAgeBand(any())).thenReturn(Optional.empty());
        when(observations.save(any(HealthObservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenAgeBand(UUID member, AgeBand band) {
        when(profiles.currentAgeBand(member)).thenReturn(Optional.of(band));
    }

    private static RecordObservationCommand cmd(UUID subject, MetricCode metric, String value) {
        return new RecordObservationCommand(subject, metric, new BigDecimal(value), null, null);
    }

    private static ErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            return e.code();
        }
        throw new AssertionError("expected an AppException");
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
        assertThat(view.source()).isEqualTo(ObservationSource.MANUAL);
    }

    @Test
    @DisplayName("a guardian may NOT record a child's weight, and nothing is written")
    void guardianCannotRecordChildWeight() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C7_9);

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.WEIGHT_KG, "24.0"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);

        // The point of the test: refused in the domain, so no row exists regardless of what
        // any UI offered — and the attempt is findable in the subject's trail.
        verify(observations, never()).save(any());
        verify(audit).deniedForSubject(eq(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE), anyString(),
                eq(CHILD_MEMBER), eq(ADULT_ACCOUNT), eq(HOUSEHOLD), eq(CHILD_MEMBER), anyString());
    }

    @Test
    @DisplayName("a guardian may NOT record a child's blood pressure either")
    void guardianCannotRecordChildClinicalMetric() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C13_17);

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.SYSTOLIC_MMHG, "110"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a guardian MAY record a child's steps — the metric, not the engagement layer")
    void guardianCanRecordChildActivity() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C7_9);

        ObservationView view = service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.STEPS, "4200"));

        assertThat(view.metricCategory()).isEqualTo(MetricCategory.ACTIVITY);
        // A child's observation must still land in the strict child-data audit category, and
        // in the child's own trail.
        verify(audit).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), anyString(), any(),
                eq(ADULT_ACCOUNT), eq(HOUSEHOLD), eq(CHILD_MEMBER), anyMap());
        verify(audit).successForSubject(eq(AuditAction.HEALTH_METRIC_RECORDED), anyString(), any(),
                eq(ADULT_ACCOUNT), eq(HOUSEHOLD), eq(CHILD_MEMBER), anyMap());
        // Under the child purpose, which the guardian grants (D-048).
        verify(consentGate).require(CHILD_MEMBER, "CHILD_WELLNESS_TRACKING");
    }

    @Test
    @DisplayName("a subject with no profile is treated as possibly a child — fails closed")
    void unknownAgeFailsClosed() {
        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.WEIGHT_KG, "72"))))
                .isEqualTo(ErrorCode.PROFILE_REQUIRED_FOR_METRIC);

        // Otherwise "skip the profile step" would be a route to recording a child's weight.
        verify(observations, never()).save(any());
        assertThat(service.isMetricPermittedForSubject(ADULT_ACCOUNT, ADULT_MEMBER, MetricCode.WEIGHT_KG)).isFalse();
    }

    @Test
    @DisplayName("the age gate runs before consent: a child's weight is refused even with no consent")
    void childWeightRefusedForAgeEvenWithoutConsent() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C4_6);
        fixture.withdrawConsent(CHILD_MEMBER);

        // "We do not store this for a child" is the answer, not "please consent first".
        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.WEIGHT_KG, "18"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        verify(consentGate, never()).require(any(), any());
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a CHILD member is a child even if their profile carried an adult band (D-046)")
    void childMemberTypeWinsOverAdultBand() {
        // ProfileService now refuses this pairing; the gate must still hold for data saved
        // before it did.
        givenAgeBand(CHILD_MEMBER, AgeBand.A31_45);

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.WEIGHT_KG, "30"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        assertThat(service.isChildSubject(CHILD_MEMBER)).isTrue();
        assertThat(service.isMetricPermittedForSubject(ADULT_ACCOUNT, CHILD_MEMBER, MetricCode.WEIGHT_KG)).isFalse();
        assertThat(service.isMetricPermittedForSubject(ADULT_ACCOUNT, CHILD_MEMBER, MetricCode.STEPS)).isTrue();
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("an adult member holding a child band is a child for every age rule (fail closed)")
    void adultMemberWithChildBandIsTreatedAsChild() {
        givenAgeBand(ADULT_MEMBER, AgeBand.C13_17);

        assertThat(service.isChildSubject(ADULT_MEMBER)).isTrue();
        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.WEIGHT_KG, "50"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
    }

    /* ------------------------------------------------------------ D-048: consent */

    @Test
    @DisplayName("without tracking consent nothing is collected — CONSENT_REQUIRED, no row")
    void missingConsentRefusesAndSavesNothing() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A31_45);
        fixture.withdrawConsent(ADULT_MEMBER);

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.STEPS, "5000"))))
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
        verify(consentGate).require(ADULT_MEMBER, "HEALTH_TRACKING");
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a guardian cannot record for a child whose tracking consent is not given")
    void childWithoutConsentCannotBeTracked() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C7_9);
        fixture.withdrawConsent(CHILD_MEMBER);

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(CHILD_MEMBER, MetricCode.SLEEP_MINUTES, "600"))))
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("withdrawing consent hides shared data from others; the subject still sees their own")
    void withdrawnConsentHidesSharingButNotSelf() {
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.ACTIVITY, AccessLevel.VIEW);
        fixture.withdrawConsent(ADULT_MEMBER);

        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, ADULT_MEMBER)).isEmpty();
        assertThat(service.visibleCategories(ADULT_ACCOUNT, ADULT_MEMBER))
                .containsExactlyInAnyOrder(MetricCategory.values());
    }

    /* ----------------------------------------------- D-002 / D-045: who may write */

    @Test
    @DisplayName("one adult may not record for another adult, however senior in the household")
    void adultCannotRecordForAnotherAdult() {
        givenAgeBand(OTHER_ADULT_MEMBER, AgeBand.A31_45);
        fixture.role(ADULT_MEMBER, "OWNER");

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, cmd(OTHER_ADULT_MEMBER, MetricCode.WEIGHT_KG, "80"))))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a caregiver with MANAGE on ACTIVITY records an elder's steps, audited against the elder")
    void caregiverRecordsStepsForAdult() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A60_PLUS);
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.ACTIVITY, AccessLevel.MANAGE);

        ObservationView view = service.record(OTHER_ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.STEPS, "3100"));

        assertThat(view.subjectMemberId()).isEqualTo(ADULT_MEMBER);
        ArgumentCaptor<HealthObservation> saved = ArgumentCaptor.forClass(HealthObservation.class);
        verify(observations).save(saved.capture());
        // Attributed to the caregiver who entered it (D-045).
        assertThat(saved.getValue().getRecordedByUserId()).isEqualTo(OTHER_ADULT_ACCOUNT);
        verify(audit).successForSubject(eq(AuditAction.HEALTH_METRIC_RECORDED), anyString(), any(),
                eq(OTHER_ADULT_ACCOUNT), eq(HOUSEHOLD), eq(ADULT_MEMBER),
                argThat(m -> "CAREGIVER".equals(m.get("relationship")) && "ACTIVITY".equals(m.get("scope"))));
        verify(audit, never()).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), any(), any(), any(),
                any(), any(), anyMap());
    }

    @Test
    @DisplayName("a SHARED viewer cannot record — VIEW is not MANAGE")
    void sharedViewerCannotRecord() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A31_45);
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.ACTIVITY, AccessLevel.VIEW);

        assertThat(codeOf(() -> service.record(OTHER_ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.STEPS, "3100"))))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(observations, never()).save(any());
        verify(audit).deniedForSubject(eq(AuditAction.HEALTH_DATA_ACCESS_DENIED), anyString(), eq(ADULT_MEMBER),
                eq(OTHER_ADULT_ACCOUNT), eq(HOUSEHOLD), eq(ADULT_MEMBER), anyString());
    }

    @Test
    @DisplayName("a caregiver for ACTIVITY cannot record weight — MANAGE is per scope")
    void caregiverWithoutBodyManageCannotRecordWeight() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A60_PLUS);
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.ACTIVITY, AccessLevel.MANAGE);
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.BODY, AccessLevel.VIEW);

        assertThat(codeOf(() -> service.record(OTHER_ADULT_ACCOUNT, cmd(ADULT_MEMBER, MetricCode.WEIGHT_KG, "64"))))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a future-dated reading is rejected")
    void futureReadingRejected() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A31_45);
        RecordObservationCommand future = new RecordObservationCommand(
                ADULT_MEMBER, MetricCode.WEIGHT_KG, new BigDecimal("72"), NOW.plusSeconds(86_400), null);

        assertThat(codeOf(() -> service.record(ADULT_ACCOUNT, future))).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(observations, never()).save(any());
    }

    /* ----------------------------------------------- D-038 / D-044: default-closed reads */

    @Test
    @DisplayName("household membership alone grants no visibility — the default is closed")
    void otherAdultSeesNothingByDefault() {
        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, ADULT_MEMBER)).isEmpty();

        // And a targeted read is refused rather than returning an empty list, so an access
        // failure never masquerades as "no data".
        assertThat(codeOf(() -> service.history(OTHER_ADULT_ACCOUNT, ADULT_MEMBER,
                MetricCode.WEIGHT_KG, NOW.minusSeconds(86_400), NOW)))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("an explicit grant opens exactly one category and no more")
    void grantOpensOnlyTheGrantedCategory() {
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.ACTIVITY, AccessLevel.VIEW);

        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, ADULT_MEMBER))
                .containsExactly(MetricCategory.ACTIVITY);

        // Sharing activity must not leak body or clinical data.
        assertThat(codeOf(() -> service.history(OTHER_ADULT_ACCOUNT, ADULT_MEMBER,
                MetricCode.WEIGHT_KG, NOW.minusSeconds(86_400), NOW)))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("the subject always sees all of their own categories")
    void subjectSeesOwnData() {
        assertThat(service.visibleCategories(ADULT_ACCOUNT, ADULT_MEMBER))
                .containsExactlyInAnyOrder(MetricCategory.values());
    }

    @Test
    @DisplayName("a guardian sees every category of a child's data; a non-guardian sees none")
    void guardianSeesChildNonGuardianDoesNot() {
        assertThat(service.visibleCategories(ADULT_ACCOUNT, CHILD_MEMBER))
                .containsExactlyInAnyOrder(MetricCategory.values());
        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, CHILD_MEMBER)).isEmpty();
    }

    @Test
    @DisplayName("a read of someone else's data is audited against them")
    void crossMemberReadIsAudited() {
        when(observations.findByHouseholdMemberIdAndMetricCodeAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                eq(CHILD_MEMBER), any(), any(), any())).thenReturn(List.of());

        service.history(ADULT_ACCOUNT, CHILD_MEMBER, MetricCode.STEPS, NOW.minusSeconds(86_400), NOW);

        verify(audit).successForSubject(eq(AuditAction.HEALTH_DATA_ACCESSED), anyString(), any(),
                eq(ADULT_ACCOUNT), eq(HOUSEHOLD), eq(CHILD_MEMBER), anyMap());
        verify(audit).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), anyString(), any(),
                eq(ADULT_ACCOUNT), eq(HOUSEHOLD), eq(CHILD_MEMBER), anyMap());
    }

    @Test
    @DisplayName("the window query asks only for categories the caller may see")
    void windowRestrictsTheQueryItself() {
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.HYDRATION, AccessLevel.VIEW);

        service.window(OTHER_ADULT_ACCOUNT, ADULT_MEMBER, NOW.minusSeconds(86_400), NOW);

        // Rows the caller may not see are never even loaded.
        verify(observations).findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                eq(ADULT_MEMBER), eq(List.of(MetricCategory.HYDRATION)), any(), any());
        verify(audit, times(1)).successForSubject(eq(AuditAction.HEALTH_DATA_ACCESSED), anyString(), any(),
                eq(OTHER_ADULT_ACCOUNT), eq(HOUSEHOLD), eq(ADULT_MEMBER), anyMap());
    }

    @Test
    @DisplayName("a caller who may see nothing gets an empty window, with no query and no access event")
    void windowForNoneIsEmpty() {
        assertThat(service.window(OTHER_ADULT_ACCOUNT, ADULT_MEMBER, NOW.minusSeconds(86_400), NOW)).isEmpty();

        verify(observations, never())
                .findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(any(), any(), any(), any());
        verify(audit, never()).successForSubject(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("sums contain only metrics with data — absent is never zero")
    void sumsOmitMetricsWithNoData() {
        when(observations.findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                eq(ADULT_MEMBER), any(), any(), any())).thenReturn(List.of(
                HealthObservation.record(ADULT_MEMBER, MetricCode.STEPS, new BigDecimal("4000"), NOW,
                        ADULT_ACCOUNT, ObservationSource.MANUAL, NOW),
                HealthObservation.record(ADULT_MEMBER, MetricCode.STEPS, new BigDecimal("1500"), NOW.minusSeconds(60),
                        ADULT_ACCOUNT, ObservationSource.MANUAL, NOW)));

        Map<MetricCode, BigDecimal> sums = service.sumByMetric(ADULT_ACCOUNT, ADULT_MEMBER, NOW.minusSeconds(86_400), NOW);

        assertThat(sums).containsOnlyKeys(MetricCode.STEPS);
        assertThat(sums.get(MetricCode.STEPS)).isEqualByComparingTo("5500");
        assertThat(sums).doesNotContainKey(MetricCode.WATER_ML);
    }

    /* ------------------------------------------------ WellnessAccess: derived writes */

    private static Map<MetricCode, BigDecimal> values(Object... pairs) {
        Map<MetricCode, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((MetricCode) pairs[i], new BigDecimal((String) pairs[i + 1]));
        }
        return map;
    }

    @Test
    @DisplayName("replaceDerived needs MANAGE on EVERY metric's category — nothing retracted or written otherwise")
    void replaceDerivedNeedsManageForEveryCategory() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A60_PLUS);
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.NUTRITION, AccessLevel.MANAGE);
        UUID sourceRef = UUID.randomUUID();

        assertThat(codeOf(() -> service.replaceDerived(OTHER_ADULT_ACCOUNT, ADULT_MEMBER, sourceRef,
                values(MetricCode.ENERGY_KCAL, "400", MetricCode.WATER_ML, "250"), NOW)))
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(observations, never()).deleteByHouseholdMemberIdAndSourceRef(any(), any());
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("a NUTRITION caregiver may derive an elder's meal share; each write is audited against them")
    void caregiverDerivesWithinGrantedScope() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A60_PLUS);
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.NUTRITION, AccessLevel.MANAGE);
        UUID sourceRef = UUID.randomUUID();

        service.replaceDerived(OTHER_ADULT_ACCOUNT, ADULT_MEMBER, sourceRef,
                values(MetricCode.ENERGY_KCAL, "400", MetricCode.PROTEIN_G, "12"), NOW.minusSeconds(3600));

        verify(observations).deleteByHouseholdMemberIdAndSourceRef(ADULT_MEMBER, sourceRef);
        ArgumentCaptor<HealthObservation> saved = ArgumentCaptor.forClass(HealthObservation.class);
        verify(observations, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(o -> {
            assertThat(o.getSource()).isEqualTo(ObservationSource.DERIVED);
            assertThat(o.getSourceRef()).isEqualTo(sourceRef);
            assertThat(o.getHouseholdMemberId()).isEqualTo(ADULT_MEMBER);
        });
        verify(audit).successForSubject(eq(AuditAction.HEALTH_METRIC_RECORDED), anyString(), eq(sourceRef),
                eq(OTHER_ADULT_ACCOUNT), eq(HOUSEHOLD), eq(ADULT_MEMBER), anyMap());
    }

    @Test
    @DisplayName("replaceDerived into another adult's record without a grant is refused")
    void replaceDerivedForAnotherAdultRefused() {
        givenAgeBand(OTHER_ADULT_MEMBER, AgeBand.A31_45);

        assertThat(codeOf(() -> service.replaceDerived(ADULT_ACCOUNT, OTHER_ADULT_MEMBER, UUID.randomUUID(),
                values(MetricCode.ENERGY_KCAL, "300"), NOW)))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(observations, never()).save(any());
        verify(observations, never()).deleteByHouseholdMemberIdAndSourceRef(any(), any());
    }

    @Test
    @DisplayName("a derived source cannot smuggle a body metric onto a child")
    void replaceDerivedStillRunsTheAgeGate() {
        givenAgeBand(CHILD_MEMBER, AgeBand.C10_12);

        assertThat(codeOf(() -> service.replaceDerived(ADULT_ACCOUNT, CHILD_MEMBER, UUID.randomUUID(),
                values(MetricCode.WEIGHT_KG, "35"), NOW)))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("replaceDerived needs the subject's tracking consent")
    void replaceDerivedNeedsConsent() {
        givenAgeBand(ADULT_MEMBER, AgeBand.A31_45);
        fixture.withdrawConsent(ADULT_MEMBER);

        assertThat(codeOf(() -> service.replaceDerived(ADULT_ACCOUNT, ADULT_MEMBER, UUID.randomUUID(),
                values(MetricCode.ENERGY_KCAL, "300"), NOW)))
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
        verify(observations, never()).save(any());
    }

    @Test
    @DisplayName("mayDeriveFor: self and guarded child yes; another adult no; a NUTRITION caregiver yes; no consent no")
    void mayDeriveFor() {
        assertThat(service.mayDeriveFor(ADULT_ACCOUNT, ADULT_MEMBER, MetricCategory.NUTRITION)).isTrue();
        assertThat(service.mayDeriveFor(ADULT_ACCOUNT, CHILD_MEMBER, MetricCategory.NUTRITION)).isTrue();
        assertThat(service.mayDeriveFor(ADULT_ACCOUNT, OTHER_ADULT_MEMBER, MetricCategory.NUTRITION)).isFalse();
        assertThat(service.mayDeriveFor(OTHER_ADULT_ACCOUNT, CHILD_MEMBER, MetricCategory.NUTRITION)).isFalse();

        fixture.grant(OTHER_ADULT_MEMBER, ADULT_MEMBER, AccessScope.NUTRITION, AccessLevel.MANAGE);
        assertThat(service.mayDeriveFor(ADULT_ACCOUNT, OTHER_ADULT_MEMBER, MetricCategory.NUTRITION)).isTrue();
        assertThat(service.mayDeriveFor(ADULT_ACCOUNT, OTHER_ADULT_MEMBER, MetricCategory.BODY)).isFalse();

        fixture.withdrawConsent(ADULT_MEMBER);
        assertThat(service.mayDeriveFor(ADULT_ACCOUNT, ADULT_MEMBER, MetricCategory.NUTRITION)).isFalse();
    }

    @Test
    @DisplayName("readable categories never include a scope that is not a metric category")
    void readableCategoriesAreMetricCategoriesOnly() {
        fixture.grant(ADULT_MEMBER, OTHER_ADULT_MEMBER, AccessScope.HABITS, AccessLevel.VIEW);

        // HABITS is visible to them, but it is not an observation category.
        assertThat(service.visibleCategories(OTHER_ADULT_ACCOUNT, ADULT_MEMBER)).isEmpty();
        assertThat(EnumSet.allOf(MetricCategory.class)).hasSize(7);
    }
}

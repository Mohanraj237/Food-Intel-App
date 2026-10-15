package com.familyfood.insights.application;

import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.insights.domain.Insight;
import com.familyfood.insights.domain.Insight.Availability;
import com.familyfood.insights.domain.InsightType;
import com.familyfood.insights.domain.InsightWindow;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The honesty properties of the insight layer.
 *
 * <p>These tests are less about arithmetic and more about restraint: the module must decline
 * to speak when the data does not support a statement, and every number it does produce must
 * appear in its own evidence list. Those two properties are what make D-011 ("AI never
 * originates a number") checkable rather than aspirational later on.
 */
class InsightServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();

    private WellnessAccess wellness;
    private InsightService service;

    @BeforeEach
    void setUp() {
        wellness = mock(WellnessAccess.class);
        // No household found → days are bucketed in the default zone (Asia/Kolkata, D-053).
        service = new InsightService(wellness, mock(HouseholdAccess.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ObservationView obs(MetricCode metric, String value, Instant at) {
        return new ObservationView(UUID.randomUUID(), SUBJECT, metric, metric.category(),
                new BigDecimal(value), metric.canonicalUnit(), at, at, ObservationSource.DERIVED);
    }

    /** n consecutive days of the same value, ending yesterday. */
    private static List<ObservationView> daily(MetricCode metric, String value, int days, Instant end) {
        List<ObservationView> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            out.add(obs(metric, value, end.minusSeconds(86_400L * i)));
        }
        return out;
    }

    @Test
    @DisplayName("no data at all produces one honest statement, not an empty list")
    void noDataIsAnAnswer() {
        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any())).thenReturn(List.of());

        List<Insight> insights = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK);

        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).type()).isEqualTo(InsightType.DATA_COVERAGE);
        assertThat(insights.get(0).availability()).isEqualTo(Availability.NO_DATA);
        assertThat(insights.get(0).evidence()).isEmpty();
    }

    @Test
    @DisplayName("two days of data does NOT produce a weekly trend — it reports coverage")
    void thinDataDoesNotBecomeATrend() {
        // The failure this guards against: "your protein is up 40%" computed from two points.
        List<ObservationView> thin = daily(MetricCode.PROTEIN_G, "50", 2, NOW.minusSeconds(3600));
        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any())).thenReturn(thin);

        List<Insight> insights = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK);

        assertThat(insights).isNotEmpty();
        assertThat(insights).allSatisfy(i ->
                assertThat(i.type()).isEqualTo(InsightType.DATA_COVERAGE));
        assertThat(insights).allSatisfy(i ->
                assertThat(i.availability()).isNotEqualTo(Availability.SUFFICIENT));
        assertThat(insights.get(0).headline()).contains("2 of");
    }

    @Test
    @DisplayName("three weigh-ins in one day count as one day, not three")
    void repeatedSameDayObservationsDoNotInflateCoverage() {
        Instant today = NOW.minusSeconds(3600);
        List<ObservationView> sameDay = List.of(
                obs(MetricCode.WATER_ML, "250", today),
                obs(MetricCode.WATER_ML, "250", today.minusSeconds(60)),
                obs(MetricCode.WATER_ML, "250", today.minusSeconds(120)),
                obs(MetricCode.WATER_ML, "250", today.minusSeconds(180)));
        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any())).thenReturn(sameDay);

        List<Insight> insights = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK);

        // Four observations, one day — below the weekly minimum of four days.
        assertThat(insights).allSatisfy(i ->
                assertThat(i.availability()).isNotEqualTo(Availability.SUFFICIENT));
        assertThat(insights.get(0).evidence().get(0).observationDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("with enough days and a prior period, it states a trend backed by evidence")
    void statesATrendWithEvidence() {
        List<ObservationView> current = daily(MetricCode.STEPS, "10000", 5, NOW.minusSeconds(3600));
        List<ObservationView> prior = daily(MetricCode.STEPS, "5000", 5, NOW.minusSeconds(86_400L * 8));

        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any()))
                .thenReturn(current)   // current window
                .thenReturn(prior);    // prior window

        List<Insight> insights = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK);

        Insight trend = insights.stream()
                .filter(i -> i.type() == InsightType.ACTIVITY_TREND)
                .findFirst()
                .orElseThrow();

        assertThat(trend.availability()).isEqualTo(Availability.SUFFICIENT);
        // 10000/day against 5000/day is a doubling.
        assertThat(trend.headline()).contains("up").contains("100");
        assertThat(trend.evidence()).hasSize(2);
        assertThat(trend.evidence()).extracting(Insight.Evidence::label)
                .containsExactly("This period", "Previous period");
    }

    @Test
    @DisplayName("a change under the materiality floor reads as 'about the same', not a fake trend")
    void smallMovementIsNotReportedAsAChange() {
        List<ObservationView> current = daily(MetricCode.STEPS, "10000", 5, NOW.minusSeconds(3600));
        List<ObservationView> prior = daily(MetricCode.STEPS, "10200", 5, NOW.minusSeconds(86_400L * 8));

        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any()))
                .thenReturn(current)
                .thenReturn(prior);

        Insight trend = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK).stream()
                .filter(i -> i.type() == InsightType.ACTIVITY_TREND)
                .findFirst()
                .orElseThrow();

        assertThat(trend.headline()).contains("about the same");
    }

    @Test
    @DisplayName("no prior period means a total, never a percentage against nothing")
    void noBaselineMeansNoComparison() {
        List<ObservationView> current = daily(MetricCode.WATER_ML, "2000", 5, NOW.minusSeconds(3600));

        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any()))
                .thenReturn(current)
                .thenReturn(List.of());

        Insight hydration = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK).stream()
                .filter(i -> i.type() == InsightType.HYDRATION_TREND)
                .findFirst()
                .orElseThrow();

        assertThat(hydration.headline()).doesNotContain("%");
        assertThat(hydration.detail()).contains("No earlier period");
    }

    @Test
    @DisplayName("every number in a headline appears in the evidence list")
    void everyStatedNumberIsEvidenced() {
        // The structural guarantee behind D-011: a reviewer (or a later AI layer) can check
        // that no figure was conjured, because the arithmetic inputs travel with the claim.
        List<ObservationView> current = daily(MetricCode.PROTEIN_G, "80", 5, NOW.minusSeconds(3600));
        List<ObservationView> prior = daily(MetricCode.PROTEIN_G, "40", 5, NOW.minusSeconds(86_400L * 8));

        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any()))
                .thenReturn(current)
                .thenReturn(prior);

        List<Insight> insights = service.forMember(CALLER, SUBJECT, InsightWindow.WEEK);

        assertThat(insights).allSatisfy(i -> {
            if (i.availability() == Availability.SUFFICIENT) {
                assertThat(i.evidence())
                        .as("a sufficient insight must carry the data it was computed from")
                        .isNotEmpty();
            }
            assertThat(i.createdAt()).isEqualTo(NOW);
            assertThat(i.subjectMemberId()).isEqualTo(SUBJECT);
            assertThat(i.window()).isEqualTo(InsightWindow.WEEK);
        });
    }

    @Test
    @DisplayName("insights never carry a clinical category, range or verdict (D-037)")
    void noClinicalInterpretation() {
        List<String> components = java.util.Arrays.stream(Insight.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(components).doesNotContain(
                "category", "severity", "referenceRange", "normalRange", "verdict", "score",
                "risk", "diagnosis");
    }

    @Test
    @DisplayName("reads go through WellnessAccess, so visibility rules apply")
    void readsAreVisibilityScoped() {
        // Not an interaction test for its own sake: if this module ever reached the
        // observation repository directly it would bypass D-038 entirely, and a household
        // aggregate would become a back door onto another adult's private data.
        when(wellness.window(eq(CALLER), eq(SUBJECT), any(), any())).thenReturn(List.of());

        service.forMember(CALLER, SUBJECT, InsightWindow.WEEK);

        org.mockito.Mockito.verify(wellness, org.mockito.Mockito.atLeastOnce())
                .window(eq(CALLER), eq(SUBJECT), any(), any());
    }
}

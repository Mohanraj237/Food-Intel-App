package com.familyfood.wellness.domain;

import com.familyfood.access.domain.AccessScope;
import com.familyfood.profile.domain.AgeBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two enums that must never drift apart (D-044): every metric category is an access scope of
 * the same name, and every metric states how a window of it becomes one number.
 *
 * <p>Iterates the enums rather than naming values, so a category added in two years without its
 * scope fails here — before a grant on it silently opens nothing, or a sum of weigh-ins ships.
 */
class MetricCategoryScopeParityTest {

    @ParameterizedTest
    @EnumSource(MetricCategory.class)
    @DisplayName("every metric category maps to a same-named, tracking-kind, offered access scope")
    void everyCategoryHasItsScope(MetricCategory category) {
        AccessScope scope = category.scope();

        assertThat(scope.name()).isEqualTo(category.name());
        assertThat(scope.isTracking()).as("%s is tracking data (D-048)", scope).isTrue();
        assertThat(scope.kind()).isEqualTo(AccessScope.Kind.TRACKING);
        assertThat(scope.isOffered()).as("%s can be shared today", scope).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MetricCategory.class)
    @DisplayName("MetricCategory.of(scope) round-trips")
    void categoryRoundTrips(MetricCategory category) {
        assertThat(MetricCategory.of(category.scope())).contains(category);
    }

    @Test
    @DisplayName("scopes that are not metric categories map to no category")
    void nonCategoryScopesMapToNothing() {
        for (AccessScope scope : AccessScope.values()) {
            boolean isCategory = Arrays.stream(MetricCategory.values()).anyMatch(c -> c.name().equals(scope.name()));
            assertThat(MetricCategory.of(scope).isPresent()).as("%s", scope).isEqualTo(isCategory);
        }
        assertThat(MetricCategory.of(AccessScope.HABITS)).isEmpty();
        assertThat(MetricCategory.of(AccessScope.MEDICATIONS)).isEmpty();
    }

    @Test
    @DisplayName("aggregation: weight LATEST, water SUM, bedtime LATEST, blood pressure LATEST")
    void aggregationPerMetric() {
        assertThat(MetricCode.WEIGHT_KG.aggregation()).isEqualTo(MetricAggregation.LATEST);
        assertThat(MetricCode.WATER_ML.aggregation()).isEqualTo(MetricAggregation.SUM);
        assertThat(MetricCode.BEDTIME_OFFSET_MIN.aggregation()).isEqualTo(MetricAggregation.LATEST);
        assertThat(MetricCode.WAKE_OFFSET_MIN.aggregation()).isEqualTo(MetricAggregation.LATEST);
        assertThat(MetricCode.SYSTOLIC_MMHG.aggregation()).isEqualTo(MetricAggregation.LATEST);
        assertThat(MetricCode.STEPS.aggregation()).isEqualTo(MetricAggregation.SUM);
        assertThat(MetricCode.SLEEP_MINUTES.aggregation()).isEqualTo(MetricAggregation.SUM);
        assertThat(MetricCode.ENERGY_KCAL.aggregation()).isEqualTo(MetricAggregation.SUM);
        assertThat(MetricCode.MOOD_SCORE.aggregation()).isEqualTo(MetricAggregation.LATEST);
    }

    @ParameterizedTest
    @EnumSource(MetricCode.class)
    @DisplayName("no point-in-time reading is ever summed")
    void bodyClinicalAndWellbeingAreNeverSummed(MetricCode metric) {
        // Two weigh-ins of 72 kg are not 144 kg; two moods of 3 are not a mood of 6.
        MetricCategory c = metric.category();
        if (c == MetricCategory.BODY || c == MetricCategory.CLINICAL || c == MetricCategory.WELLBEING) {
            assertThat(metric.aggregation()).as("%s", metric).isEqualTo(MetricAggregation.LATEST);
        }
        assertThat(metric.aggregation()).as("%s declares an aggregation", metric).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(AgeBand.class)
    @DisplayName("AgeBand.isChild() is true exactly for the C* bands")
    void isChildExactlyForChildBands(AgeBand band) {
        assertThat(band.isChild()).as("%s", band).isEqualTo(band.name().startsWith("C"));
        assertThat(MetricCode.isChildBand(band)).isEqualTo(band.isChild());
    }

    @Test
    @DisplayName("only A60_PLUS is an older-adult band, and it is never a child band")
    void olderAdultBand() {
        for (AgeBand band : AgeBand.values()) {
            assertThat(band.isOlderAdult()).as("%s", band).isEqualTo(band == AgeBand.A60_PLUS);
        }
        assertThat(AgeBand.A60_PLUS.isChild()).isFalse();
    }
}

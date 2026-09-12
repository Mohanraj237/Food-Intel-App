package com.familyfood.wellness.domain;

import com.familyfood.profile.domain.AgeBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The D-036 age gate, tested as a property of the taxonomy rather than of one service.
 *
 * <p>This is the test that has to keep passing when someone adds a metric in two years. It
 * deliberately iterates the whole enum instead of naming specific metrics, so a newly added
 * body or clinical metric that forgets its age class fails here rather than shipping.
 */
class MetricCodeAgeGateTest {

    private static final List<AgeBand> CHILD_BANDS = List.of(
            AgeBand.C1_3, AgeBand.C4_6, AgeBand.C7_9, AgeBand.C10_12, AgeBand.C13_17);

    private static final List<AgeBand> ADULT_BANDS = List.of(
            AgeBand.A18_30, AgeBand.A31_45, AgeBand.A46_60, AgeBand.A60_PLUS);

    @Test
    @DisplayName("no BODY metric is permitted for any child band — the child-safety boundary")
    void bodyMetricsAreNeverPermittedForChildren() {
        List<MetricCode> bodyMetrics = Arrays.stream(MetricCode.values())
                .filter(m -> m.category() == MetricCategory.BODY)
                .toList();

        // Guards against the whole category being deleted and the test passing vacuously.
        assertThat(bodyMetrics).isNotEmpty();

        for (MetricCode metric : bodyMetrics) {
            for (AgeBand band : CHILD_BANDS) {
                assertThat(metric.isPermittedFor(band))
                        .as("%s must be refused for child band %s (D-036: no weight, "
                          + "body-fat or body-composition data for a minor)", metric, band)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("height is adult-only too, closing the growth-percentile side channel")
    void heightIsAdultOnlySoGrowthTrackingCannotArriveByAccident() {
        // Recording a child's height and weight is how paediatric growth tracking would
        // sneak in through two individually innocuous metrics. D-036 puts that out of scope
        // until a paediatric standard and clinical review exist.
        assertThat(MetricCode.HEIGHT_CM.category()).isEqualTo(MetricCategory.BODY);
        for (AgeBand band : CHILD_BANDS) {
            assertThat(MetricCode.HEIGHT_CM.isPermittedFor(band)).isFalse();
        }
    }

    @Test
    @DisplayName("no CLINICAL metric is permitted for a child")
    void clinicalMetricsAreNeverPermittedForChildren() {
        List<MetricCode> clinical = Arrays.stream(MetricCode.values())
                .filter(m -> m.category() == MetricCategory.CLINICAL)
                .toList();

        assertThat(clinical).isNotEmpty();

        for (MetricCode metric : clinical) {
            for (AgeBand band : CHILD_BANDS) {
                assertThat(metric.isPermittedFor(band))
                        .as("%s must be refused for child band %s", metric, band)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("every adult-only metric is permitted for every adult band")
    void adultOnlyMetricsWorkForAllAdultBands() {
        // The mirror of the rule above: D-036 supersedes D-017 *for adults*, so an adult
        // managing their own weight must actually work — including at 60+.
        for (MetricCode metric : MetricCode.values()) {
            if (metric.permittedFor() != MetricCode.AgeClass.ADULT_ONLY) continue;
            for (AgeBand band : ADULT_BANDS) {
                assertThat(metric.isPermittedFor(band))
                        .as("%s should be permitted for adult band %s", metric, band)
                        .isTrue();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MetricCode.class)
    @DisplayName("every metric declares a category, a canonical unit and an age class")
    void everyMetricIsFullyDeclared(MetricCode metric) {
        assertThat(metric.category()).as("%s category", metric).isNotNull();
        assertThat(metric.canonicalUnit()).as("%s unit", metric).isNotBlank();
        assertThat(metric.permittedFor()).as("%s age class", metric).isNotNull();
    }

    @Test
    @DisplayName("activity, sleep, hydration and nutrition are recordable for children")
    void childFriendlyCategoriesArePermitted() {
        // A child's steps and sleep are legitimate and in scope. What stays forbidden is the
        // engagement layer on top of them — streaks, badges, ranking, notifications
        // (D-036, CH-004) — which lives outside this enum.
        List<MetricCategory> childSafe = List.of(
                MetricCategory.ACTIVITY, MetricCategory.SLEEP,
                MetricCategory.HYDRATION, MetricCategory.NUTRITION);

        for (MetricCode metric : MetricCode.values()) {
            if (!childSafe.contains(metric.category())) continue;
            assertThat(metric.isPermittedFor(AgeBand.C7_9))
                    .as("%s should be recordable for a child", metric)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("no metric carries a threshold, reference range or category (D-037)")
    void metricsCarryNoInterpretation() {
        // Interpretation needs a citable reviewed standard. If this enum ever grows a
        // "normalRange" or "category" accessor, that decision was not taken.
        List<String> accessorNames = Arrays.stream(MetricCode.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(accessorNames)
                .as("D-037: storing a value is a record; naming it 'high' is a clinical claim")
                .doesNotContain("normalRange", "referenceRange", "threshold",
                                "classify", "interpret", "severity");
    }
}

package com.familyfood.wellness.domain;

import com.familyfood.profile.domain.AgeBand;

/**
 * The closed set of measurements this product will store, and — for each one — the age
 * classes it may be stored for.
 *
 * <p><b>The age set is a constructor parameter on purpose (D-036).</b> The safety rule that
 * matters most here is that no body or clinical metric is ever recorded for a child, and the
 * fragile way to enforce that is an {@code if} in whichever service happens to write an
 * observation. Putting {@code permittedFor} on the metric definition means a new metric
 * cannot be added without deciding, because the code will not compile until it is stated,
 * and every write path inherits the check for free.
 *
 * <p>Note what this makes structurally impossible rather than merely discouraged:
 * {@link #HEIGHT_CM} is adult-only alongside {@link #WEIGHT_KG}, which closes the obvious
 * side-channel of recording a child's height and weight to derive a growth percentile.
 * Paediatric growth tracking is out of scope until a paediatric standard and clinical review
 * exist (D-036), and it must not arrive by accident through two innocuous-looking metrics.
 *
 * <p>Interpretation is a separate question with a higher bar. A value may be stored; naming
 * it "high" or "elevated" is a clinical claim and requires a citable reviewed standard
 * (D-037). Nothing in this enum carries a threshold, a reference range or a category.
 */
public enum MetricCode {

    // ---------------------------------------------------------------- BODY (adults only)
    // D-036 supersedes D-017 for adults: an adult managing their own weight is a legitimate,
    // expected capability. For a child subject these are refused in the domain, not hidden
    // in the UI — see WellnessService.
    WEIGHT_KG(MetricCategory.BODY, "kg", AgeClass.ADULT_ONLY),
    HEIGHT_CM(MetricCategory.BODY, "cm", AgeClass.ADULT_ONLY),
    BMI(MetricCategory.BODY, "kg/m2", AgeClass.ADULT_ONLY),
    WAIST_CM(MetricCategory.BODY, "cm", AgeClass.ADULT_ONLY),
    BODY_FAT_PCT(MetricCategory.BODY, "%", AgeClass.ADULT_ONLY),

    // ------------------------------------------------------------------------- ACTIVITY
    // Permitted for a child as a measurement. What remains forbidden for a child is the
    // engagement layer on top — streaks, badges, ranking, notifications (D-036, CH-004).
    STEPS(MetricCategory.ACTIVITY, "steps", AgeClass.ANY),
    ACTIVE_MINUTES(MetricCategory.ACTIVITY, "min", AgeClass.ANY),
    DISTANCE_KM(MetricCategory.ACTIVITY, "km", AgeClass.ANY),
    WORKOUT_MINUTES(MetricCategory.ACTIVITY, "min", AgeClass.ANY),

    // ---------------------------------------------------------------------------- SLEEP
    SLEEP_MINUTES(MetricCategory.SLEEP, "min", AgeClass.ANY),
    /** Minutes after midnight, so that 23:30 is -30 and 00:30 is 30 — a wrap-safe scalar
     *  that averages correctly across a week, which a clock time does not. */
    BEDTIME_OFFSET_MIN(MetricCategory.SLEEP, "min", AgeClass.ANY),
    WAKE_OFFSET_MIN(MetricCategory.SLEEP, "min", AgeClass.ANY),

    // ------------------------------------------------------------------------ HYDRATION
    WATER_ML(MetricCategory.HYDRATION, "ml", AgeClass.ANY),

    // ------------------------------------------------------------------------ NUTRITION
    // Written by Phase 2 meal logging rather than entered by hand, but they are
    // observations like any other so that insights have one source to read.
    ENERGY_KCAL(MetricCategory.NUTRITION, "kcal", AgeClass.ANY),
    PROTEIN_G(MetricCategory.NUTRITION, "g", AgeClass.ANY),
    CARBOHYDRATE_G(MetricCategory.NUTRITION, "g", AgeClass.ANY),
    FAT_G(MetricCategory.NUTRITION, "g", AgeClass.ANY),
    FIBRE_G(MetricCategory.NUTRITION, "g", AgeClass.ANY),
    SUGAR_G(MetricCategory.NUTRITION, "g", AgeClass.ANY),
    SODIUM_MG(MetricCategory.NUTRITION, "mg", AgeClass.ANY),

    // ----------------------------------------------------------- CLINICAL (adults only)
    // Capture is permitted for an adult: it is their own reading off their own device, and
    // refusing to store it just sends them to a worse tool (D-037). Interpretation is not
    // permitted for anyone without a reviewed standard.
    SYSTOLIC_MMHG(MetricCategory.CLINICAL, "mmHg", AgeClass.ADULT_ONLY),
    DIASTOLIC_MMHG(MetricCategory.CLINICAL, "mmHg", AgeClass.ADULT_ONLY),
    RESTING_HEART_RATE_BPM(MetricCategory.CLINICAL, "bpm", AgeClass.ADULT_ONLY),
    BLOOD_GLUCOSE_MGDL(MetricCategory.CLINICAL, "mg/dL", AgeClass.ADULT_ONLY),
    SPO2_PCT(MetricCategory.CLINICAL, "%", AgeClass.ADULT_ONLY),
    BODY_TEMPERATURE_C(MetricCategory.CLINICAL, "C", AgeClass.ADULT_ONLY),

    // ------------------------------------------------------------------------ WELLBEING
    /** Self-report on a 1-5 scale. Self-report only — never inferred from other metrics. */
    MOOD_SCORE(MetricCategory.WELLBEING, "score", AgeClass.ANY),
    STRESS_SCORE(MetricCategory.WELLBEING, "score", AgeClass.ANY);

    /** Which age classes a metric may be recorded for. */
    public enum AgeClass {
        /** Adults and children alike. */
        ANY,
        /** Adults only — refused in the domain for any child age band (D-036). */
        ADULT_ONLY
    }

    private final MetricCategory category;
    private final String canonicalUnit;
    private final AgeClass permittedFor;

    MetricCode(MetricCategory category, String canonicalUnit, AgeClass permittedFor) {
        this.category = category;
        this.canonicalUnit = canonicalUnit;
        this.permittedFor = permittedFor;
    }

    public MetricCategory category() {
        return category;
    }

    /**
     * The one unit this metric is stored in. Conversion from whatever the user typed happens
     * at the edge; storing mixed units would make every trend and every sum wrong in a way
     * that is invisible until someone switches units mid-history.
     */
    public String canonicalUnit() {
        return canonicalUnit;
    }

    public AgeClass permittedFor() {
        return permittedFor;
    }

    /**
     * How a window of this metric is reduced to one number. Accumulating categories sum;
     * point-in-time readings take the latest; clock-like sleep offsets take the latest because
     * a total of bedtimes is noise.
     */
    public MetricAggregation aggregation() {
        return switch (this) {
            case BEDTIME_OFFSET_MIN, WAKE_OFFSET_MIN -> MetricAggregation.LATEST;
            default -> switch (category) {
                case NUTRITION, HYDRATION, ACTIVITY, SLEEP -> MetricAggregation.SUM;
                case BODY, CLINICAL, WELLBEING -> MetricAggregation.LATEST;
            };
        };
    }

    /**
     * Whether this metric may be recorded for a subject in the given age band.
     *
     * <p>The subject's band, never the actor's: a guardian recording their own weight is
     * fine, and the same guardian recording a child's weight is the thing we refuse.
     */
    public boolean isPermittedFor(AgeBand subjectBand) {
        return permittedFor == AgeClass.ANY || !isChildBand(subjectBand);
    }

    /**
     * Whether a band is a child band. Delegates to {@link AgeBand#isChild()}, which states it per
     * constant — one definition for every age gate in the product (D-036, D-046, D-047), rather
     * than a second list here that could fall out of step with it.
     */
    public static boolean isChildBand(AgeBand band) {
        return band != null && band.isChild();
    }
}

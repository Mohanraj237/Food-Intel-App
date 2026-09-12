package com.familyfood.nutrition.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The seven amounts a meal item records, as stored on {@code meal_item}.
 *
 * <p><b>{@code null} means unknown, never zero.</b> That distinction is the whole reason this
 * is a record of nullable {@link BigDecimal}s rather than a map with defaults: a home-cooked
 * dal nobody has a fibre figure for must not end up asserting "0 g of fibre", because a
 * zero is a claim about the food and an absent value is a claim about our data. Every
 * operation here preserves absence.
 *
 * <p>{@code BigDecimal} throughout for the same reason {@code HealthObservation} uses it —
 * the columns are {@code numeric(10,2)}, and a float sum over a week of meals drifts by
 * enough to be visible in the total.
 */
public record NutritionAmounts(BigDecimal energyKcal,
                               BigDecimal proteinG,
                               BigDecimal carbohydrateG,
                               BigDecimal fatG,
                               BigDecimal fibreG,
                               BigDecimal sugarG,
                               BigDecimal sodiumMg) {

    /** Nothing known about this food at all — a valid, common state, not an error. */
    public static final NutritionAmounts NONE =
            new NutritionAmounts(null, null, null, null, null, null, null);

    /** True when not one of the seven amounts is known. */
    public boolean isAbsent() {
        return energyKcal == null && proteinG == null && carbohydrateG == null && fatG == null
                && fibreG == null && sugarG == null && sodiumMg == null;
    }

    /** True when any present amount is negative — no amount here is ever meaningfully below zero. */
    public boolean hasNegativeAmount() {
        return isNegative(energyKcal) || isNegative(proteinG) || isNegative(carbohydrateG)
                || isNegative(fatG) || isNegative(fibreG) || isNegative(sugarG) || isNegative(sodiumMg);
    }

    /**
     * Every known amount multiplied by {@code factor} and rounded to {@code scale}; an unknown
     * amount stays unknown.
     *
     * <p>Rounding happens here rather than being left to the database so that the value this
     * code computed is the value that gets stored. A silent truncation on insert would make a
     * stored total disagree with the same arithmetic run again in memory.
     */
    public NutritionAmounts scaledTo(BigDecimal factor, int scale) {
        return new NutritionAmounts(
                scale(energyKcal, factor, scale),
                scale(proteinG, factor, scale),
                scale(carbohydrateG, factor, scale),
                scale(fatG, factor, scale),
                scale(fibreG, factor, scale),
                scale(sugarG, factor, scale),
                scale(sodiumMg, factor, scale));
    }

    /**
     * Adds two sets of amounts.
     *
     * <p>An unknown amount on one side contributes nothing rather than poisoning the total to
     * unknown. A dinner of rice (fully labelled) and a home-made side (no figures) has a known
     * protein total from the rice, and discarding it would throw away the one reliable thing we
     * have; the total is then a floor, which is the honest reading of a partially labelled meal.
     * An amount absent on both sides stays absent.
     */
    public NutritionAmounts plus(NutritionAmounts other) {
        return new NutritionAmounts(
                add(energyKcal, other.energyKcal),
                add(proteinG, other.proteinG),
                add(carbohydrateG, other.carbohydrateG),
                add(fatG, other.fatG),
                add(fibreG, other.fibreG),
                add(sugarG, other.sugarG),
                add(sodiumMg, other.sodiumMg));
    }

    private static BigDecimal scale(BigDecimal amount, BigDecimal factor, int scale) {
        return amount == null ? null : amount.multiply(factor).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal add(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        return right == null ? left : left.add(right);
    }

    private static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.signum() < 0;
    }
}

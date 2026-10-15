package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.FoodType;
import com.familyfood.scoring.domain.ScoreComponent;
import com.familyfood.scoring.domain.ScoreComponentRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code POINTS_NET_V2}: the FSSAI draft Indian Nutrition Rating points method
 * (evidence/source-002, Schedule III; scoring-methodology.md §2), D-059.
 *
 * <ol>
 *   <li>Each {@link ScoreComponentRole#BASELINE} component (energy, saturated fat, total
 *       sugars, sodium) places the product's per-100 value in its transcribed band table
 *       ({@link BandTable}) — the points add up to the <em>baseline points</em>. Any baseline
 *       factor undeclared → the whole score is {@code INSUFFICIENT_DATA}; nothing is assumed.
 *   <li>Each {@link ScoreComponentRole#MODIFYING} component (fruit/veg/nut/legume %, fibre,
 *       protein) places its value likewise, then the standard's capping rule (the band
 *       table's {@code cap}, read against the baseline points) limits it. An undeclared
 *       modifying factor scores 0 and is reported as {@code ASSUMED_ZERO} (methodology §2.4 —
 *       an interpretation the reviewer may reject).
 *   <li>{@code net = baseline − Σ capped modifying}; the star band comes from the rule set's
 *       {@code algorithm_config} ({@link StarBands}); the 0–100 number is a display rescale of
 *       the net points between the table's own top and bottom edges.
 * </ol>
 *
 * <p>{@link FoodType#FAT_OIL} products get {@code INSUFFICIENT_DATA} with
 * {@code SCORE_NOT_APPLICABLE}: the cited standard exempts fats and oils from a rating, so
 * no number is produced rather than a fabricated one. Only per-100 values are used — the
 * band tables are defined per 100 g / 100 mL and a per-serving fallback would compare
 * against the wrong scale.
 *
 * <p>Free of any repository call, so it is unit-testable with hand-built components and
 * configuration ({@code PointsNetAlgorithmTest}).
 */
public final class PointsNetAlgorithm {

    static final String EXPLANATION_KEY = "score.driver";
    private static final String DIRECTION_NEGATIVE = "NEGATIVE";
    private static final String DIRECTION_POSITIVE = "POSITIVE";

    private PointsNetAlgorithm() {
    }

    public static ProductHealthView score(ProductAssessmentInput input, String ruleSetVersion,
                                          Map<String, Object> algorithmConfig,
                                          List<ScoreComponent> componentsInScope, Instant now) {
        FoodType foodType = input.foodType() == null ? FoodType.SOLID : FoodType.valueOf(input.foodType());
        ProcessingLevel processingLevel = processingLevelOf(input);
        String basis = input.basisPer100() == null ? "PER_100G" : input.basisPer100();

        if (foodType == FoodType.FAT_OIL) {
            String standardRef = componentsInScope.isEmpty() ? null : componentsInScope.get(0).getStandardRef();
            return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                    ProductHealthView.STATUS_INSUFFICIENT_DATA, null, null, null,
                    ProductHealthView.SCHEME_POINTS_NET_V2, foodType.name(), List.of(), processingLevel,
                    Coverage.empty(),
                    List.of(new Reason(Reason.SCORE_NOT_APPLICABLE, null, null, null, null, standardRef)), now);
        }

        List<ScoreComponent> components = componentsInScope.stream()
                .filter(c -> c.getFoodTypeScope() == null || c.getFoodTypeScope() == foodType)
                .filter(c -> c.getCategoryScope() == null || c.getCategoryScope().equals(input.categoryCode()))
                .toList();
        if (components.isEmpty()) {
            return ProductHealthView.notYetScored(input.productVersionId(), ruleSetVersion, processingLevel, now);
        }

        Map<String, NutrientFact> factsByCode = new LinkedHashMap<>();
        for (NutrientFact fact : input.nutrients()) {
            factsByCode.put(fact.nutrientCode(), fact);
        }

        List<String> required = new ArrayList<>();
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<Reason> reasons = new ArrayList<>();
        List<ScoreDriver> drivers = new ArrayList<>();

        // --- baseline -------------------------------------------------------------------
        int baselinePoints = 0;
        boolean baselineMissing = false;
        for (ScoreComponent component : components) {
            if (component.getRole() != ScoreComponentRole.BASELINE) {
                continue;
            }
            required.add(component.getNutrientCode());
            NutrientFact fact = factsByCode.get(component.getNutrientCode());
            BigDecimal value = fact == null ? null : fact.amountPer100();
            if (value == null) {
                missing.add(component.getNutrientCode());
                baselineMissing = true;
                reasons.add(new Reason(Reason.MISSING_NUTRIENT_DATA, component.getNutrientCode(),
                        basis, null, null, component.getStandardRef()));
                continue;
            }
            present.add(component.getNutrientCode());
            BandTable table = BandTable.from(component.getThresholds());
            BandTable.Placement placement = table.place(value);
            baselinePoints += placement.points();
            drivers.add(new ScoreDriver(component.getCode(), DIRECTION_NEGATIVE, component.getNutrientCode(),
                    value, fact.unit(), basis, placement.points(), table.maxPoints(), placement.threshold(),
                    component.getStandardRef(), EXPLANATION_KEY, fact.estimated(), false));
            reasons.add(new Reason(Reason.POINTS_BAND, component.getNutrientCode(), basis, value,
                    placement.threshold(), component.getStandardRef()));
        }

        if (baselineMissing) {
            // Modifying factors are still reported as required so coverage is complete.
            for (ScoreComponent component : components) {
                if (component.getRole() == ScoreComponentRole.MODIFYING) {
                    required.add(component.getNutrientCode());
                    NutrientFact fact = factsByCode.get(component.getNutrientCode());
                    (fact != null && fact.amountPer100() != null ? present : missing).add(component.getNutrientCode());
                }
            }
            return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                    ProductHealthView.STATUS_INSUFFICIENT_DATA, null, null, null,
                    ProductHealthView.SCHEME_POINTS_NET_V2, foodType.name(), List.copyOf(drivers), processingLevel,
                    new Coverage(List.copyOf(required), List.copyOf(present), List.copyOf(missing)),
                    List.copyOf(reasons), now);
        }

        // --- modifying, capped ------------------------------------------------------------
        int modifyingPoints = 0;
        for (ScoreComponent component : components) {
            if (component.getRole() != ScoreComponentRole.MODIFYING) {
                continue;
            }
            required.add(component.getNutrientCode());
            BandTable table = BandTable.from(component.getThresholds());
            NutrientFact fact = factsByCode.get(component.getNutrientCode());
            BigDecimal value = fact == null ? null : fact.amountPer100();
            if (value == null) {
                missing.add(component.getNutrientCode());
                drivers.add(new ScoreDriver(component.getCode(), DIRECTION_POSITIVE, component.getNutrientCode(),
                        null, null, basis, 0, table.maxPoints(), null, component.getStandardRef(),
                        EXPLANATION_KEY, false, true));
                reasons.add(new Reason(Reason.ASSUMED_ZERO, component.getNutrientCode(), basis, null, null,
                        component.getStandardRef()));
                continue;
            }
            present.add(component.getNutrientCode());
            BandTable.Placement placement = table.place(value);
            int points = placement.points();
            if (table.cap() != null) {
                points = Math.min(points, table.cap().limitFor(baselinePoints));
            }
            modifyingPoints += points;
            drivers.add(new ScoreDriver(component.getCode(), DIRECTION_POSITIVE, component.getNutrientCode(),
                    value, fact.unit(), basis, points, table.maxPoints(), placement.threshold(),
                    component.getStandardRef(), EXPLANATION_KEY, fact.estimated(), false));
            reasons.add(new Reason(Reason.POINTS_BAND, component.getNutrientCode(), basis, value,
                    placement.threshold(), component.getStandardRef()));
        }

        int net = baselinePoints - modifyingPoints;
        StarBands starBands = StarBands.from(algorithmConfig, foodType);
        BigDecimal stars = starBands.starsFor(net);
        int score = starBands.scoreFor(net);

        return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                ProductHealthView.STATUS_SCORED, score, WeightedMeanAlgorithm.gradeFor(score), stars,
                ProductHealthView.SCHEME_POINTS_NET_V2, foodType.name(), List.copyOf(drivers), processingLevel,
                new Coverage(List.copyOf(required), List.copyOf(present), List.copyOf(missing)),
                List.copyOf(reasons), now);
    }

    static ProcessingLevel processingLevelOf(ProductAssessmentInput input) {
        if (input.novaGroup() == null) {
            return null;
        }
        String source = input.novaSource();
        boolean estimated = source == null || "OPEN_FOOD_FACTS".equals(source);
        return new ProcessingLevel(input.novaGroup(), estimated, source);
    }
}

package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Direction;
import com.familyfood.scoring.domain.FoodType;
import com.familyfood.scoring.domain.ScoreComponent;
import com.familyfood.scoring.domain.ScoreComponentRole;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hand-built {@code POINTS_NET_V2} components and configuration for tests, transcribed from
 * {@code docs/data/scoring/evidence/source-002-fssai-inr-draft-2022.md} (Schedule III,
 * Tables 2–4 and 6). These are test fixtures proving the engine mechanism against the
 * transcribed tables; they are never persisted under any real rule set, and nothing here
 * activates anything (D-025 — the reviewed content arrives in V17/V18 after
 * {@code review.md} is signed).
 */
final class PointsNetFixtures {

    static final String STANDARD = "FSSAI_INR";

    private PointsNetFixtures() {
    }

    // ---- Table 2 (solid, per 100 g) ---------------------------------------------------

    static Map<String, Object> solidEnergyBands() {
        return bands(new double[]{80, 160, 240, 320, 400, 480, 560, 640, 720, 800}, null);
    }

    static Map<String, Object> solidSatFatBands() {
        return bands(new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7, 8, 9, 10, 12, 14, 16, 18, 20,
                22, 24, 26, 28, 30, 32, 34, 36, 38, 40}, null);
    }

    static Map<String, Object> solidTotalSugarBands() {
        return bands(new double[]{4.2, 8.4, 12.6, 16.8, 21, 25.2, 29.4, 33.6, 37.8, 42, 46.2, 50.4,
                54.6, 58.8, 63, 67.2, 71.4, 75.6, 79.8, 84}, null);
    }

    static Map<String, Object> solidSodiumBands() {
        return bands(new double[]{90, 180, 270, 360, 450, 540, 630, 720, 810, 900, 990, 1080, 1170,
                1260, 1350, 1440, 1530, 1620, 1710, 1800, 1890, 1980, 2070, 2160, 2250}, null);
    }

    /** FV%, solid — cap per Table 4: baseline ≤ 20 → 10, > 20 → 5. */
    static Map<String, Object> solidFvBands() {
        return bands(new double[]{10, 15, 20, 25, 30, 35, 40, 45, 50, 55}, cap(20, 10, 5));
    }

    /** Dietary fibre, solid — cap per Table 4: baseline ≤ 20 → 10, > 20 → 5. */
    static Map<String, Object> solidFibreBands() {
        return bands(new double[]{3, 6, 9, 12, 15, 18, 21, 24, 27, 30}, cap(20, 10, 5));
    }

    /** Protein, solid — cap per Table 4: baseline ≤ 20 → 15, > 20 → 7. */
    static Map<String, Object> solidProteinBands() {
        return bands(new double[]{1.5, 2.0, 2.5, 3.0, 5, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50}, cap(20, 15, 7));
    }

    // ---- Table 3 (liquid, per 100 mL) -------------------------------------------------

    static Map<String, Object> liquidEnergyBands() {
        return bands(new double[]{6, 12, 18, 24, 30, 36, 42, 48, 54, 60}, null);
    }

    static Map<String, Object> liquidTotalSugarBands() {
        return bands(new double[]{0.1, 1.6, 3.1, 4.6, 6.1, 7.6, 9.1, 10.6, 12.1, 13.6}, null);
    }

    /** FV%, liquid — cap per Table 4: baseline ≤ 10 → 10, > 10 → 5. */
    static Map<String, Object> liquidFvBands() {
        return bands(new double[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 55}, cap(10, 10, 5));
    }

    /** Protein, liquid — cap per Table 4: baseline ≤ 10 → 15, > 10 → 7. */
    static Map<String, Object> liquidProteinBands() {
        return bands(new double[]{1.5, 2.0, 2.5, 3.0, 5, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50}, cap(10, 15, 7));
    }

    // ---- Table 6 (star bands) -----------------------------------------------------------

    static Map<String, Object> algorithmConfig() {
        return Map.of(
                "starBandsSolid", List.of(
                        band(null, -11, 5), band(-10, -7, 4.5), band(-6, -2, 4), band(-1, 2, 3.5),
                        band(3, 6, 3), band(7, 11, 2.5), band(12, 15, 2), band(16, 20, 1.5),
                        band(21, 24, 1), band(25, null, 0.5)),
                "starBandsLiquid", List.of(
                        band(null, 0, 5), band(1, 2, 4.5), band(3, 4, 4), band(5, 6, 3.5),
                        band(7, 9, 3), band(10, 12, 2.5), band(13, 15, 2), band(16, 18, 1.5),
                        band(18, 20, 1), band(20, null, 0.5)));
    }

    // ---- component sets --------------------------------------------------------------

    static List<ScoreComponent> solidComponents(UUID ruleSetId) {
        return List.of(
                baseline(ruleSetId, "INR_SOLID_ENERGY", "ENERGY_KCAL", solidEnergyBands(), FoodType.SOLID),
                baseline(ruleSetId, "INR_SOLID_SATFAT", "SAT_FAT_G", solidSatFatBands(), FoodType.SOLID),
                baseline(ruleSetId, "INR_SOLID_TOTALSUGAR", "TOTAL_SUGAR_G", solidTotalSugarBands(), FoodType.SOLID),
                baseline(ruleSetId, "INR_SOLID_SODIUM", "SODIUM_MG", solidSodiumBands(), FoodType.SOLID),
                modifying(ruleSetId, "INR_SOLID_FV", "FVNL_PCT", solidFvBands(), FoodType.SOLID),
                modifying(ruleSetId, "INR_SOLID_FIBRE", "FIBRE_G", solidFibreBands(), FoodType.SOLID),
                modifying(ruleSetId, "INR_SOLID_PROTEIN", "PROTEIN_G", solidProteinBands(), FoodType.SOLID));
    }

    static List<ScoreComponent> liquidComponents(UUID ruleSetId) {
        return List.of(
                baseline(ruleSetId, "INR_LIQUID_ENERGY", "ENERGY_KCAL", liquidEnergyBands(), FoodType.LIQUID),
                baseline(ruleSetId, "INR_LIQUID_TOTALSUGAR", "TOTAL_SUGAR_G", liquidTotalSugarBands(), FoodType.LIQUID),
                modifying(ruleSetId, "INR_LIQUID_FV", "FVNL_PCT", liquidFvBands(), FoodType.LIQUID),
                modifying(ruleSetId, "INR_LIQUID_PROTEIN", "PROTEIN_G", liquidProteinBands(), FoodType.LIQUID));
    }

    static List<ScoreComponent> allComponents(UUID ruleSetId) {
        List<ScoreComponent> all = new ArrayList<>(solidComponents(ruleSetId));
        all.addAll(liquidComponents(ruleSetId));
        return all;
    }

    static ScoreComponent baseline(UUID ruleSetId, String code, String nutrient, Map<String, Object> thresholds,
                                   FoodType scope) {
        return ScoreComponent.create(ruleSetId, code, nutrient, Direction.NEGATIVE, BigDecimal.ONE, thresholds,
                null, STANDARD, ScoreComponentRole.BASELINE, scope);
    }

    static ScoreComponent modifying(UUID ruleSetId, String code, String nutrient, Map<String, Object> thresholds,
                                    FoodType scope) {
        return ScoreComponent.create(ruleSetId, code, nutrient, Direction.POSITIVE, BigDecimal.ONE, thresholds,
                null, STANDARD, ScoreComponentRole.MODIFYING, scope);
    }

    // ---- helpers ----------------------------------------------------------------------

    static Map<String, Object> bands(double[] edges, Map<String, Object> cap) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < edges.length; i++) {
            list.add(Map.of("gt", BigDecimal.valueOf(edges[i]), "points", i + 1));
        }
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("type", "BANDS");
        thresholds.put("bands", list);
        if (cap != null) {
            thresholds.put("cap", cap);
        }
        return thresholds;
    }

    static Map<String, Object> cap(int baselineLte, int within, int above) {
        return Map.of("baselineLte", baselineLte, "within", within, "above", above);
    }

    static Map<String, Object> band(Integer gte, Integer lte, double stars) {
        Map<String, Object> band = new LinkedHashMap<>();
        if (gte != null) {
            band.put("gte", gte);
        }
        if (lte != null) {
            band.put("lte", lte);
        }
        band.put("stars", BigDecimal.valueOf(stars));
        return band;
    }

    static NutrientFact fact(String code, String per100) {
        return new NutrientFact(code, null, new BigDecimal(per100), unitOf(code), false);
    }

    static NutrientFact estimatedFact(String code, String per100) {
        return new NutrientFact(code, null, new BigDecimal(per100), unitOf(code), true);
    }

    private static String unitOf(String code) {
        if (code.endsWith("_MG")) {
            return "mg";
        }
        if (code.endsWith("_PCT")) {
            return "%";
        }
        return code.equals("ENERGY_KCAL") ? "kcal" : "g";
    }

    static ProductAssessmentInput input(String category, FoodType foodType, String basis, Integer nova,
                                        NutrientFact... facts) {
        return new ProductAssessmentInput(UUID.randomUUID(), category, List.of(facts), List.of(), basis, nova,
                nova == null ? null : "OPEN_FOOD_FACTS", foodType == null ? null : foodType.name(), Map.of());
    }
}

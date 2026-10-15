package com.familyfood.scoring.domain;

/**
 * The FSSAI draft INR food-type split (evidence/source-002, Schedule III Tables 1–3):
 * {@code SOLID} (Category-I, per 100 g), {@code LIQUID} (Category-II non-dairy, per
 * 100 mL) and this project's own {@code FAT_OIL} bucket for Schedule-IV-exempt fats and
 * oils, for which no INR score is computed (scoring-methodology.md §2.2). Mirrors the
 * {@code score_component.food_type_scope} and {@code category_standard_map.food_type}
 * CHECK constraints.
 */
public enum FoodType {
    SOLID, LIQUID, FAT_OIL
}

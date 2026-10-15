package com.familyfood.scoring.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a scoring computation needs about one product, assembled from catalog's
 * published {@code ProductDetailDto} by {@link AssessmentInputMapper} — nothing in
 * {@code scoring} imports catalog's domain or infra packages, only that DTO.
 *
 * <p>The v2 fields (D-059): {@code basisPer100} is the declared per-100 basis of the
 * nutrient facts ({@code PER_100G} / {@code PER_100ML}); {@code novaGroup}/{@code novaSource}
 * the catalogue's processing level, if classified; {@code foodType} the INR food type
 * resolved by {@link AssessmentOrchestrator} (null until resolved); {@code derivedFacts} the
 * engine-computed facts ({@code INR_STARS}, {@code NOVA_GROUP}) that verdict rules may
 * reference exactly like a nutrient — empty until the orchestrator adds them.
 */
public record ProductAssessmentInput(
        UUID productVersionId,
        String categoryCode,                 // nullable
        List<NutrientFact> nutrients,
        List<String> declaredAllergenCodes,  // CONTAINS-declared allergen codes only
        String basisPer100,                  // "PER_100G" | "PER_100ML" | null
        Integer novaGroup,                   // 1-4 or null
        String novaSource,                   // product_source.source_type of the NOVA fact, or null
        String foodType,                     // "SOLID" | "LIQUID" | "FAT_OIL" | null
        Map<String, BigDecimal> derivedFacts // INR_STARS, NOVA_GROUP … never null
) {

    public ProductAssessmentInput {
        derivedFacts = derivedFacts == null ? Map.of() : Map.copyOf(derivedFacts);
    }

    /** The pre-v2 shape: no basis, no processing level, no food type, no derived facts. */
    public ProductAssessmentInput(UUID productVersionId, String categoryCode,
                                  List<NutrientFact> nutrients, List<String> declaredAllergenCodes) {
        this(productVersionId, categoryCode, nutrients, declaredAllergenCodes, null, null, null, null, Map.of());
    }

    public ProductAssessmentInput withFoodType(String resolvedFoodType) {
        return new ProductAssessmentInput(productVersionId, categoryCode, nutrients, declaredAllergenCodes,
                basisPer100, novaGroup, novaSource, resolvedFoodType, derivedFacts);
    }

    public ProductAssessmentInput withDerivedFacts(Map<String, BigDecimal> facts) {
        return new ProductAssessmentInput(productVersionId, categoryCode, nutrients, declaredAllergenCodes,
                basisPer100, novaGroup, novaSource, foodType, facts);
    }
}

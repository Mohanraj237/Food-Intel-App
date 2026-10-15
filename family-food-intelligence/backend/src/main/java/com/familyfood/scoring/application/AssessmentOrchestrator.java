package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.CategoryStandardMap;
import com.familyfood.scoring.domain.FoodType;
import com.familyfood.scoring.infra.CategoryStandardMapRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the two layers of an assessment in order (scoring-methodology.md §1, D-059):
 *
 * <ol>
 *   <li>resolve the product's INR food type ({@link #resolve}) — the category's mapped
 *       default from {@code category_standard_map}, overridden to {@code LIQUID} when the
 *       facts are declared per 100 mL (a category can hold both, e.g. a powder and a
 *       ready-to-drink); {@code FAT_OIL} is never overridden; no mapping → {@code SOLID}
 *       unless the basis says otherwise;
 *   <li>get the product-level score from {@link ProductScoreService} (stored or computed);
 *   <li>add the engine-derived facts the verdict rules may reference: {@code INR_STARS}
 *       (only when scored) and {@code NOVA_GROUP} (only when classified).
 * </ol>
 *
 * The verdict layer ({@code PersonalFitService}/{@code HouseholdFitService}) then takes the
 * returned input. Every controller and module that assesses a product goes through here so
 * a derived fact can never be computed two different ways.
 */
@Service
public class AssessmentOrchestrator {

    public static final String FACT_INR_STARS = "INR_STARS";
    public static final String FACT_NOVA_GROUP = "NOVA_GROUP";
    private static final String BASIS_PER_100ML = "PER_100ML";

    private final CategoryStandardMapRepository categoryStandardMaps;
    private final ProductScoreService productScores;

    public AssessmentOrchestrator(CategoryStandardMapRepository categoryStandardMaps,
                                  ProductScoreService productScores) {
        this.categoryStandardMaps = categoryStandardMaps;
        this.productScores = productScores;
    }

    /** Score against the ACTIVE rule set and enrich the input with derived facts. */
    public AssessmentContext assess(ProductAssessmentInput base) {
        ProductAssessmentInput resolved = resolve(base);
        ProductHealthView health = productScores.getOrCompute(resolved);
        return new AssessmentContext(health, withDerivedFacts(resolved, health));
    }

    /** Food-type resolution only — no score, no derived facts. */
    public ProductAssessmentInput resolve(ProductAssessmentInput base) {
        if (base.foodType() != null) {
            return base;
        }
        FoodType mapped = base.categoryCode() == null ? null
                : categoryStandardMaps.findByCategoryCode(base.categoryCode()).stream()
                        .map(CategoryStandardMap::getFoodType)
                        .findFirst()
                        .orElse(null);
        FoodType resolved;
        if (mapped == FoodType.FAT_OIL) {
            resolved = FoodType.FAT_OIL;
        } else if (BASIS_PER_100ML.equals(base.basisPer100())) {
            resolved = FoodType.LIQUID;
        } else {
            resolved = mapped == null ? FoodType.SOLID : mapped;
        }
        return base.withFoodType(resolved.name());
    }

    static ProductAssessmentInput withDerivedFacts(ProductAssessmentInput input, ProductHealthView health) {
        Map<String, BigDecimal> facts = new LinkedHashMap<>(input.derivedFacts());
        if (health.isScored() && health.stars() != null) {
            facts.put(FACT_INR_STARS, health.stars());
        }
        if (input.novaGroup() != null) {
            facts.put(FACT_NOVA_GROUP, BigDecimal.valueOf(input.novaGroup()));
        }
        return input.withDerivedFacts(facts);
    }
}

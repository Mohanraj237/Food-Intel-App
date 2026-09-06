package com.familyfood.scoring.api;

import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.scoring.application.NutrientFact;
import com.familyfood.scoring.application.ProductAssessmentInput;

/**
 * Assembles a {@link ProductAssessmentInput} from catalog's published {@code ProductDetailDto}
 * — the one place this mapping happens, shared by both scoring controllers. {@code scoring}
 * never imports catalog's domain or infra packages; only this DTO, which is catalog's own
 * published contract (the same relationship {@code search} already has with {@code catalog}
 * via {@code ProductQueryService}).
 */
final class AssessmentInputMapper {

    private static final String DECLARED = "CONTAINS";

    private AssessmentInputMapper() {
    }

    static ProductAssessmentInput from(ProductDetailDto product) {
        var nutrients = product.nutrients().stream()
                .map(n -> new NutrientFact(n.nutrientCode(), n.amountPerServing(), n.amountPer100(), n.unit()))
                .toList();

        var declaredAllergens = product.allergens().stream()
                .filter(a -> DECLARED.equals(a.declarationType()))
                .map(a -> a.code())
                .toList();

        return new ProductAssessmentInput(
                product.currentVersion().id(),
                product.categoryCode(),
                nutrients,
                declaredAllergens);
    }
}

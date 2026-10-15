package com.familyfood.scoring.application;

import com.familyfood.catalog.application.dto.ProductDetailDto;

/**
 * Assembles a {@link ProductAssessmentInput} from catalog's published {@code ProductDetailDto}
 * — the one place this mapping happens, shared by both scoring controllers, the
 * recommendation module and the rescore job. {@code scoring} never imports catalog's domain
 * or infra packages; only this DTO, which is catalog's own published contract (the same
 * relationship {@code search} already has with {@code catalog} via {@code ProductQueryService}).
 *
 * <p>The result carries no food type and no derived facts yet — {@link AssessmentOrchestrator}
 * adds those. The per-100 basis is taken from the nutrient rows (all rows of one version
 * share a basis; the first declared one wins).
 */
public final class AssessmentInputMapper {

    private static final String DECLARED = "CONTAINS";

    private AssessmentInputMapper() {
    }

    public static ProductAssessmentInput from(ProductDetailDto product) {
        var nutrients = product.nutrients().stream()
                .map(n -> new NutrientFact(n.nutrientCode(), n.amountPerServing(), n.amountPer100(),
                        n.unit(), n.isEstimated()))
                .toList();

        var declaredAllergens = product.allergens().stream()
                .filter(a -> DECLARED.equals(a.declarationType()))
                .map(a -> a.code())
                .toList();

        String basis = product.nutrients().stream()
                .map(n -> n.basisPer100())
                .filter(b -> b != null)
                .findFirst()
                .orElse(null);

        var version = product.currentVersion();
        return new ProductAssessmentInput(
                version.id(),
                product.categoryCode(),
                nutrients,
                declaredAllergens,
                basis,
                version.novaGroup(),
                version.novaSource(),
                null,
                java.util.Map.of());
    }
}

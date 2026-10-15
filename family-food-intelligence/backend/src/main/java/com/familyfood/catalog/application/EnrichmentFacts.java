package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.SourceType;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one enrichment pass may fill into a product's current version — every field optional
 * and honoured only where the version has nothing yet (null → value, D-014). Nutrients are
 * added only for codes the version lacks; ingredient tokens are re-scanned for additive
 * marks. {@code sourceType}/{@code sourceRef}/{@code licenseNote} become the
 * {@code product_source} row every filled fact points at (D-015).
 */
public record EnrichmentFacts(
        Integer novaGroup,
        DietFlag dietFlag,
        boolean dietFlagEstimated,
        BigDecimal servingSizeG,
        String servingSizeDeclared,
        BigDecimal fvnlPctEstimated,
        List<IngestProductCommand.NutrientValue> nutrients,
        SourceType sourceType,
        String sourceRef,
        String licenseNote) {

    public EnrichmentFacts {
        nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
    }
}

package com.familyfood.scoring.api;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.scoring.application.AssessmentInputMapper;
import com.familyfood.scoring.application.AssessmentOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The product-level, person-independent health assessment (D-003). Public and anonymous, like
 * {@code ProductController} itself (D-020, {@code SecurityConfig}'s permitAll on
 * {@code GET /api/v1/products/**}) — a health assessment is a fact about the product, not
 * about any person, so it carries the same visibility as the product facts it is derived from.
 *
 * <p>Thin by design (Constitution §36): bind, delegate, map the result.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/assessment")
@Tag(name = "Scoring")
public class ProductHealthController {

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;

    public ProductHealthController(ProductQueryService productQueryService,
                                   AssessmentOrchestrator orchestrator) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
    }

    @GetMapping
    @Operation(summary = "Get a product's deterministic health assessment",
               description = "404 if the product does not exist or has no current version, "
                           + "matching GET /products/{id}. status=NOT_YET_SCORED (score/grade "
                           + "both null) is the honest default until a reviewed rule_set is "
                           + "activated (D-024); INSUFFICIENT_DATA names the missing inputs in "
                           + "coverage. Under the v2 engine a SCORED result also carries stars, "
                           + "drivers and processingLevel (D-059).")
    public ProductHealthResponse get(@PathVariable UUID productId) {
        var product = productQueryService.getProduct(productId);
        var context = orchestrator.assess(AssessmentInputMapper.from(product));
        return ProductHealthResponse.from(context.health());
    }
}

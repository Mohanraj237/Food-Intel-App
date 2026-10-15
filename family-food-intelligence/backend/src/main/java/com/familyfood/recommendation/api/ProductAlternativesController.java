package com.familyfood.recommendation.api;

import com.familyfood.recommendation.application.AlternativesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Product-level alternatives for anyone, signed in or not (D-020, D-060): same-category
 * products with a different rating band, points band or NOVA group — never a reason about a
 * person. Public under {@code GET /api/v1/products/**}.
 */
@RestController
@Tag(name = "Recommendation")
public class ProductAlternativesController {

    private final AlternativesService alternativesService;

    public ProductAlternativesController(AlternativesService alternativesService) {
        this.alternativesService = alternativesService;
    }

    @GetMapping("/api/v1/products/{productId}/alternatives")
    @Operation(summary = "Product-level alternatives to a product",
               description = "Every alternative publishes both betterOn and worseOn (D-013). "
                           + "Differences count only when a product lands in a different band of the "
                           + "active rating method or a different NOVA group (D-060). Empty is a "
                           + "first-class result.")
    public AlternativesResponse get(@PathVariable UUID productId) {
        return AlternativesResponse.from(alternativesService.findProductAlternatives(productId));
    }
}

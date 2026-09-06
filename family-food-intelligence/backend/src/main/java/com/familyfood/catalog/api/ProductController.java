package com.familyfood.catalog.api;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public, read-only product lookups. Thin by design: bind, delegate, return
 * (Constitution §36). No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductQueryService productQueryService;

    public ProductController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id",
               description = "Returns the current version's facts. 404 if the product "
                           + "does not exist, is not ACTIVE, or has no current version.")
    public ProductDetailDto getProduct(@PathVariable UUID id) {
        return productQueryService.getProduct(id);
    }

    @GetMapping("/barcode/{gtin}")
    @Operation(summary = "Get a product by barcode (GTIN)",
               description = "409 if the GTIN maps to more than one active product — a "
                           + "GTIN is not guaranteed unique (AC-4.4).")
    public ProductDetailDto getProductByBarcode(@PathVariable String gtin) {
        return productQueryService.getProductByBarcode(gtin);
    }
}

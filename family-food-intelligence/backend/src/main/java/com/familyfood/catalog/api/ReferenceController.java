package com.familyfood.catalog.api;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.CategoryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public reference data used to drive catalogue filters. */
@RestController
@RequestMapping("/api/v1/reference")
@Tag(name = "Reference data")
public class ReferenceController {

    private final ProductQueryService productQueryService;

    public ReferenceController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping("/categories")
    @Operation(summary = "List product categories", description = "Ordered by sortOrder.")
    public List<CategoryDto> categories() {
        return productQueryService.listCategories();
    }
}

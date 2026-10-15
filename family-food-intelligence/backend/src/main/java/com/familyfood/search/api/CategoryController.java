package com.familyfood.search.api;

import com.familyfood.search.application.CategoryIntelligenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public category pages (D-060). Thin: bind, delegate, return. */
@RestController
@Tag(name = "Search")
public class CategoryController {

    private final CategoryIntelligenceService intelligence;

    public CategoryController(CategoryIntelligenceService intelligence) {
        this.intelligence = intelligence;
    }

    @GetMapping("/api/v1/categories/{code}/intelligence")
    @Operation(summary = "A category page: counts and product rails",
               description = "Popular (at least three different viewers in 30 days), better rated, lowest "
                           + "sugar, highest protein, lowest sodium, least processed — each up to 8 products "
                           + "that declare the value it orders by. Empty rails are honest.")
    public CategoryIntelligenceService.CategoryIntelligence get(@PathVariable String code) {
        return intelligence.intelligence(code);
    }
}

package com.familyfood.search.api;

import com.familyfood.search.application.SearchService;
import com.familyfood.search.application.dto.SearchResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public product search. Thin by design: bind, delegate, return (Constitution §36).
 */
@RestController
@Tag(name = "Search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/search")
    @Operation(summary = "Search products",
               description = "Matches against brand, brand aliases, product name and "
                           + "category, tolerant of typos. Only ACTIVE products with a "
                           + "current version are returned.")
    public SearchResponseDto search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        return searchService.search(q, category, page, size);
    }
}

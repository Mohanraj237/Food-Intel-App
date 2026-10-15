package com.familyfood.search.api;

import com.familyfood.catalog.application.dto.SuggestionDto;
import com.familyfood.search.application.SearchFilters;
import com.familyfood.search.application.SearchService;
import com.familyfood.search.application.SearchSort;
import com.familyfood.search.application.dto.SearchResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Public product search and suggestions. Thin by design: bind, delegate, return
 * (Constitution §36). Anonymous, like every product-level read (D-020).
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
               description = "Matches brand, brand aliases, product name and category, tolerant of "
                           + "typos. Filters and sort apply server-side (D-061); phrases in q such as "
                           + "'under 10g sugar', 'veg', 'no nuts' or 'high protein' are read into "
                           + "filters and echoed in `interpreted`. Explicit parameters win over the "
                           + "words. Nutrient values are per 100 g / 100 ml: grams, sodium in mg, "
                           + "energy in kcal. A nutrient filter excludes products that do not declare "
                           + "that nutrient; `excludedForMissingData` counts them.")
    public SearchResponseDto search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) BigDecimal maxAddedSugar,
                                    @RequestParam(required = false) BigDecimal maxTotalSugar,
                                    @RequestParam(required = false) BigDecimal maxSodium,
                                    @RequestParam(required = false) BigDecimal maxSatFat,
                                    @RequestParam(required = false) BigDecimal maxEnergy,
                                    @RequestParam(required = false) BigDecimal minProtein,
                                    @RequestParam(required = false) BigDecimal minFibre,
                                    @RequestParam(required = false) String excludeAllergens,
                                    @RequestParam(required = false) String dietary,
                                    @RequestParam(required = false) Integer maxNova,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        List<String> allergens = SearchFilters.parseAllergenCsv(excludeAllergens);
        SearchFilters filters = new SearchFilters(
                blankToNull(category), maxAddedSugar, maxTotalSugar, maxSodium, maxSatFat, maxEnergy,
                minProtein, minFibre, allergens,
                dietary == null || dietary.isBlank() ? null : dietary.trim().toUpperCase(Locale.ROOT),
                maxNova);
        SearchSort explicitSort = sort == null || sort.isBlank() ? null : SearchSort.parse(sort);
        return searchService.search(q, filters, explicitSort, page, size);
    }

    @GetMapping("/api/v1/search/suggest")
    @Operation(summary = "Search-box suggestions",
               description = "Up to 10 brands, categories and product names for what has been "
                           + "typed so far (2+ characters). Public catalogue data only.")
    public List<SuggestionDto> suggest(@RequestParam(required = false) String q) {
        return searchService.suggest(q);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

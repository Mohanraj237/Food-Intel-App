package com.familyfood.catalog.application;

import com.familyfood.catalog.application.dto.CategoryDto;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.application.dto.SuggestionDto;
import com.familyfood.platform.cache.CacheSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The catalogue's caches (D-056).
 *
 * <p>Everything cached here is public, non-personal catalogue data: the same facts an
 * anonymous visitor may read (D-020). One cached copy is therefore correct for every caller.
 * Nothing about a person is cached anywhere, and CachingBoundaryTest keeps caching confined
 * to this module.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogCaches {

    /**
     * One product's detail, by product id — the product page, compare, alternatives, fit and
     * meal logging all read it. Evicted when the product's image changes or an enrichment pass
     * fills a fact. New facts otherwise arrive as a new product ({@link ProductIngestionService}
     * never edits one), so the TTL only has to bound changes made outside this process: a
     * separate seed run, a SQL correction.
     */
    public static final String PRODUCT = "catalog.product";

    /** The category list behind every catalogue filter. It changes only with a migration. */
    public static final String CATEGORIES = "catalog.categories";

    /** Category aliases for search's query parser (V15). Change only with a migration. */
    public static final String CATEGORY_ALIASES = "catalog.category-aliases";

    /**
     * Search-box suggestions by normalized prefix (D-061): brands, categories and product
     * names — public catalogue data, identical for every caller. Short TTL: a new product
     * should appear in suggestions within minutes of import.
     */
    public static final String SUGGEST = "catalog.suggest";

    @Bean
    CacheSpec productCache() {
        return new CacheSpec(PRODUCT, Duration.ofMinutes(30), 2_000,
                new TypeReference<ProductDetailDto>() {});
    }

    @Bean
    CacheSpec categoriesCache() {
        return new CacheSpec(CATEGORIES, Duration.ofHours(1), 1,
                new TypeReference<List<CategoryDto>>() {});
    }

    @Bean
    CacheSpec categoryAliasesCache() {
        return new CacheSpec(CATEGORY_ALIASES, Duration.ofHours(1), 1,
                new TypeReference<Map<String, String>>() {});
    }

    @Bean
    CacheSpec suggestCache() {
        return new CacheSpec(SUGGEST, Duration.ofMinutes(10), 5_000,
                new TypeReference<List<SuggestionDto>>() {});
    }
}

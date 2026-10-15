package com.familyfood.catalog.application;

import com.familyfood.catalog.application.dto.SuggestionDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Search-box suggestions (D-061): as a person types "amu", offer the brand Amul, a matching
 * category, and a few product names — prefix matches first, then typo-tolerant trigram
 * matches, so "britania" still offers Britannia through its alias. Public catalogue data,
 * cached per normalized query (D-056).
 *
 * <p>Ranking is deterministic: brands by how many active products they have, categories by
 * their sort order, products by name similarity then name. Nothing commercial can reach it
 * (D-012) — there is no input for it to reach.
 */
@Service
@Transactional(readOnly = true)
public class ProductSuggestionService {

    static final int MAX_ITEMS = 10;
    static final int MIN_QUERY_LENGTH = 2;
    private static final double TRIGRAM_THRESHOLD = 0.3;

    private final NamedParameterJdbcTemplate jdbc;

    public ProductSuggestionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cached by the normalized query; a too-short query returns nothing without touching the database. */
    @Cacheable(cacheNames = CatalogCaches.SUGGEST, key = "T(com.familyfood.catalog.application.ProductIngestionService).normalize(#q)",
            condition = "#q != null && #q.trim().length() >= 2")
    public List<SuggestionDto> suggest(String q) {
        if (q == null || q.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String normalized = ProductIngestionService.normalize(q);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", normalized)
                .addValue("prefix", escapeLike(normalized) + "%")
                .addValue("threshold", TRIGRAM_THRESHOLD);

        List<SuggestionDto> brands = jdbc.query("""
                SELECT b.name, count(p.id) AS n,
                       max(CASE WHEN b.normalized_name LIKE :prefix OR a.normalized_alias LIKE :prefix THEN 1 ELSE 0 END) AS is_prefix,
                       max(greatest(similarity(b.normalized_name, :q), coalesce(similarity(a.normalized_alias, :q), 0))) AS sim
                FROM brand b
                JOIN product p ON p.brand_id = b.id AND p.status = 'ACTIVE'
                LEFT JOIN brand_alias a ON a.brand_id = b.id
                WHERE b.normalized_name LIKE :prefix OR a.normalized_alias LIKE :prefix
                   OR similarity(b.normalized_name, :q) > :threshold
                   OR similarity(a.normalized_alias, :q) > :threshold
                GROUP BY b.id, b.name
                ORDER BY is_prefix DESC, n DESC, sim DESC, b.name
                LIMIT 3
                """, params, (rs, i) -> new SuggestionDto("BRAND", rs.getString("name"), rs.getString("name"),
                String.valueOf(rs.getLong("n"))));

        List<SuggestionDto> categories = jdbc.query("""
                SELECT c.code, c.name, min(c.sort_order) AS sort_order
                FROM category c
                LEFT JOIN category_alias ca ON ca.category_code = c.code
                WHERE lower(c.name) LIKE :prefix OR ca.alias LIKE :prefix
                GROUP BY c.code, c.name
                ORDER BY sort_order
                LIMIT 2
                """, params, (rs, i) -> new SuggestionDto("CATEGORY", rs.getString("name"), rs.getString("code"), null));

        List<SuggestionDto> products = jdbc.query("""
                SELECT p.id, p.name, coalesce(b.name, '') AS brand,
                       CASE WHEN p.normalized_name LIKE :prefix THEN 1 ELSE 0 END AS is_prefix,
                       similarity(p.search_text, :q) AS sim
                FROM product p
                JOIN product_version pv ON pv.product_id = p.id AND pv.is_current
                 AND pv.status NOT IN ('REJECTED','SUPERSEDED')
                LEFT JOIN brand b ON b.id = p.brand_id
                WHERE p.status = 'ACTIVE'
                  AND (p.normalized_name LIKE :prefix
                       OR p.search_vector @@ plainto_tsquery('simple', :q)
                       OR similarity(p.search_text, :q) > :threshold)
                ORDER BY is_prefix DESC, sim DESC, p.name
                LIMIT 8
                """, params, (rs, i) -> new SuggestionDto("PRODUCT", rs.getString("name"),
                rs.getObject("id", java.util.UUID.class).toString(), rs.getString("brand")));

        List<SuggestionDto> result = new ArrayList<>(brands);
        result.addAll(categories);
        Set<String> seen = new LinkedHashSet<>();
        result.forEach(s -> seen.add(s.kind() + "|" + s.label().toLowerCase()));
        for (SuggestionDto product : products) {
            if (result.size() >= MAX_ITEMS) {
                break;
            }
            if (seen.add(product.kind() + "|" + product.label().toLowerCase())) {
                result.add(product);
            }
        }
        return List.copyOf(result.size() > MAX_ITEMS ? result.subList(0, MAX_ITEMS) : result);
    }

    /** "Did you mean" for a query with no results: the closest brand or product name, if any is close. */
    public Optional<String> didYouMean(String q) {
        if (q == null || q.trim().length() < MIN_QUERY_LENGTH) {
            return Optional.empty();
        }
        String normalized = ProductIngestionService.normalize(q);
        List<String> names = jdbc.query("""
                SELECT name FROM (
                    SELECT b.name, similarity(b.normalized_name, :q) AS sim
                    FROM brand b WHERE EXISTS (SELECT 1 FROM product p WHERE p.brand_id = b.id AND p.status = 'ACTIVE')
                    UNION ALL
                    SELECT p.name, similarity(p.normalized_name, :q) AS sim FROM product p WHERE p.status = 'ACTIVE'
                ) candidates
                WHERE sim > 0.2 AND lower(name) <> :q
                ORDER BY sim DESC, name
                LIMIT 1
                """, new MapSqlParameterSource("q", normalized), (rs, i) -> rs.getString(1));
        return names.stream().findFirst();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

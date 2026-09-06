package com.familyfood.search.infra;

import com.familyfood.catalog.domain.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Search over {@code product}. A native query is the right tool here (Constitution's
 * catalog module notes, V4__catalog.sql): JPQL cannot express {@code to_tsquery}/
 * {@code plainto_tsquery} or {@code similarity()}.
 *
 * <p>{@code 'simple'} rather than {@code 'english'}, matching the trigger that maintains
 * {@code product.search_vector} in V4__catalog.sql — Indian brand names are not English
 * words and stemming actively harms them.
 *
 * <p>Only {@code product.status='ACTIVE'} rows with a non-rejected, non-superseded
 * current version are ever matched — the same visibility rule
 * {@code ProductQueryService} applies to a direct lookup.
 *
 * <p>Query parameters are cast explicitly ({@code CAST(:q AS text)}) because a native,
 * parameterised query with a possibly-null bind value otherwise leaves PostgreSQL unable
 * to infer the parameter's type ("could not determine data type of parameter").
 */
public interface ProductSearchRepository extends Repository<Product, UUID> {

    String VISIBLE_PRODUCT_JOIN = """
            FROM product p
            JOIN product_version pv ON pv.product_id = p.id AND pv.is_current = true
            LEFT JOIN category c ON c.id = p.category_id
            WHERE p.status = 'ACTIVE'
              AND pv.status NOT IN ('REJECTED', 'SUPERSEDED')
              AND (CAST(:q AS text) IS NULL
                   OR p.search_vector @@ plainto_tsquery('simple', unaccent(CAST(:q AS text)))
                   OR similarity(p.search_text, lower(unaccent(CAST(:q AS text)))) > 0.15)
              AND (CAST(:category AS text) IS NULL OR c.code = CAST(:category AS text))
            """;

    @Query(value = "SELECT p.id " + VISIBLE_PRODUCT_JOIN + """
            ORDER BY
              CASE WHEN CAST(:q AS text) IS NULL THEN 0
                   ELSE GREATEST(
                       ts_rank(p.search_vector, plainto_tsquery('simple', unaccent(CAST(:q AS text)))),
                       similarity(p.search_text, lower(unaccent(CAST(:q AS text))))
                   )
              END DESC,
              p.name ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<UUID> searchIds(@Param("q") String q, @Param("category") String category,
                         @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "SELECT count(p.id) " + VISIBLE_PRODUCT_JOIN, nativeQuery = true)
    long countSearch(@Param("q") String q, @Param("category") String category);
}

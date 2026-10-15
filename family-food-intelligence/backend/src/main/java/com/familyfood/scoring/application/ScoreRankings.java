package com.familyfood.scoring.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Products ordered by the ACTIVE rule set's persisted score (D-059/D-060) — the candidate pool
 * for alternatives and household picks. Reads {@code product_search_facts}, whose score column
 * is the ACTIVE set's {@code product_score}; a product without a stored score is simply absent.
 * While the ACTIVE set is the empty {@code v1} this returns nothing, and callers fall back to
 * their pre-v2 pools.
 */
@Service
@Transactional(readOnly = true)
public class ScoreRankings {

    private final JdbcTemplate jdbc;

    public ScoreRankings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> topScoredInCategory(String categoryCode, UUID excludeProductId, int limit) {
        return jdbc.query("""
                SELECT f.product_id
                FROM product_search_facts f
                JOIN product p ON p.id = f.product_id
                WHERE f.category_code = ? AND f.score_status = 'SCORED'
                  AND (?::uuid IS NULL OR f.product_id <> ?::uuid)
                ORDER BY f.score DESC, p.name
                LIMIT ?
                """, (rs, i) -> rs.getObject(1, UUID.class),
                categoryCode, excludeProductId, excludeProductId, limit);
    }
}

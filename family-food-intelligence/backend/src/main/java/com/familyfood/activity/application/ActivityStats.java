package com.familyfood.activity.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate, non-personal activity figures for other modules (D-060): which products in a
 * category people have looked at most. Only an ordering of product ids leaves this class —
 * never a count, never an account — and a product enters the ranking only once at least
 * {@link #MIN_DISTINCT_VIEWERS} different accounts have viewed it in the window, so the
 * ranking can never reveal what one person looked at.
 */
@Service
@Transactional(readOnly = true)
public class ActivityStats {

    /** k-anonymity floor for a product to count as "popular". */
    static final int MIN_DISTINCT_VIEWERS = 3;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ActivityStats(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Most-viewed ACTIVE products in a category over the window, most distinct viewers first. */
    public List<UUID> mostViewedInCategory(String categoryCode, Duration window, int limit) {
        Instant since = clock.instant().minus(window);
        return jdbc.query("""
                SELECT e.product_id
                FROM activity_event e
                JOIN product p ON p.id = e.product_id AND p.status = 'ACTIVE'
                JOIN category c ON c.id = p.category_id
                WHERE c.code = ? AND e.event_type = 'VIEWED' AND e.occurred_at > ?
                GROUP BY e.product_id
                HAVING count(DISTINCT e.account_id) >= ?
                ORDER BY count(DISTINCT e.account_id) DESC, e.product_id
                LIMIT ?
                """, (rs, i) -> rs.getObject(1, UUID.class),
                categoryCode, Timestamp.from(since), MIN_DISTINCT_VIEWERS, limit);
    }
}

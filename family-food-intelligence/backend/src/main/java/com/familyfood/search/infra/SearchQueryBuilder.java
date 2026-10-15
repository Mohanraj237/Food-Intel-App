package com.familyfood.search.infra;

import com.familyfood.search.application.SearchFilters;
import com.familyfood.search.application.SearchSort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Search v2 over the {@code product_search_facts} view (V16, D-061): text matching as before
 * (full-text on {@code product.search_vector}, trigram on {@code product.search_text}), plus
 * a predicate for each supplied filter and a server-side sort. Only supplied filters add SQL;
 * every value is a bind parameter.
 *
 * <p>A nutrient, diet or processing-level filter needs the product to declare that fact. Rows
 * that would have passed every filter they have data for, but lack data for at least one, are
 * counted separately ({@link SearchPage#excludedForMissingData}) so the UI can say "12 more
 * don't declare sugar" instead of implying they were checked.
 *
 * <p>Minimum-amount filters also ignore physically impossible values (more than 100 g of a
 * nutrient per 100 g), mirroring the frontend's plausibility rule, so a unit error on a label
 * can never top a "most protein" list.
 */
@Repository
public class SearchQueryBuilder {

    public record SearchPage(List<UUID> ids, long total, long excludedForMissingData) {
    }

    /** The per-product facts search results carry beyond the catalogue summary. */
    public record Facts(UUID productId, Integer score, BigDecimal stars, String scoreStatus, String grade) {
    }

    private static final String TEXT_MATCH = """
            (p.search_vector @@ plainto_tsquery('simple', unaccent(:q))
             OR similarity(p.search_text, lower(unaccent(:q))) > 0.15)""";

    private static final String RELEVANCE = """
            GREATEST(ts_rank(p.search_vector, plainto_tsquery('simple', unaccent(:q))),
                     similarity(p.search_text, lower(unaccent(:q))))""";

    private static final BigDecimal PLAUSIBLE_MAX_G = new BigDecimal("100");

    private final NamedParameterJdbcTemplate jdbc;

    public SearchQueryBuilder(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchPage search(String q, SearchFilters filters, SearchSort sort, int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> always = new ArrayList<>();
        List<Presence> needsData = new ArrayList<>();

        if (q != null) {
            params.addValue("q", q);
            always.add(TEXT_MATCH);
        }
        if (filters.category() != null) {
            params.addValue("category", filters.category());
            always.add("f.category_code = :category");
        }
        if (!filters.excludeAllergens().isEmpty()) {
            params.addValue("excludeAllergens", filters.excludeAllergens());
            always.add("NOT (f.allergen_codes && ARRAY[:excludeAllergens]::text[])");
        }
        max(needsData, params, "f.added_sugar_100", "maxAddedSugar", filters.maxAddedSugar());
        max(needsData, params, "f.total_sugar_100", "maxTotalSugar", filters.maxTotalSugar());
        max(needsData, params, "f.sodium_100", "maxSodium", filters.maxSodium());
        max(needsData, params, "f.sat_fat_100", "maxSatFat", filters.maxSatFat());
        max(needsData, params, "f.energy_kcal_100", "maxEnergy", filters.maxEnergy());
        if (filters.minProtein() != null) {
            params.addValue("minProtein", filters.minProtein());
            params.addValue("plausibleMax", PLAUSIBLE_MAX_G);
            needsData.add(new Presence("f.protein_100 IS NULL",
                    "f.protein_100 >= :minProtein AND f.protein_100 <= :plausibleMax"));
        }
        if (filters.minFibre() != null) {
            params.addValue("minFibre", filters.minFibre());
            params.addValue("plausibleMax", PLAUSIBLE_MAX_G);
            needsData.add(new Presence("f.fibre_100 IS NULL",
                    "f.fibre_100 >= :minFibre AND f.fibre_100 <= :plausibleMax"));
        }
        if (filters.dietary() != null) {
            params.addValue("diets", "VEGAN".equals(filters.dietary()) ? List.of("VEGAN") : List.of("VEG", "VEGAN"));
            needsData.add(new Presence("f.diet_flag = 'UNKNOWN'", "f.diet_flag IN (:diets)"));
        }
        if (filters.maxNova() != null) {
            params.addValue("maxNova", filters.maxNova());
            needsData.add(new Presence("f.nova_group IS NULL", "f.nova_group <= :maxNova"));
        }

        List<String> matching = new ArrayList<>(always);
        needsData.forEach(p -> matching.add(p.condition()));
        String from = """
                FROM product_search_facts f
                JOIN product p ON p.id = f.product_id
                """;

        long total = count(from, matching, params);

        long excluded = 0;
        if (!needsData.isEmpty()) {
            List<String> missing = new ArrayList<>(always);
            needsData.forEach(p -> missing.add("(" + p.missing() + " OR (" + p.condition() + "))"));
            missing.add("(" + String.join(" OR ", needsData.stream().map(Presence::missing).toList()) + ")");
            excluded = count(from, missing, params);
        }

        params.addValue("limit", limit);
        params.addValue("offset", offset);
        String sql = "SELECT f.product_id " + from + where(matching)
                + " ORDER BY " + orderBy(sort, q != null) + " LIMIT :limit OFFSET :offset";
        List<UUID> ids = jdbc.query(sql, params, (rs, i) -> rs.getObject(1, UUID.class));
        return new SearchPage(ids, total, excluded);
    }

    /** The ACTIVE rule set's persisted score for each id (absent when not scored). */
    public Map<UUID, Facts> factsFor(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Facts> result = new HashMap<>();
        jdbc.query("""
                SELECT f.product_id, f.score, f.stars, f.score_status, ps.grade
                FROM product_search_facts f
                LEFT JOIN product_score ps ON ps.product_version_id = f.product_version_id
                 AND ps.rule_set_id = (SELECT id FROM rule_set WHERE status = 'ACTIVE')
                WHERE f.product_id IN (:ids)
                """, new MapSqlParameterSource("ids", ids), rs -> {
            UUID id = rs.getObject("product_id", UUID.class);
            Integer score = (Integer) rs.getObject("score");
            result.put(id, new Facts(id, score, rs.getBigDecimal("stars"), rs.getString("score_status"), rs.getString("grade")));
        });
        return result;
    }

    /** Top products by persisted score in a category, excluding a brand — for "better in this category". */
    public List<UUID> topScoredInCategory(String categoryCode, UUID excludeBrandId, Integer aboveScore, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", categoryCode)
                .addValue("limit", limit);
        StringBuilder sql = new StringBuilder("""
                SELECT f.product_id FROM product_search_facts f
                JOIN product p ON p.id = f.product_id
                WHERE f.category_code = :category AND f.score_status = 'SCORED'
                """);
        if (excludeBrandId != null) {
            params.addValue("brand", excludeBrandId);
            sql.append(" AND (f.brand_id IS NULL OR f.brand_id <> :brand)");
        }
        if (aboveScore != null) {
            params.addValue("above", aboveScore);
            sql.append(" AND f.score > :above");
        }
        sql.append(" ORDER BY f.score DESC, p.name ASC LIMIT :limit");
        return jdbc.query(sql.toString(), params, (rs, i) -> rs.getObject(1, UUID.class));
    }

    /**
     * One category rail: products that declare the value the rail orders by (a product without
     * sugar on its label never appears in "lowest sugar"), plausible values only.
     */
    public List<UUID> categoryRail(String categoryCode, SearchSort sort, int limit) {
        String declared = switch (sort) {
            case SCORE_DESC -> "f.score_status = 'SCORED'";
            case SUGAR_ASC -> "f.total_sugar_100 IS NOT NULL AND f.total_sugar_100 <= 100";
            case ADDED_SUGAR_ASC -> "f.added_sugar_100 IS NOT NULL AND f.added_sugar_100 <= 100";
            case SODIUM_ASC -> "f.sodium_100 IS NOT NULL AND f.sodium_100 <= 100000";
            case SAT_FAT_ASC -> "f.sat_fat_100 IS NOT NULL AND f.sat_fat_100 <= 100";
            case PROTEIN_DESC -> "f.protein_100 IS NOT NULL AND f.protein_100 <= 100";
            case FIBRE_DESC -> "f.fibre_100 IS NOT NULL AND f.fibre_100 <= 100";
            case RELEVANCE, QUALITY -> "true";
        };
        String sql = "SELECT f.product_id FROM product_search_facts f JOIN product p ON p.id = f.product_id "
                + "WHERE f.category_code = :category AND " + declared
                + " ORDER BY " + orderBy(sort, false) + " LIMIT :limit";
        return jdbc.query(sql, new MapSqlParameterSource().addValue("category", categoryCode).addValue("limit", limit),
                (rs, i) -> rs.getObject(1, UUID.class));
    }

    /** Least processed first: NOVA group ascending, classified products only. */
    public List<UUID> leastProcessedInCategory(String categoryCode, int limit) {
        return jdbc.query("""
                SELECT f.product_id FROM product_search_facts f JOIN product p ON p.id = f.product_id
                WHERE f.category_code = :category AND f.nova_group IS NOT NULL
                ORDER BY f.nova_group ASC, f.score DESC NULLS LAST, p.name
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("category", categoryCode).addValue("limit", limit),
                (rs, i) -> rs.getObject(1, UUID.class));
    }

    /** How many visible products a category holds, and how many of them are rated. */
    public long[] categoryCounts(String categoryCode) {
        return jdbc.queryForObject("""
                SELECT count(*), count(*) FILTER (WHERE score_status = 'SCORED')
                FROM product_search_facts WHERE category_code = :category
                """, new MapSqlParameterSource("category", categoryCode),
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)});
    }

    /** Other products of the same brand, most complete data first. */
    public List<UUID> sameBrand(UUID brandId, UUID excludeProductId, int limit) {
        return jdbc.query("""
                SELECT f.product_id FROM product_search_facts f
                JOIN product p ON p.id = f.product_id
                WHERE f.brand_id = :brand AND f.product_id <> :exclude
                ORDER BY CASE f.data_quality WHEN 'VERIFIED' THEN 0 WHEN 'RECENT' THEN 1 ELSE 2 END, p.name
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("brand", brandId).addValue("exclude", excludeProductId)
                .addValue("limit", limit), (rs, i) -> rs.getObject(1, UUID.class));
    }

    /** Brand and category of each id — for deciding whether results are dominated by one brand/category. */
    public Map<UUID, UUID[]> brandAndCategory(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID[]> result = new HashMap<>();
        jdbc.query("SELECT product_id, brand_id, category_id FROM product_search_facts WHERE product_id IN (:ids)",
                new MapSqlParameterSource("ids", ids), rs -> {
                    result.put(rs.getObject(1, UUID.class),
                            new UUID[]{rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)});
                });
        return result;
    }

    /** The brand a whole query names exactly — by its normalized name or one of its aliases. */
    public UUID brandNamedExactly(String normalizedQuery) {
        List<UUID> ids = jdbc.query("""
                SELECT b.id FROM brand b WHERE b.normalized_name = :q
                UNION
                SELECT a.brand_id FROM brand_alias a WHERE a.normalized_alias = :q
                LIMIT 1
                """, new MapSqlParameterSource("q", normalizedQuery), (rs, i) -> rs.getObject(1, UUID.class));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long count(String from, List<String> predicates, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject("SELECT count(*) " + from + where(predicates), params, Long.class);
        return n == null ? 0 : n;
    }

    private static String where(List<String> predicates) {
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private static String orderBy(SearchSort sort, boolean hasQuery) {
        String relevance = hasQuery ? RELEVANCE + " DESC, " : "";
        String tail = relevance + "p.name ASC";
        return switch (sort) {
            case RELEVANCE -> tail;
            case SCORE_DESC -> "f.score DESC NULLS LAST, " + tail;
            case SUGAR_ASC -> "f.total_sugar_100 ASC NULLS LAST, " + tail;
            case ADDED_SUGAR_ASC -> "f.added_sugar_100 ASC NULLS LAST, " + tail;
            case SODIUM_ASC -> "f.sodium_100 ASC NULLS LAST, " + tail;
            case SAT_FAT_ASC -> "f.sat_fat_100 ASC NULLS LAST, " + tail;
            case PROTEIN_DESC -> "CASE WHEN f.protein_100 <= 100 THEN f.protein_100 END DESC NULLS LAST, " + tail;
            case FIBRE_DESC -> "CASE WHEN f.fibre_100 <= 100 THEN f.fibre_100 END DESC NULLS LAST, " + tail;
            case QUALITY -> "CASE f.data_quality WHEN 'VERIFIED' THEN 0 WHEN 'RECENT' THEN 1 WHEN 'UNVERIFIED' THEN 2 "
                    + "WHEN 'STALE' THEN 3 WHEN 'NEEDS_REVIEW' THEN 4 ELSE 5 END ASC, " + tail;
        };
    }

    private static void max(List<Presence> needsData, MapSqlParameterSource params, String column, String name,
                            BigDecimal value) {
        if (value == null) {
            return;
        }
        params.addValue(name, value);
        needsData.add(new Presence(column + " IS NULL", column + " <= :" + name));
    }

    /** A filter that needs data: its condition, and what "the product lacks this data" looks like. */
    private record Presence(String missing, String condition) {
    }
}

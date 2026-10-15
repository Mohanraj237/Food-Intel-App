package com.familyfood.recommendation.application;

import com.familyfood.scoring.application.PersonalFitView;
import com.familyfood.scoring.application.ProductHealthView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The deterministic "best overall" rule for a comparison of 2–5 products (D-066). Never a
 * guess: when the data cannot separate the products, the answer is "no winner" with the
 * reason, not a coin toss.
 *
 * <ol>
 *   <li>With a household: drop every product whose label declares an allergen matching anyone
 *       the caller may see. All dropped → {@code NO_WINNER_ALL_CONFLICT}.
 *   <li>Every remaining product must be rated by the active method; otherwise
 *       {@code NO_WINNER_INSUFFICIENT_DATA}.
 *   <li>With a household and real verdicts: the product whose worst verdict among visible
 *       members is least severe; then higher stars; then higher score.
 *       Without: higher stars, then higher score.
 *   <li>A tie on every key → {@code NO_WINNER_TIE}.
 * </ol>
 */
public record BestOverall(UUID productId, String outcome, List<String> reasons, List<UUID> excludedForAllergens) {

    public static final String WINNER = "WINNER";
    public static final String NO_WINNER_TIE = "NO_WINNER_TIE";
    public static final String NO_WINNER_INSUFFICIENT_DATA = "NO_WINNER_INSUFFICIENT_DATA";
    public static final String NO_WINNER_ALL_CONFLICT = "NO_WINNER_ALL_CONFLICT";

    private static final Map<String, Integer> SEVERITY = Map.of("EVERYDAY", 0, "OCCASIONAL", 1, "LIMIT", 2, "DOESNT_FIT", 3);

    /** One compared product: its product-level assessment and the visible members' fit (empty without a household). */
    public record Entry(UUID productId, ProductHealthView health, List<PersonalFitView> members) {
    }

    public static BestOverall decide(List<Entry> entries, boolean withHousehold) {
        List<UUID> excluded = new ArrayList<>();
        List<Entry> remaining = new ArrayList<>();
        for (Entry e : entries) {
            boolean conflict = withHousehold && e.members().stream().anyMatch(m -> !m.allergenMatches().isEmpty());
            if (conflict) {
                excluded.add(e.productId());
            } else {
                remaining.add(e);
            }
        }
        if (remaining.isEmpty()) {
            return new BestOverall(null, NO_WINNER_ALL_CONFLICT, List.of("ALLERGEN_CONFLICT"), List.copyOf(excluded));
        }
        boolean allRated = remaining.stream().allMatch(e -> e.health().isScored() && e.health().stars() != null);
        if (!allRated) {
            return new BestOverall(null, NO_WINNER_INSUFFICIENT_DATA, List.of("NOT_ALL_RATED"), List.copyOf(excluded));
        }

        boolean useVerdicts = withHousehold && remaining.stream().allMatch(e -> worstSeverity(e) != null);
        Comparator<Entry> order = Comparator.comparing((Entry e) -> e.health().stars(), Comparator.reverseOrder())
                .thenComparing(e -> e.health().score(), Comparator.nullsLast(Comparator.reverseOrder()));
        if (useVerdicts) {
            order = Comparator.<Entry, Integer>comparing(BestOverall::worstSeverity).thenComparing(order);
        }
        List<Entry> sorted = remaining.stream().sorted(order).toList();
        if (sorted.size() > 1 && order.compare(sorted.get(0), sorted.get(1)) == 0) {
            return new BestOverall(null, NO_WINNER_TIE, List.of("TIED"), List.copyOf(excluded));
        }

        Entry winner = sorted.get(0);
        List<String> reasons = new ArrayList<>();
        if (useVerdicts) {
            reasons.add("LEAST_SEVERE_VERDICT");
        }
        reasons.add("HIGHEST_RATING");
        if (!excluded.isEmpty()) {
            reasons.add("OTHERS_ALLERGEN_CONFLICT");
        }
        return new BestOverall(winner.productId(), WINNER, List.copyOf(reasons), List.copyOf(excluded));
    }

    /** The most severe real verdict among the visible members, or null when nobody has one. */
    static Integer worstSeverity(Entry e) {
        return e.members().stream()
                .filter(m -> "ASSESSED".equals(m.status()))
                .map(m -> SEVERITY.get(m.verdict()))
                .filter(java.util.Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);
    }
}

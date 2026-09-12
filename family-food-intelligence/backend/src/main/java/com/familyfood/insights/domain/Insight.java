package com.familyfood.insights.domain;

import com.familyfood.wellness.domain.MetricCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One statement derived from recorded data.
 *
 * <p>Not an entity. Insights are computed on read and never stored as fact
 * (`docs/architecture/wellness-platform-architecture.md` §6) — storing one would make a
 * derived opinion outlive the data it came from, and a corrected meal would leave a stale
 * sentence behind claiming something that is no longer true.
 *
 * <p><b>Every number in {@link #headline} and {@link #detail} must trace to {@link #evidence}.</b>
 * That is the whole discipline of this module. The AI boundary (D-011) says AI never
 * originates a number, threshold, verdict or authorization decision; this record is how that
 * is made checkable rather than merely promised. An intelligence layer may later rephrase
 * these sentences, but it may not compute them, and the evidence list is what a reviewer
 * reads to confirm it did not.
 *
 * @param availability whether there was enough data to say anything at all — a first-class
 *                     outcome, not an error. The same posture as {@code INSUFFICIENT_DATA} in
 *                     scoring (D-024): saying nothing is better than guessing.
 * @param evidence     the observations the statement was computed from. Never empty unless
 *                     {@code availability} is {@link Availability#NO_DATA}.
 */
public record Insight(
        InsightType type,
        UUID subjectMemberId,
        InsightWindow window,
        Availability availability,
        /** One short sentence stating what happened. */
        String headline,
        /** Optional second sentence: why it matters, in terms of the user's own goal. */
        String detail,
        List<Evidence> evidence,
        Instant createdAt) {

    /** How much of the window actually had data behind it. */
    public enum Availability {
        /** Enough observations to make the statement. */
        SUFFICIENT,
        /** Some data, but below the threshold for a trend — report coverage, not a trend. */
        PARTIAL,
        /** Nothing recorded in the window. */
        NO_DATA
    }

    /**
     * One number the insight used, and where it came from.
     *
     * <p>Deliberately granular enough that a reader can reproduce the arithmetic by hand.
     * An insight a user cannot check is an insight they have to take on trust, and this
     * product's whole position is that it does not ask for that.
     */
    public record Evidence(
            String label,
            java.math.BigDecimal value,
            String unit,
            MetricCategory category,
            /** How many distinct days contributed. Makes "5 of 7 days" checkable. */
            int observationDays) {
    }

    public static Insight noData(InsightType type, UUID subject, InsightWindow window,
                                 String headline, Instant now) {
        return new Insight(type, subject, window, Availability.NO_DATA, headline, null,
                List.of(), now);
    }
}

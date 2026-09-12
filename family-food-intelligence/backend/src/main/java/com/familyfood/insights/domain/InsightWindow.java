package com.familyfood.insights.domain;

import java.time.Duration;

/**
 * The period an insight covers, and the minimum evidence it needs.
 *
 * <p>{@code minimumDays} is the honesty threshold: below it the module reports coverage
 * rather than a trend. Two days of logging cannot support "your protein is up 18%" — that
 * sentence would be arithmetic performed on noise, and it would read as authoritative.
 */
public enum InsightWindow {
    TODAY(Duration.ofDays(1), 1),
    WEEK(Duration.ofDays(7), 4),
    MONTH(Duration.ofDays(30), 14);

    private final Duration length;
    private final int minimumDays;

    InsightWindow(Duration length, int minimumDays) {
        this.length = length;
        this.minimumDays = minimumDays;
    }

    public Duration length() {
        return length;
    }

    /** Distinct days that must carry an observation before a trend may be stated. */
    public int minimumDays() {
        return minimumDays;
    }
}

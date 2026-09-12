package com.familyfood.habits.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * How often a habit is meant to happen.
 *
 * <p>Kept to three values on purpose. The moment this grows "every other Tuesday" it has become
 * a scheduling engine, and a scheduling engine for a child's behaviour is a different product
 * than the one being built (D-036, CH-004).
 *
 * <p>{@link #expectedDays(LocalDate, LocalDate)} is the only arithmetic this type does, and it
 * answers exactly one question: how many days in a range did this cadence ask for. That is the
 * denominator in "4 of 5 expected days" — a statement about the log, never a score. There is
 * deliberately no longest-run, no consecutive-day count and no rate.
 */
public enum HabitCadence {

    DAILY,
    WEEKDAYS,
    WEEKLY;

    /**
     * How many days in the inclusive range {@code [from, to]} this cadence asks for.
     *
     * <p>Computed at read time from the dates, never accumulated in a column. A stored counter
     * would have to be maintained by a job, would disagree with the log the first time one
     * failed, and would be a streak in all but name.
     */
    public int expectedDays(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        return switch (this) {
            case DAILY -> (int) (ChronoUnit.DAYS.between(from, to) + 1);
            case WEEKDAYS -> countWeekdays(from, to);
            // One completion per calendar week, so a ten-day range spanning two weeks asks for
            // two. Counting seven-day blocks from the range start instead would make the same
            // fortnight expect a different number depending on which day the report was run.
            case WEEKLY -> (int) (ChronoUnit.WEEKS.between(weekStart(from), weekStart(to)) + 1);
        };
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static int countWeekdays(LocalDate from, LocalDate to) {
        int weekdays = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                weekdays++;
            }
        }
        return weekdays;
    }
}

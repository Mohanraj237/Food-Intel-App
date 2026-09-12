package com.familyfood.habits.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "4 of 5 expected days" — a fact about the log, and nothing else.
 *
 * <p>Two counts and the window they were counted over. Deliberately <b>no</b> percentage, rate,
 * streak, longest run, grade or comparison: those are scores, and a score attached to a child's
 * behaviour is a permanent product boundary rather than a feature awaiting prioritisation (D-036,
 * CH-004). A client that wants to draw five dots has everything it needs; a client that wants to
 * award a trophy has nothing to award it from.
 *
 * @param from the window actually measured, which may be narrower than the window requested:
 *             clamped to the habit's own life and never into the future. A habit created on
 *             Friday has not failed the four days before it existed, and counting them would
 *             turn a log into a bad mark.
 */
public record HabitConsistencyView(
        UUID habitId,
        LocalDate from,
        LocalDate to,
        int completedDays,
        int expectedDays) {
}

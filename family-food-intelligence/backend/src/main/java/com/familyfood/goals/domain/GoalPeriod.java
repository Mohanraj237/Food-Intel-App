package com.familyfood.goals.domain;

/**
 * The window a target applies to.
 *
 * <p>{@code days} lives on the enum rather than in the service so that "weekly" means the
 * same seven days to progress, to the API and to anything built on either. A window length
 * computed independently in two places is a defect waiting for the second caller.
 */
public enum GoalPeriod {

    DAILY(1),
    WEEKLY(7);

    private final int days;

    GoalPeriod(int days) {
        this.days = days;
    }

    /** Length of the window in whole days, counting the end day itself. */
    public int days() {
        return days;
    }
}

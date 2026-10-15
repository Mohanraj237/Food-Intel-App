package com.familyfood.goals.domain;

import com.familyfood.wellness.domain.MetricCategory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The goals a child may have (D-047) — the one definition of it.
 *
 * <p>A child's goal may only aim at more activity, sleep or water, or at keeping one of them
 * steady. No nutrition target of any kind (a calorie, sugar, sodium or macro target for a minor
 * is the restriction framing the product never offers a child), no wellbeing score to aim at,
 * and no "at most" on anything. Routines — "fruit at breakfast", "outside after school" — belong
 * in habits, which carry no numeric target at all.
 *
 * <p>Enforced by {@code GoalService}; published so the experience module can tell a client
 * which goals to offer a child without a second copy of the rule.
 */
public final class ChildGoalRule {

    private static final Set<MetricCategory> CATEGORIES = Collections.unmodifiableSet(
            EnumSet.of(MetricCategory.ACTIVITY, MetricCategory.SLEEP, MetricCategory.HYDRATION));

    private static final Set<GoalDirection> DIRECTIONS = Collections.unmodifiableSet(
            EnumSet.of(GoalDirection.AT_LEAST, GoalDirection.MAINTAIN));

    private ChildGoalRule() {
    }

    public static Set<MetricCategory> categories() {
        return CATEGORIES;
    }

    public static Set<GoalDirection> directions() {
        return DIRECTIONS;
    }

    public static boolean allows(MetricCategory category, GoalDirection direction) {
        return CATEGORIES.contains(category) && DIRECTIONS.contains(direction);
    }
}

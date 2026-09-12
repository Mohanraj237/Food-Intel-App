package com.familyfood.goals.domain;

/**
 * Which way a target points.
 *
 * <p>Deliberately carries no {@code isAchieved(actual, target)} predicate. Two reasons, and
 * both are product decisions rather than omissions: {@link #MAINTAIN} has no tolerance to
 * judge against, so any verdict would be invented; and deciding that a part-logged week
 * "failed" a target is a judgement about missing data, which is exactly the kind of sentence
 * this product does not put in a field (D-037's posture, applied to a self-set target).
 *
 * <p>Progress reports the measured amount, the target, and this direction. The reader draws
 * the conclusion.
 */
public enum GoalDirection {

    /** Reach at least the target — water, protein, steps. */
    AT_LEAST,

    /** Stay at or under the target — sugar, sodium, screen-adjacent intake. */
    AT_MOST,

    /** Stay around the target. No tolerance is stored, so no "within range" is computed. */
    MAINTAIN
}

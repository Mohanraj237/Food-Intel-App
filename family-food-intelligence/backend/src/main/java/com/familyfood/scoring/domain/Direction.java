package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code score_component.direction} CHECK constraint in V5__scoring.sql:
 * whether a higher raw nutrient value should push a component's contribution to the
 * {@code ProductHealthScore} up ({@link #POSITIVE}, e.g. fibre) or down
 * ({@link #NEGATIVE}, e.g. added sugar).
 */
public enum Direction {
    POSITIVE, NEGATIVE
}

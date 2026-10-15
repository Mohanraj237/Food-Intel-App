package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code rule_set.algorithm} CHECK constraint (V16__scoring_v2_engine.sql).
 *
 * <ul>
 *   <li>{@link #WEIGHTED_MEAN_V1} — the original {@code {min,max}} linear-interpolation
 *       weighted mean ({@code WeightedMeanAlgorithm}); kept for the {@code v1-draft-examples}
 *       fixture set and any rule set authored against that shape.
 *   <li>{@link #POINTS_NET_V2} — the FSSAI draft Indian Nutrition Rating points method
 *       ({@code PointsNetAlgorithm}): baseline points minus capped modifying points, mapped
 *       to a star band from {@code rule_set.algorithm_config} (D-059).
 * </ul>
 */
public enum ScoringAlgorithm {
    WEIGHTED_MEAN_V1, POINTS_NET_V2
}

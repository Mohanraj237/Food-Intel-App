package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code score_component.role} CHECK constraint (V16__scoring_v2_engine.sql).
 * {@code BASELINE} rows add points for risk-increasing factors; {@code MODIFYING} rows
 * subtract capped points for positive factors. Only {@code POINTS_NET_V2} reads it.
 */
public enum ScoreComponentRole {
    BASELINE, MODIFYING
}

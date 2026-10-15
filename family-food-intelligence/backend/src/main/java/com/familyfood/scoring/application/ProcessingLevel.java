package com.familyfood.scoring.application;

/**
 * The catalogue's NOVA processing group for the assessed version, as carried into the
 * assessment response. Never a score weight unless a reviewed rule set adds one (D-059).
 * {@code isEstimated} is true whenever the group came from an automated classification
 * (Open Food Facts) rather than a reviewer.
 */
public record ProcessingLevel(int novaGroup, boolean isEstimated, String source) {
}

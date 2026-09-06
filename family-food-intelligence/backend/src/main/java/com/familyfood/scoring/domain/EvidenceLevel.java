package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code reference_standard.evidence_level} CHECK constraint. The table itself
 * is defined and seeded by {@code V1__extensions_and_reference_data.sql} (D-009), whose own
 * column comment is the authoritative definition of these four tiers: A = regulator/official
 * guidance or systematic review; B = peer-reviewed; C = emerging/limited, must disclose
 * uncertainty; D = anecdotal, may not drive claims. Never itself a numeric health threshold.
 */
public enum EvidenceLevel {
    A, B, C, D
}

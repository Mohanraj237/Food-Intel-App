package com.familyfood.dashboard.application;

import com.familyfood.habits.application.HabitService.HabitDay;
import com.familyfood.wellness.domain.MetricCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The household at a glance, over the overview's week (D-054).
 *
 * <p>Computed only from what the caller's own cards already show — see {@link FamilySummaries} —
 * so a total can never reveal a number the caller could not already read on someone's card
 * (D-038's aggregate rule, architecture §11).
 *
 * <p>What it never carries: who contributed what, any ordering of people, a "most" or "least",
 * or any comparison of one person with the family. Those make a leaderboard, and leaderboards
 * between family members are permanently excluded (D-051). Health readings, body measurements
 * and mood are never combined across people at all (D-036, D-037).
 *
 * @param includedMemberIds whose visible records the summary is built from, in card order
 * @param metrics           water, steps and sleep — each only across the people whose category
 *                          the caller can see
 * @param meals             how many people had a meal logged each day; never a kcal total, so a
 *                          child's energy intake is never a family headline (UXD-08)
 * @param habits            habits due and done each day, across the people whose habits the caller
 *                          can see; {@code null} when that is nobody
 *
 * <p>Nulls here and in {@link FamilyDay} are written, not omitted, despite the application-wide
 * {@code non_null} default: a client tells "nobody logged" from a missing field by the null.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FamilySummary(List<UUID> includedMemberIds,
                            List<FamilyMetric> metrics,
                            FamilyCoverage meals,
                            List<HabitDay> habits) {

    /**
     * @param eligiblePeople how many included people's {@code code} the caller can see — the "of m"
     *                       in "logged for n of m people"
     * @param days           the week, oldest first
     */
    public record FamilyMetric(MetricCode code, String unit, int eligiblePeople, List<FamilyDay> days) {}

    /**
     * @param eligiblePeople how many included people's meals the caller can see
     * @param days           the week, oldest first; every {@code total} is {@code null}
     */
    public record FamilyCoverage(int eligiblePeople, List<FamilyDay> days) {}

    /**
     * @param total        the sum over the people who logged that day; {@code null} when nobody did,
     *                     never a zero
     * @param contributors how many of the eligible people logged that day
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FamilyDay(LocalDate date, BigDecimal total, int contributors) {}
}

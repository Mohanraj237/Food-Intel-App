package com.familyfood.dashboard.application;

import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.goals.application.GoalProgressView;
import com.familyfood.habits.application.HabitService.DaySummary;
import com.familyfood.habits.application.HabitService.HabitDay;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "How is my family doing?" — one card per live member, each showing only what that member lets
 * the caller see (D-043, D-044), plus a household summary built from those same cards (D-054).
 *
 * <p>Deliberately absent: any ordering by a measurement, any "most" or "least", any comparison of
 * one member with another or with the family. A family overview that ranks its members is a
 * leaderboard, and leaderboards between family members are permanently excluded (D-051). Cards
 * are ordered by who they are — you, then the children you guard, then everyone else in the order
 * they joined — never by what they did.
 *
 * @param date                      the household-local day every card summarises
 * @param timeZone                  the IANA zone whose midnight bounds it (D-053)
 * @param week                      the seven household-local days ending on {@code date}, oldest
 *                                  first — the days every {@link MetricWeek} and habit week covers
 * @param mealsAwaitingConfirmation the caller's own meal shares not yet in their log (the last two
 *                                  weeks); 0 when none
 * @param summary                   the household at a glance; {@code null} when fewer than two
 *                                  people's records are visible to the caller
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FamilyOverviewView(UUID householdId,
                                 String householdName,
                                 boolean individual,
                                 String timeZone,
                                 LocalDate date,
                                 List<LocalDate> week,
                                 int mealsAwaitingConfirmation,
                                 FamilySummary summary,
                                 List<MemberCard> members) {

    /**
     * One member, as far as the caller may see them.
     *
     * <p>A member who shares nothing with the caller ({@code relationship} NONE) gets an identity
     * card only — name, member type and the coarse child/adult tier the member list already
     * shows — with no metrics, no habits and no consent state. {@code visibleScopes} is then
     * empty, which is how a client tells "not shared with you" from "nothing logged today".
     * Nulls are written, not omitted.
     *
     * @param isYou                 the caller's own card
     * @param experience            the full tier for you, a child you guard or someone who made you
     *                              their caregiver; otherwise only CHILD or ADULT (D-046)
     * @param today                 one row per metric logged today in a visible category
     * @param habits                {@code null} unless HABITS is visible
     * @param trackingConsentActive told only to the member and a child's guardian; {@code null}
     *                              otherwise
     * @param canLogNutrition       whether a meal the caller logs would go straight into this
     *                              member's record (MANAGE on NUTRITION and their tracking consent
     *                              active); otherwise that member's share waits for them (D-041)
     * @param manageScopes          the scopes the caller may log for this member, by name, sorted —
     *                              "You can log: Water, Activity" on a caregiver's card
     * @param week                  one row per metric logged at least once in the week, in a
     *                              visible category
     * @param goals                 live goals in visible categories, measured against the same read
     *                              as {@code week}
     * @param habitsWeek            the week's habits, day by day; {@code null} unless HABITS is
     *                              visible
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MemberCard(UUID memberId,
                             String displayLabel,
                             String memberType,
                             @JsonProperty("isYou") boolean isYou,
                             String relationship,
                             ExperienceTier experience,
                             List<String> visibleScopes,
                             List<MetricDay> today,
                             DaySummary habits,
                             Boolean trackingConsentActive,
                             boolean canLogNutrition,
                             List<String> manageScopes,
                             List<MetricWeek> week,
                             List<GoalProgressView> goals,
                             List<HabitDay> habitsWeek) {}
}

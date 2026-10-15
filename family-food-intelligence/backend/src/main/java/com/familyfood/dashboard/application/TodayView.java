package com.familyfood.dashboard.application;

import com.familyfood.goals.application.GoalProgressView;
import com.familyfood.habits.application.HabitService.DaySummary;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One member's day, as far as the caller may see it (D-044, D-053).
 *
 * <p>Honest by construction: {@code metrics} lists only what was logged (never a zero for what
 * was not); {@code goals} and {@code habits} appear only for scopes the caller may see, and
 * {@code visibleScopes} says which those are, so a client can show "not shared with you" as a
 * designed state instead of an empty tile. Nulls are written, not omitted, for the same reason.
 *
 * @param date                      the household-local day summarised
 * @param timeZone                  the IANA zone whose midnight bounds it (D-053)
 * @param relationship              SELF, GUARDIAN, CAREGIVER or SHARED (NONE is refused)
 * @param metrics                   one row per metric logged that day, in visible categories
 * @param goals                     live goals in visible categories, each over its own period
 *                                  ending on {@code date}; empty when no metric category is visible
 * @param habits                    live habits, due and done on {@code date}; {@code null} unless
 *                                  HABITS is visible
 * @param mealsAwaitingConfirmation the caller's own shares of household meals waiting to be added
 *                                  to their log (last 14 days); 0 for anyone else's day — nobody
 *                                  else needs to know what a person has not yet confirmed
 * @param trackingConsentActive     whether tracking is switched on for this member — told only to
 *                                  the member and a child's guardian; {@code null} otherwise
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TodayView(UUID memberId,
                        String displayLabel,
                        LocalDate date,
                        String timeZone,
                        String relationship,
                        List<String> visibleScopes,
                        List<MetricDay> metrics,
                        List<GoalProgressView> goals,
                        DaySummary habits,
                        int mealsAwaitingConfirmation,
                        Boolean trackingConsentActive) {}

package com.familyfood.experience.application;

import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.goals.domain.ChildGoalRule;
import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the product will do for a member of a given tier (D-046), stated by the server so a
 * client never has to work it out — and so a client that gets it wrong still cannot act on it,
 * because every one of these is also enforced where the data is written.
 *
 * <p>Only capabilities that exist today are listed. There is deliberately no flag for streaks,
 * challenges or notifications: those features are not built (W2, W6), and a flag for a feature
 * that does not exist is an invitation to render one (the release process's no-fake-data rule).
 *
 * @param canTrack                  false until the age band is known (UNKNOWN)
 * @param trackableMetrics          every metric this member may have recorded (D-036)
 * @param goalCategories            categories a goal may target (D-047)
 * @param goalDirections            directions a goal may take (D-047)
 * @param habits                    habits are for everyone, children included
 * @param careFirstHome             OLDER_ADULT: reminders, medicines and appointments lead
 * @param largerTextSuggested       OLDER_ADULT: a larger type size is offered, not imposed
 * @param caregiverInviteProminent  OLDER_ADULT: asking a family member for help is surfaced
 */
public record Capabilities(boolean canTrack,
                           List<MetricCode> trackableMetrics,
                           Set<MetricCategory> goalCategories,
                           Set<GoalDirection> goalDirections,
                           boolean habits,
                           boolean careFirstHome,
                           boolean largerTextSuggested,
                           boolean caregiverInviteProminent) {

    public static Capabilities forTier(ExperienceTier tier) {
        return switch (tier) {
            case UNKNOWN -> new Capabilities(false, List.of(),
                    EnumSet.noneOf(MetricCategory.class), EnumSet.noneOf(GoalDirection.class),
                    true, false, false, false);
            case CHILD -> new Capabilities(true,
                    Arrays.stream(MetricCode.values())
                            .filter(m -> m.permittedFor() == MetricCode.AgeClass.ANY)
                            .toList(),
                    EnumSet.copyOf(ChildGoalRule.categories()),
                    EnumSet.copyOf(ChildGoalRule.directions()),
                    true, false, false, false);
            case ADULT -> adult(false);
            case OLDER_ADULT -> adult(true);
        };
    }

    private static Capabilities adult(boolean olderAdult) {
        // NUTRITION goals are offered to adults; the one direction held back (a calorie cap,
        // D-047) is refused where the goal is created, with its own "not yet" error.
        Set<MetricCategory> goalCategories = EnumSet.allOf(MetricCategory.class);
        return new Capabilities(true, List.of(MetricCode.values()), goalCategories,
                EnumSet.allOf(GoalDirection.class), true, olderAdult, olderAdult, olderAdult);
    }
}

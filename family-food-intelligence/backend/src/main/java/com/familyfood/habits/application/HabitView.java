package com.familyfood.habits.application;

import com.familyfood.habits.domain.Habit;
import com.familyfood.habits.domain.HabitCadence;

import java.time.Instant;
import java.util.UUID;

/**
 * One habit as the API returns it.
 *
 * <p><b>Read the component list as a commitment.</b> There is no streak, no badge, no points, no
 * level, no rank and no comparison against another member — not omitted pending a later sprint,
 * but absent by decision (D-036, CH-004). A test asserts over these component names precisely so
 * that adding one is a visible, failing change rather than a quiet afternoon's work.
 */
public record HabitView(
        UUID id,
        UUID subjectMemberId,
        String name,
        HabitCadence cadence,
        boolean active,
        Instant createdAt,
        Instant archivedAt) {

    public static HabitView from(Habit habit) {
        return new HabitView(
                habit.getId(),
                habit.getHouseholdMemberId(),
                habit.getName(),
                habit.getCadence(),
                habit.isActive(),
                habit.getCreatedAt(),
                habit.getArchivedAt());
    }
}

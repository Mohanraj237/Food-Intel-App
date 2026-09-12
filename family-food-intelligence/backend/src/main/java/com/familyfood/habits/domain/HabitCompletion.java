package com.familyfood.habits.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day on which a habit happened.
 *
 * <p>A {@link LocalDate}, not an instant: "did this happen today" is a calendar question, and a
 * timestamp would make the same day ambiguous across time zones — a 23:50 tick in one zone
 * landing on the next day in another.
 *
 * <p>Immutable, and unique per (habit, day) in the database. Completing twice in a day is the
 * same fact stated twice rather than two facts, so the second statement is a no-op rather than
 * an error the user has to understand. {@link #recordedAt} keeps who said so and when, which is
 * what lets a guardian's entry on a child's habit be distinguished from the child's own.
 */
@Entity
@Table(name = "habit_completion")
public class HabitCompletion {

    @Id
    private UUID id;

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected HabitCompletion() {
        // JPA
    }

    public static HabitCompletion on(UUID habitId,
                                     LocalDate completedOn,
                                     UUID recordedByUserId,
                                     Instant now) {
        HabitCompletion completion = new HabitCompletion();
        completion.id = UUID.randomUUID();
        completion.habitId = habitId;
        completion.completedOn = completedOn;
        completion.recordedByUserId = recordedByUserId;
        completion.recordedAt = now;
        return completion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHabitId() {
        return habitId;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public UUID getRecordedByUserId() {
        return recordedByUserId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}

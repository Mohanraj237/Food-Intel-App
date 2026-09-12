package com.familyfood.habits.infra;

import com.familyfood.habits.domain.HabitCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, UUID> {

    /** The log for a window, newest first. */
    List<HabitCompletion> findByHabitIdAndCompletedOnBetweenOrderByCompletedOnDesc(
            UUID habitId, LocalDate from, LocalDate to);

    /** Mirrors {@code uq_habit_completion_day}, so a repeat tick is answered without an insert. */
    Optional<HabitCompletion> findByHabitIdAndCompletedOn(UUID habitId, LocalDate completedOn);

    /**
     * The numerator in "N of M expected days", counted in the database.
     *
     * <p>A count rather than loading the rows: consistency needs the number, and fetching a
     * month of completions to call {@code size()} on them is work done to throw away.
     */
    long countByHabitIdAndCompletedOnBetween(UUID habitId, LocalDate from, LocalDate to);

    void deleteByHabitIdAndCompletedOn(UUID habitId, LocalDate completedOn);
}

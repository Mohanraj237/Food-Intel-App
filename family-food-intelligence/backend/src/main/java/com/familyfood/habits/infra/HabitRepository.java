package com.familyfood.habits.infra;

import com.familyfood.habits.domain.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

    /** Everything a member has ever tracked, newest first — the "show archived" listing. */
    List<Habit> findByHouseholdMemberIdOrderByCreatedAtDesc(UUID householdMemberId);

    /** What they are tracking now — the default listing. */
    List<Habit> findByHouseholdMemberIdAndActiveTrueOrderByCreatedAtDesc(UUID householdMemberId);

    /** Called by erasure: a habit is the subject's own data. Completions cascade in the DB. */
    void deleteByHouseholdMemberId(UUID householdMemberId);
}

package com.familyfood.nutrition.infra;

import com.familyfood.nutrition.domain.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MealRepository extends JpaRepository<Meal, UUID> {

    /** The household diary for a window, newest first — matches ix_meal_household_consumed. */
    List<Meal> findByHouseholdIdAndConsumedAtBetweenOrderByConsumedAtDesc(
            UUID householdId, Instant from, Instant to);

    /** One member's meals, resolved from their portions, narrowed to the same window. */
    List<Meal> findByIdInAndConsumedAtBetweenOrderByConsumedAtDesc(
            Collection<UUID> ids, Instant from, Instant to);
}

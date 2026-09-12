package com.familyfood.nutrition.infra;

import com.familyfood.nutrition.domain.MealPortion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MealPortionRepository extends JpaRepository<MealPortion, UUID> {

    List<MealPortion> findByMealId(UUID mealId);

    List<MealPortion> findByMealIdIn(Collection<UUID> mealIds);

    /** Which meals one member ate any share of — matches ix_meal_portion_member. */
    List<MealPortion> findByHouseholdMemberId(UUID householdMemberId);

    void deleteByMealId(UUID mealId);
}

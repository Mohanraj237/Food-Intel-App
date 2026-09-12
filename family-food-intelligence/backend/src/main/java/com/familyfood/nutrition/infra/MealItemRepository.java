package com.familyfood.nutrition.infra;

import com.familyfood.nutrition.domain.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MealItemRepository extends JpaRepository<MealItem, UUID> {

    List<MealItem> findByMealId(UUID mealId);

    /** Batched so that listing a week of meals is two queries, not one per meal. */
    List<MealItem> findByMealIdIn(Collection<UUID> mealIds);

    /** An edit rewrites a meal's items wholesale; see {@code MealService.updateMeal}. */
    void deleteByMealId(UUID mealId);
}

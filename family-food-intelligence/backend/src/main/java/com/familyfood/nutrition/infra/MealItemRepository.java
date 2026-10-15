package com.familyfood.nutrition.infra;

import com.familyfood.nutrition.domain.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MealItemRepository extends JpaRepository<MealItem, UUID> {

    List<MealItem> findByMealId(UUID mealId);

    /** Batched so that listing a week of meals is two queries, not one per meal. */
    List<MealItem> findByMealIdIn(Collection<UUID> mealIds);

    /**
     * An edit rewrites a meal's items wholesale; see {@code MealService.updateMeal}.
     *
     * <p>A bulk delete that runs when called, for the same reason as
     * {@code MealPortionRepository.deleteByMealId}: a derived delete is queued behind the
     * re-inserts at flush time, which is the opposite of the order the edit means.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MealItem i where i.mealId = :mealId")
    void deleteByMealId(@Param("mealId") UUID mealId);
}

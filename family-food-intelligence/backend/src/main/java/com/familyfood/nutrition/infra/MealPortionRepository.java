package com.familyfood.nutrition.infra;

import com.familyfood.nutrition.domain.MealPortion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MealPortionRepository extends JpaRepository<MealPortion, UUID> {

    List<MealPortion> findByMealId(UUID mealId);

    List<MealPortion> findByMealIdIn(Collection<UUID> mealIds);

    /** Which meals one member ate any share of — matches ix_meal_portion_member. */
    List<MealPortion> findByHouseholdMemberId(UUID householdMemberId);

    /** A member's shares still waiting for them to add to their own log (D-041). */
    List<MealPortion> findByHouseholdMemberIdAndDerivedAtIsNull(UUID householdMemberId);

    /**
     * A bulk delete, executed immediately — not the derived {@code deleteBy...} form.
     *
     * <p>Editing a meal deletes its portions and re-inserts them, often for the same members.
     * A derived delete only queues {@code em.remove} calls, and Hibernate flushes inserts
     * before deletes, so the new row reached {@code uq_meal_portion_member} while the old one
     * still existed and every edit failed with a 500. A {@code @Modifying} query runs its
     * DELETE at the point it is called, which is the order the service actually means.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MealPortion p where p.mealId = :mealId")
    void deleteByMealId(@Param("mealId") UUID mealId);
}

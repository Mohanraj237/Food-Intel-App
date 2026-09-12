package com.familyfood.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One food in a meal, with the nutrition it contributed for the whole item — not per 100 g,
 * and not per person. Apportioning happens at {@link MealPortion}.
 *
 * <p>The amounts are denormalised on purpose (see V13__nutrition_goals_habits.sql). A food log
 * is a historical record, so it stores what the label said on the day, the way an invoice
 * stores the price at the time of sale. Recomputing from {@code product_nutrient} on every read
 * would make last Tuesday's dinner change when the catalogue is corrected, and
 * {@code productVersionId} already keeps the provenance recoverable.
 *
 * <p>Exactly one of {@code productId} and {@code customName} identifies the food — the two
 * factory methods are the only ways in, so the {@code ck_meal_item_identifies_food} constraint
 * cannot be reached by an object this class can construct.
 */
@Entity
@Table(name = "meal_item")
public class MealItem {

    @Id
    private UUID id;

    @Column(name = "meal_id", nullable = false)
    private UUID mealId;

    @Column(name = "product_id")
    private UUID productId;

    /** The version, not just the product: a reformulation next year must not rewrite last
     *  year's dinner (D-014). */
    @Column(name = "product_version_id")
    private UUID productVersionId;

    @Column(name = "custom_name")
    private String customName;

    @Column(name = "quantity_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityG;

    @Column(name = "energy_kcal", precision = 10, scale = 2)
    private BigDecimal energyKcal;

    @Column(name = "protein_g", precision = 10, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbohydrate_g", precision = 10, scale = 2)
    private BigDecimal carbohydrateG;

    @Column(name = "fat_g", precision = 10, scale = 2)
    private BigDecimal fatG;

    @Column(name = "fibre_g", precision = 10, scale = 2)
    private BigDecimal fibreG;

    @Column(name = "sugar_g", precision = 10, scale = 2)
    private BigDecimal sugarG;

    @Column(name = "sodium_mg", precision = 10, scale = 2)
    private BigDecimal sodiumMg;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MealItem() {
        // JPA
    }

    /** A catalogue food, with the version the amounts were read from. */
    public static MealItem fromCatalogue(UUID mealId,
                                         UUID productId,
                                         UUID productVersionId,
                                         BigDecimal quantityG,
                                         NutritionAmounts amounts,
                                         Instant now) {
        MealItem item = base(mealId, quantityG, amounts, now);
        item.productId = productId;
        item.productVersionId = productVersionId;
        return item;
    }

    /** Something home-cooked or unlabelled, named by whoever logged it. */
    public static MealItem fromCustomFood(UUID mealId,
                                          String customName,
                                          BigDecimal quantityG,
                                          NutritionAmounts amounts,
                                          Instant now) {
        MealItem item = base(mealId, quantityG, amounts, now);
        item.customName = customName;
        return item;
    }

    private static MealItem base(UUID mealId, BigDecimal quantityG, NutritionAmounts amounts, Instant now) {
        MealItem item = new MealItem();
        item.id = UUID.randomUUID();
        item.mealId = mealId;
        item.quantityG = quantityG;
        item.energyKcal = amounts.energyKcal();
        item.proteinG = amounts.proteinG();
        item.carbohydrateG = amounts.carbohydrateG();
        item.fatG = amounts.fatG();
        item.fibreG = amounts.fibreG();
        item.sugarG = amounts.sugarG();
        item.sodiumMg = amounts.sodiumMg();
        item.createdAt = now;
        return item;
    }

    /** What this item contributed, for summing across a meal. Unknown amounts stay unknown. */
    public NutritionAmounts nutrition() {
        return new NutritionAmounts(energyKcal, proteinG, carbohydrateG, fatG, fibreG, sugarG, sodiumMg);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMealId() {
        return mealId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public String getCustomName() {
        return customName;
    }

    public BigDecimal getQuantityG() {
        return quantityG;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

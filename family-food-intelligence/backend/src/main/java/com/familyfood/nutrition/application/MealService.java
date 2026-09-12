package com.familyfood.nutrition.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.NutrientRowDto;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.nutrition.domain.Meal;
import com.familyfood.nutrition.domain.MealItem;
import com.familyfood.nutrition.domain.MealPortion;
import com.familyfood.nutrition.domain.NutritionAmounts;
import com.familyfood.nutrition.infra.MealItemRepository;
import com.familyfood.nutrition.infra.MealPortionRepository;
import com.familyfood.nutrition.infra.MealRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Meal logging with family apportioning (D-039).
 *
 * <h2>Log it once, apportion it</h2>
 * A meal is one composition event with N items and M member portions, not M food diaries. A
 * member's nutrition is the sum of the items multiplied by their {@code portionFraction}, and
 * that product is written into {@code health_observation} as a DERIVED observation tagged with
 * the meal's id. Editing the meal retracts exactly those rows and states fresh ones, which is
 * why a correction cannot double-count and does not need four separate edits.
 *
 * <h2>What this module refuses to do</h2>
 * It stores and it sums. There is no target, no score, no "too much sodium" and no comparison
 * against a goal — naming a number is a clinical claim and needs a citable reviewed standard
 * (D-037). The arithmetic here is addition and multiplication; every judgement is deliberately
 * left without a field to live in.
 *
 * <h2>Scope of a read</h2>
 * A meal is household data by construction: one dinner, four portions, one row. Any live member
 * of the household may therefore read the household's meal log. The per-member
 * <em>observations</em> it derives are a different thing with a stricter rule — those are read
 * back through {@link WellnessAccess}, where default-closed visibility (D-038) applies.
 *
 * <p>Writes are gated by {@link WellnessAccess#replaceDerived}, which applies the D-036 age gate
 * and the D-002 "no adult proxies for another adult" relationship itself. Neither is repeated
 * here: a second copy would be a second thing to keep in step, and letting that exception
 * propagate unchanged is the intended behaviour — a meal apportioned to someone the caller may
 * not record for is refused outright, not silently logged without them.
 */
@Service
public class MealService {

    /** {@code meal_item}'s amount columns are {@code numeric(10,2)}. */
    private static final int ITEM_SCALE = 2;

    /** {@code health_observation.value} is {@code numeric(12,3)}. */
    private static final int OBSERVATION_SCALE = 3;

    private static final int MAX_TEXT_LENGTH = 120;
    private static final BigDecimal MAX_PORTION_FRACTION = new BigDecimal("10");

    /**
     * The catalogue nutrient codes (V1__extensions_and_reference_data.sql) that map onto the
     * seven amounts {@code meal_item} stores. The catalogue's vocabulary is wider; the rest is
     * not dropped by oversight, it simply has nowhere to go in this table.
     *
     * <p>{@code sugar_g} reads {@code TOTAL_SUGAR_G}, never {@code ADDED_SUGAR_G}. Those are
     * separate catalogue rows precisely so the two are never conflated, and a log that filed
     * added sugar as total sugar would understate one while overstating the other.
     */
    private static final String CODE_ENERGY = "ENERGY_KCAL";
    private static final String CODE_PROTEIN = "PROTEIN_G";
    private static final String CODE_CARBOHYDRATE = "CARB_G";
    private static final String CODE_FAT = "TOTAL_FAT_G";
    private static final String CODE_FIBRE = "FIBRE_G";
    private static final String CODE_SUGAR = "TOTAL_SUGAR_G";
    private static final String CODE_SODIUM = "SODIUM_MG";

    private final MealRepository meals;
    private final MealItemRepository mealItems;
    private final MealPortionRepository mealPortions;
    private final ProductQueryService catalog;
    private final HouseholdAccess households;
    private final WellnessAccess wellness;
    private final Clock clock;

    public MealService(MealRepository meals,
                       MealItemRepository mealItems,
                       MealPortionRepository mealPortions,
                       ProductQueryService catalog,
                       HouseholdAccess households,
                       WellnessAccess wellness,
                       Clock clock) {
        this.meals = meals;
        this.mealItems = mealItems;
        this.mealPortions = mealPortions;
        this.catalog = catalog;
        this.households = households;
        this.wellness = wellness;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------------- writing */

    /** Logs one meal with its items and portions, and derives every member's nutrition. */
    @Transactional
    public MealView createMeal(UUID callerAccountId, CreateMealCommand command) {
        List<MemberInfo> liveMembers = requireCallerIsLiveMember(callerAccountId, command.householdId());
        requireItemsAndPortions(command.items(), command.portions());
        requirePortionsAreLiveMembers(command.portions(), liveMembers);
        requireShortText(command.name());

        Instant now = clock.instant();
        Instant consumedAt = requireNotFuture(command.consumedAt(), now);

        Meal meal = meals.save(Meal.log(command.householdId(), command.mealType(), command.name(),
                consumedAt, callerAccountId, now));
        List<MealItem> items = saveItems(meal.getId(), command.items(), now);
        List<MealPortion> portions = savePortions(meal.getId(), command.portions(), now);

        return apportionAndView(callerAccountId, meal, items, portions);
    }

    /**
     * Replaces a meal's items and portions, then recomputes what each member ate.
     *
     * <p>The whole source is retracted before the new shares are written, not only the members
     * the update names: {@code replaceDerived} can retract for a subject only if it is told
     * about that subject, so a member dropped from the meal would otherwise keep their share of
     * a meal they are no longer part of.
     */
    @Transactional
    public MealView updateMeal(UUID callerAccountId, UUID mealId, UpdateMealCommand command) {
        Meal meal = requireMeal(mealId);
        List<MemberInfo> liveMembers = requireCallerIsLiveMember(callerAccountId, meal.getHouseholdId());
        requireItemsAndPortions(command.items(), command.portions());
        requirePortionsAreLiveMembers(command.portions(), liveMembers);
        requireShortText(command.name());

        Instant now = clock.instant();
        Instant consumedAt = command.consumedAt() == null
                ? meal.getConsumedAt()
                : requireNotFuture(command.consumedAt(), now);

        meal.revise(command.mealType(), command.name(), consumedAt, now);
        meals.save(meal);

        mealItems.deleteByMealId(mealId);
        mealPortions.deleteByMealId(mealId);
        List<MealItem> items = saveItems(mealId, command.items(), now);
        List<MealPortion> portions = savePortions(mealId, command.portions(), now);

        wellness.retractDerived(callerAccountId, mealId);
        return apportionAndView(callerAccountId, meal, items, portions);
    }

    /**
     * Removes a meal and everything derived from it.
     *
     * <p>The retraction comes first, so that if it fails the whole transaction rolls back. A
     * deleted meal whose observations survived would leave nutrition attributed to a member
     * with nothing left that explains it.
     */
    @Transactional
    public void deleteMeal(UUID callerAccountId, UUID mealId) {
        Meal meal = requireMeal(mealId);
        requireCallerIsLiveMember(callerAccountId, meal.getHouseholdId());

        wellness.retractDerived(callerAccountId, mealId);
        mealItems.deleteByMealId(mealId);
        mealPortions.deleteByMealId(mealId);
        meals.delete(meal);
    }

    /* ------------------------------------------------------------------------- reading */

    @Transactional(readOnly = true)
    public MealView getMeal(UUID callerAccountId, UUID mealId) {
        Meal meal = requireMeal(mealId);
        requireCallerIsLiveMember(callerAccountId, meal.getHouseholdId());
        return viewOf(meal, mealItems.findByMealId(mealId), mealPortions.findByMealId(mealId));
    }

    /** A household's meal log for a window, newest first. */
    @Transactional(readOnly = true)
    public List<MealView> listMeals(UUID callerAccountId, UUID householdId, Instant from, Instant to) {
        requireCallerIsLiveMember(callerAccountId, householdId);
        return viewsOf(meals.findByHouseholdIdAndConsumedAtBetweenOrderByConsumedAtDesc(householdId, from, to));
    }

    /**
     * The meals one member had a share of, in a window.
     *
     * <p>Resolved through that member's portions rather than by filtering the household listing,
     * so a meal nobody served them is not listed as theirs.
     */
    @Transactional(readOnly = true)
    public List<MealView> listMealsForMember(UUID callerAccountId, UUID memberId, Instant from, Instant to) {
        MemberInfo subject = households.findMember(memberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        requireCallerIsLiveMember(callerAccountId, subject.householdId());

        List<UUID> mealIds = mealPortions.findByHouseholdMemberId(memberId).stream()
                .map(MealPortion::getMealId)
                .distinct()
                .toList();
        if (mealIds.isEmpty()) {
            return List.of();
        }
        return viewsOf(meals.findByIdInAndConsumedAtBetweenOrderByConsumedAtDesc(mealIds, from, to));
    }

    /* ------------------------------------------------------------------- apportioning */

    /**
     * Writes each member's share into wellness, then returns the meal as the API shows it.
     *
     * <p>Portions are processed in member-id order. The repository promises no ordering, and a
     * stable one means two identical calls write in the same sequence and return the same list,
     * which is what makes a partial failure here reproducible.
     */
    private MealView apportionAndView(UUID callerAccountId,
                                      Meal meal,
                                      List<MealItem> items,
                                      List<MealPortion> portions) {
        NutritionAmounts total = totalOf(items);
        for (MealPortion portion : byMemberId(portions)) {
            NutritionAmounts share = total.scaledTo(portion.getPortionFraction(), OBSERVATION_SCALE);
            wellness.replaceDerived(callerAccountId, portion.getHouseholdMemberId(), meal.getId(),
                    metricValues(share), meal.getConsumedAt());
        }
        return viewOf(meal, items, portions);
    }

    /**
     * The observation values for one member's share.
     *
     * <p>A metric with no data is left out, never written as zero. The distinction carries
     * weight: zero fibre is a statement about what someone ate, an absent key is a statement
     * about what we know. A meal with nothing known at all yields an empty map, which
     * {@code replaceDerived} treats as a retraction — correct, since there is nothing honest to
     * record for it.
     */
    private static Map<MetricCode, BigDecimal> metricValues(NutritionAmounts amounts) {
        Map<MetricCode, BigDecimal> values = new EnumMap<>(MetricCode.class);
        putIfKnown(values, MetricCode.ENERGY_KCAL, amounts.energyKcal());
        putIfKnown(values, MetricCode.PROTEIN_G, amounts.proteinG());
        putIfKnown(values, MetricCode.CARBOHYDRATE_G, amounts.carbohydrateG());
        putIfKnown(values, MetricCode.FAT_G, amounts.fatG());
        putIfKnown(values, MetricCode.FIBRE_G, amounts.fibreG());
        putIfKnown(values, MetricCode.SUGAR_G, amounts.sugarG());
        putIfKnown(values, MetricCode.SODIUM_MG, amounts.sodiumMg());
        return values;
    }

    private static void putIfKnown(Map<MetricCode, BigDecimal> values, MetricCode code, BigDecimal amount) {
        if (amount != null) {
            values.put(code, amount);
        }
    }

    private static NutritionAmounts totalOf(List<MealItem> items) {
        NutritionAmounts total = NutritionAmounts.NONE;
        for (MealItem item : items) {
            total = total.plus(item.nutrition());
        }
        return total;
    }

    /* ------------------------------------------------------------------------ persisting */

    private List<MealItem> saveItems(UUID mealId, List<MealItemCommand> commands, Instant now) {
        List<MealItem> saved = new ArrayList<>();
        for (MealItemCommand command : commands) {
            BigDecimal quantityG = command.quantityG();
            if (quantityG == null || quantityG.signum() <= 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }

            boolean fromCatalogue = command.productId() != null;
            boolean custom = command.customName() != null && !command.customName().isBlank();
            if (fromCatalogue == custom) {
                // Exactly one of the two identifies the food, as ck_meal_item_identifies_food
                // requires. Checked here so the caller gets a 400 with a message instead of a
                // constraint violation surfacing as a 500.
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }

            // Dividing by 100 by moving the decimal point is exact for any quantity the column
            // can hold, where divide(BigDecimal) would risk a non-terminating expansion.
            BigDecimal per100Factor = quantityG.movePointLeft(2);

            if (fromCatalogue) {
                saved.add(mealItems.save(catalogueItem(mealId, command, quantityG, per100Factor, now)));
            } else {
                requireShortText(command.customName());
                NutritionAmounts per100 = command.nutritionPer100() == null
                        ? NutritionAmounts.NONE
                        : command.nutritionPer100();
                if (per100.hasNegativeAmount()) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }
                saved.add(mealItems.save(MealItem.fromCustomFood(mealId, command.customName().strip(),
                        quantityG, per100.scaledTo(per100Factor, ITEM_SCALE), now)));
            }
        }
        return saved;
    }

    /**
     * A catalogue food, resolved through the published catalogue read service — the same path
     * the product screen uses, so "what does this pack declare" has one implementation rather
     * than a second one here that could drift away from it.
     *
     * <p>The product's <em>current</em> version is resolved now and stored on the item, so the
     * log keeps saying what the label said on the day it was eaten (D-014).
     */
    private MealItem catalogueItem(UUID mealId,
                                   MealItemCommand command,
                                   BigDecimal quantityG,
                                   BigDecimal per100Factor,
                                   Instant now) {
        ProductDetailDto product = catalog.getProduct(command.productId());
        NutritionAmounts amounts = per100Of(product).scaledTo(per100Factor, ITEM_SCALE);
        return MealItem.fromCatalogue(mealId, command.productId(), product.currentVersion().id(),
                quantityG, amounts, now);
    }

    /**
     * The pack's per-100 declarations, mapped onto the seven amounts a meal stores.
     *
     * <p>A nutrient declared per serving only has no per-100 figure and stays absent rather than
     * being back-calculated: that needs a serving size we may not have, and would put a number
     * on the log the label never stated. A caller-supplied per-100 map is ignored entirely for a
     * catalogue product — a meal log must not be able to restate what a pack declares.
     *
     * <p>{@code quantityG} is applied to a per-100 declaration whether the pack declared per
     * 100 g or per 100 ml: the schema stores one {@code quantity_g}, so a drink logged in
     * millilitres is read at the same magnitude.
     */
    private static NutritionAmounts per100Of(ProductDetailDto product) {
        Map<String, BigDecimal> declared = new HashMap<>();
        for (NutrientRowDto row : product.nutrients()) {
            if (row.amountPer100() != null) {
                declared.put(row.nutrientCode(), row.amountPer100());
            }
        }
        return new NutritionAmounts(
                declared.get(CODE_ENERGY),
                declared.get(CODE_PROTEIN),
                declared.get(CODE_CARBOHYDRATE),
                declared.get(CODE_FAT),
                declared.get(CODE_FIBRE),
                declared.get(CODE_SUGAR),
                declared.get(CODE_SODIUM));
    }

    private List<MealPortion> savePortions(UUID mealId, List<MealPortionCommand> commands, Instant now) {
        List<MealPortion> saved = new ArrayList<>();
        for (MealPortionCommand command : commands) {
            saved.add(mealPortions.save(MealPortion.apportion(
                    mealId, command.householdMemberId(), command.portionFraction(), now)));
        }
        return saved;
    }

    /* ---------------------------------------------------------------------- validation */

    private Meal requireMeal(UUID mealId) {
        return meals.findById(mealId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * The caller must be a live member of the meal's household. The live membership is returned
     * because every portion then has to be checked against exactly that set.
     *
     * <p>{@link HouseholdAccess#listActiveMembers} is the only sanctioned way to enumerate
     * membership (D-023); household's repositories stay private to household.
     */
    private List<MemberInfo> requireCallerIsLiveMember(UUID callerAccountId, UUID householdId) {
        List<MemberInfo> liveMembers = households.listActiveMembers(householdId);
        boolean callerBelongs = liveMembers.stream()
                .anyMatch(member -> callerAccountId.equals(member.userAccountId()));
        if (!callerBelongs) {
            throw new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER);
        }
        return liveMembers;
    }

    /**
     * A meal with no items is a share of nothing; a meal with no portions records nothing for
     * anyone. Each is refused with its own code so a client can say which happened.
     */
    private static void requireItemsAndPortions(List<MealItemCommand> items,
                                                List<MealPortionCommand> portions) {
        if (items == null || items.isEmpty()) {
            throw new AppException(ErrorCode.MEAL_HAS_NO_ITEMS);
        }
        if (portions == null || portions.isEmpty()) {
            throw new AppException(ErrorCode.MEAL_HAS_NO_PORTIONS);
        }
    }

    /**
     * Every portion must name a live member of this meal's household.
     *
     * <p>A boundary check, not an authorization check: whether the caller may record nutrition
     * <em>for</em> that member is decided by {@link WellnessAccess}, which knows the age gate and
     * the guardian relationship. Both run — a portion for someone outside the household is
     * refused here, and a portion for another adult is refused there.
     */
    private static void requirePortionsAreLiveMembers(List<MealPortionCommand> portions,
                                                      List<MemberInfo> liveMembers) {
        Set<UUID> liveMemberIds = liveMembers.stream().map(MemberInfo::id).collect(Collectors.toSet());
        Set<UUID> named = new HashSet<>();
        for (MealPortionCommand portion : portions) {
            if (portion.householdMemberId() == null || portion.portionFraction() == null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
            if (!liveMemberIds.contains(portion.householdMemberId())) {
                throw new AppException(ErrorCode.PORTION_MEMBER_NOT_IN_HOUSEHOLD);
            }
            if (portion.portionFraction().signum() <= 0
                    || portion.portionFraction().compareTo(MAX_PORTION_FRACTION) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
            if (!named.add(portion.householdMemberId())) {
                // One person's share of one meal is one fact. A second row for the same member
                // would trip uq_meal_portion_member as a 500, and quietly adding the two would
                // be a guess about which the user meant.
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    /**
     * A meal cannot have been eaten in the future. {@code consumedAt} also becomes the derived
     * observations' {@code effectiveAt}, so a date-picker slip here would poison every "this
     * week" total rather than just this one row.
     */
    private static Instant requireNotFuture(Instant consumedAt, Instant now) {
        if (consumedAt == null) {
            return now;
        }
        if (consumedAt.isAfter(now)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        return consumedAt;
    }

    /** The 1..120 trimmed length the {@code name} and {@code custom_name} CHECKs allow. */
    private static void requireShortText(String text) {
        if (text != null && text.strip().length() > MAX_TEXT_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /* ----------------------------------------------------------------------- assembly */

    private List<MealView> viewsOf(List<Meal> window) {
        if (window.isEmpty()) {
            return List.of();
        }
        List<UUID> mealIds = window.stream().map(Meal::getId).toList();

        // Batched: a week of meals costs two extra queries rather than two per meal.
        Map<UUID, List<MealItem>> itemsByMeal = mealItems.findByMealIdIn(mealIds).stream()
                .collect(Collectors.groupingBy(MealItem::getMealId));
        Map<UUID, List<MealPortion>> portionsByMeal = mealPortions.findByMealIdIn(mealIds).stream()
                .collect(Collectors.groupingBy(MealPortion::getMealId));

        return window.stream()
                .map(meal -> viewOf(meal,
                        itemsByMeal.getOrDefault(meal.getId(), List.of()),
                        portionsByMeal.getOrDefault(meal.getId(), List.of())))
                .toList();
    }

    private static MealView viewOf(Meal meal, List<MealItem> items, List<MealPortion> portions) {
        NutritionAmounts total = totalOf(items);
        List<MealPortionView> portionViews = byMemberId(portions).stream()
                .map(portion -> new MealPortionView(
                        portion.getId(),
                        portion.getHouseholdMemberId(),
                        portion.getPortionFraction(),
                        total.scaledTo(portion.getPortionFraction(), OBSERVATION_SCALE)))
                .toList();

        return new MealView(
                meal.getId(),
                meal.getHouseholdId(),
                meal.getMealType(),
                meal.getName(),
                meal.getConsumedAt(),
                total,
                items.stream().map(MealItemView::from).toList(),
                portionViews);
    }

    private static List<MealPortion> byMemberId(List<MealPortion> portions) {
        return portions.stream()
                .sorted(Comparator.comparing(MealPortion::getHouseholdMemberId))
                .toList();
    }
}

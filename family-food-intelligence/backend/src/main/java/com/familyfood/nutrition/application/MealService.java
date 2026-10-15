package com.familyfood.nutrition.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
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
import com.familyfood.wellness.domain.MetricCategory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Meal logging with family apportioning (D-039, D-041).
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
 * (D-037). The arithmetic here is addition and multiplication.
 *
 * <h2>Who a meal writes for (D-041, D-044, D-048)</h2>
 * A meal and its portions are household facts; the nutrition derived from a portion is that
 * member's health record. So a meal derives immediately only for members the logger may
 * already write NUTRITION for — themselves, children they guard, and anyone who made them a
 * NUTRITION caregiver (D-045) — and only while that member's tracking consent is active. Every
 * other share is stored and waits: another adult {@linkplain #confirmPortion confirms} their own.
 *
 * <h2>Who a meal is shown to (D-044) — fixed in W1</h2>
 * Before W1 any household member could list every meal in the household, including another
 * adult's solo lunch and the nutrition of every share (D-042, defect 1). Now:
 * <ul>
 *   <li>A meal is visible to the member who logged it, to everyone who was served in it, and to
 *       anyone who may see the NUTRITION of at least one person served. A meal the caller had no
 *       part in, about people who share nothing with them, is not returned — or, by id, is
 *       reported as not found, because its existence is itself private.</li>
 *   <li>Within a visible meal, the dish and who was served are shown; each share's nutrition is
 *       shown only if the caller may see that member's NUTRITION, and is otherwise withheld.</li>
 * </ul>
 */
@Service
public class MealService {

    /** {@code meal_item}'s amount columns are {@code numeric(10,2)}. */
    private static final int ITEM_SCALE = 2;

    /** {@code health_observation.value} is {@code numeric(12,3)}. */
    private static final int OBSERVATION_SCALE = 3;

    private static final int MAX_TEXT_LENGTH = 120;
    private static final String RESOURCE_TYPE_MEAL = "MEAL";
    private static final String CHILD_MEMBER_TYPE = "CHILD";
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
    private final HealthAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;

    public MealService(MealRepository meals,
                       MealItemRepository mealItems,
                       MealPortionRepository mealPortions,
                       ProductQueryService catalog,
                       HouseholdAccess households,
                       WellnessAccess wellness,
                       HealthAccessPolicy access,
                       AuditService audit,
                       Clock clock) {
        this.meals = meals;
        this.mealItems = mealItems;
        this.mealPortions = mealPortions;
        this.catalog = catalog;
        this.households = households;
        this.wellness = wellness;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------------- writing */

    /** Logs one meal with its items and portions, and derives every share it may. */
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

        audit.success(AuditAction.MEAL_LOGGED, RESOURCE_TYPE_MEAL, meal.getId(),
                callerAccountId, meal.getHouseholdId());
        auditChildPortions(callerAccountId, meal, portions, liveMembers);
        return apportionAndView(callerAccountId, meal, items, portions, now);
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
        requireLoggedByCaller(callerAccountId, meal);
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

        // Every share derived from the old meal is withdrawn, confirmed ones included: a
        // derived share only ever meant "my portion of this meal", and this meal is no longer
        // the one that was confirmed (D-041). Other adults' fresh portions start unconfirmed.
        wellness.retractDerived(callerAccountId, mealId);
        audit.success(AuditAction.MEAL_UPDATED, RESOURCE_TYPE_MEAL, mealId,
                callerAccountId, meal.getHouseholdId());
        auditChildPortions(callerAccountId, meal, portions, liveMembers);
        return apportionAndView(callerAccountId, meal, items, portions, now);
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
        requireLoggedByCaller(callerAccountId, meal);

        wellness.retractDerived(callerAccountId, mealId);
        mealItems.deleteByMealId(mealId);
        mealPortions.deleteByMealId(mealId);
        meals.delete(meal);
        audit.success(AuditAction.MEAL_DELETED, RESOURCE_TYPE_MEAL, mealId,
                callerAccountId, meal.getHouseholdId());
    }

    /**
     * An adult adds their own share of a household meal to their own log (D-041).
     *
     * <p>The only way a portion someone else apportioned to an adult enters that adult's
     * record. The caller is the subject, so this is a self-write: it passes the access policy as
     * SELF and still needs their own tracking consent (D-048), which {@code replaceDerived}
     * checks.
     */
    @Transactional
    public MealView confirmPortion(UUID callerAccountId, UUID mealId) {
        Meal meal = requireMeal(mealId);
        List<MemberInfo> liveMembers = requireCallerIsLiveMember(callerAccountId, meal.getHouseholdId());

        MemberInfo self = liveMembers.stream()
                .filter(m -> callerAccountId.equals(m.userAccountId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER));

        List<MealPortion> portions = mealPortions.findByMealId(mealId);
        MealPortion mine = portions.stream()
                .filter(p -> p.getHouseholdMemberId().equals(self.id()))
                .findFirst()
                // Confirming a meal you were not served is not a correction, it is a new
                // claim — and that belongs in logging a meal, not here.
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        List<MealItem> items = mealItems.findByMealId(mealId);
        NutritionAmounts share = totalOf(items).scaledTo(mine.getPortionFraction(), OBSERVATION_SCALE);
        wellness.replaceDerived(callerAccountId, self.id(), mealId, metricValues(share), meal.getConsumedAt());
        mine.markDerived(clock.instant());
        mealPortions.save(mine);

        audit.successForSubject(AuditAction.MEAL_PORTION_CONFIRMED, RESOURCE_TYPE_MEAL, mealId,
                callerAccountId, meal.getHouseholdId(), self.id(), Map.of("scope", AccessScope.NUTRITION.name()));

        return new Reader(callerAccountId).viewOf(meal, items, portions);
    }

    /* ------------------------------------------------------------------------- reading */

    /** One meal, if the caller may see it; otherwise not found — its existence is private. */
    @Transactional(readOnly = true)
    public MealView getMeal(UUID callerAccountId, UUID mealId) {
        Meal meal = requireMeal(mealId);
        requireCallerIsLiveMember(callerAccountId, meal.getHouseholdId());

        Reader reader = new Reader(callerAccountId);
        List<MealPortion> portions = mealPortions.findByMealId(mealId);
        if (!reader.maySee(meal, portions)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        MealView view = reader.viewOf(meal, mealItems.findByMealId(mealId), portions);
        reader.auditDisclosures();
        return view;
    }

    /** The household's meals the caller may see, in a window, newest first. */
    @Transactional(readOnly = true)
    public List<MealView> listMeals(UUID callerAccountId, UUID householdId, Instant from, Instant to) {
        requireCallerIsLiveMember(callerAccountId, householdId);
        Reader reader = new Reader(callerAccountId);
        List<MealView> views = reader.viewsOf(
                meals.findByHouseholdIdAndConsumedAtBetweenOrderByConsumedAtDesc(householdId, from, to));
        reader.auditDisclosures();
        return views;
    }

    /**
     * The meals one member had a share of, in a window — which is that member's nutrition
     * history, so it needs VIEW on their NUTRITION (self, guardian, or a grant). Resolved through
     * the member's portions rather than by filtering the household listing, so a meal nobody
     * served them is not listed as theirs.
     */
    @Transactional(readOnly = true)
    public List<MealView> listMealsForMember(UUID callerAccountId, UUID memberId, Instant from, Instant to) {
        access.requireView(callerAccountId, memberId, AccessScope.NUTRITION, RESOURCE_TYPE_MEAL);

        List<UUID> mealIds = mealPortions.findByHouseholdMemberId(memberId).stream()
                .map(MealPortion::getMealId)
                .distinct()
                .toList();
        if (mealIds.isEmpty()) {
            return List.of();
        }
        Reader reader = new Reader(callerAccountId);
        List<MealView> views = reader.viewsOf(
                meals.findByIdInAndConsumedAtBetweenOrderByConsumedAtDesc(mealIds, from, to));
        reader.auditDisclosures();
        return views;
    }

    /**
     * How many of a member's shares are waiting for them to add to their own log — the Today
     * screen's "2 meals to confirm". Self only: nobody else needs to know what someone has not
     * yet confirmed.
     */
    @Transactional(readOnly = true)
    public int countAwaitingConfirmation(UUID callerAccountId, UUID memberId, Instant since) {
        AccessDecision decision = access.decide(callerAccountId, memberId);
        if (!decision.isSelf()) {
            return 0;
        }
        List<MealPortion> waiting = mealPortions.findByHouseholdMemberIdAndDerivedAtIsNull(memberId);
        if (waiting.isEmpty()) {
            return 0;
        }
        Set<UUID> mealIds = waiting.stream().map(MealPortion::getMealId).collect(Collectors.toSet());
        return meals.findByIdInAndConsumedAtBetweenOrderByConsumedAtDesc(List.copyOf(mealIds), since, clock.instant())
                .size();
    }

    /* ------------------------------------------------------------------- data rights */

    /**
     * Account or child erasure (D-028): the member's shares go, and with them the member from
     * every meal. A meal left with nobody's share in it records nothing for anyone and goes too;
     * a meal other people also ate stays — it is their record as much as it was this member's.
     * Derived observations are erased with the member's other observations by wellness.
     */
    @Transactional
    public void eraseForMember(UUID memberId) {
        List<MealPortion> mine = mealPortions.findByHouseholdMemberId(memberId);
        Set<UUID> touchedMeals = mine.stream().map(MealPortion::getMealId).collect(Collectors.toSet());
        mealPortions.deleteAll(mine);
        mealPortions.flush();
        for (UUID mealId : touchedMeals) {
            if (mealPortions.findByMealId(mealId).isEmpty()) {
                mealItems.deleteByMealId(mealId);
                meals.findById(mealId).ifPresent(meals::delete);
            }
        }
    }

    /**
     * The member's own meal history for their export: each meal they had a share of, with what
     * was in it and their own share — never anyone else's share.
     */
    @Transactional(readOnly = true)
    public List<MealView> exportForMember(UUID memberId) {
        List<MealPortion> mine = mealPortions.findByHouseholdMemberId(memberId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, MealPortion> byMeal = mine.stream()
                .collect(Collectors.toMap(MealPortion::getMealId, p -> p, (a, b) -> a, LinkedHashMap::new));
        List<Meal> found = meals.findAllById(byMeal.keySet());
        Map<UUID, List<MealItem>> itemsByMeal = mealItems.findByMealIdIn(byMeal.keySet()).stream()
                .collect(Collectors.groupingBy(MealItem::getMealId));
        return found.stream()
                .sorted(Comparator.comparing(Meal::getConsumedAt).reversed())
                .map(meal -> assemble(meal, itemsByMeal.getOrDefault(meal.getId(), List.of()),
                        List.of(byMeal.get(meal.getId())), member -> true))
                .toList();
    }

    /* ------------------------------------------------------------------- apportioning */

    /**
     * Writes each share the logger may derive into wellness, then returns the meal as the API
     * shows it to them.
     *
     * <p>Portions are processed in member-id order. The repository promises no ordering, and a
     * stable one means two identical calls write in the same sequence and return the same list,
     * which is what makes a partial failure here reproducible.
     */
    private MealView apportionAndView(UUID callerAccountId,
                                      Meal meal,
                                      List<MealItem> items,
                                      List<MealPortion> portions,
                                      Instant now) {
        NutritionAmounts total = totalOf(items);
        for (MealPortion portion : byMemberId(portions)) {
            // Only shares the logger may write NUTRITION for, into a record whose tracking
            // consent is active. Everything else is stored and waits (D-041, D-048). The age
            // gate still runs inside replaceDerived for everyone who is written for.
            if (!wellness.mayDeriveFor(callerAccountId, portion.getHouseholdMemberId(), MetricCategory.NUTRITION)) {
                continue;
            }
            NutritionAmounts share = total.scaledTo(portion.getPortionFraction(), OBSERVATION_SCALE);
            wellness.replaceDerived(callerAccountId, portion.getHouseholdMemberId(), meal.getId(),
                    metricValues(share), meal.getConsumedAt());
            portion.markDerived(now);
            mealPortions.save(portion);
        }
        return new Reader(callerAccountId).viewOf(meal, items, portions);
    }

    /** Only the member who logged a meal may change or remove it (D-041). */
    private static void requireLoggedByCaller(UUID callerAccountId, Meal meal) {
        if (!callerAccountId.equals(meal.getCreatedByUserId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * A meal apportioned to a child writes child data, and every write of child data is
     * audited in the strict category as well as the ordinary one.
     */
    private void auditChildPortions(UUID callerAccountId, Meal meal,
                                    List<MealPortion> portions, List<MemberInfo> liveMembers) {
        for (MealPortion portion : portions) {
            boolean isChild = liveMembers.stream()
                    .anyMatch(m -> m.id().equals(portion.getHouseholdMemberId())
                            && CHILD_MEMBER_TYPE.equals(m.memberType()));
            if (isChild) {
                audit.successForSubject(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_MEAL, meal.getId(),
                        callerAccountId, meal.getHouseholdId(), portion.getHouseholdMemberId(),
                        Map.of("scope", AccessScope.NUTRITION.name()));
            }
        }
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
     * <em>for</em> that member is decided by the access policy through {@link WellnessAccess}.
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

    /**
     * One caller's view of meals for the length of one request: which members' nutrition they
     * may see, asked once per member and remembered, and which of those were other people —
     * so each disclosure is audited once, not once per meal (D-044).
     */
    private final class Reader {

        private final UUID callerAccountId;
        private final Map<UUID, Optional<AccessDecision>> decisions = new HashMap<>();
        private final Map<UUID, AccessDecision> disclosed = new LinkedHashMap<>();

        Reader(UUID callerAccountId) {
            this.callerAccountId = callerAccountId;
        }

        /** Whether the caller may see this member's NUTRITION. A departed member: no. */
        boolean mayViewNutritionOf(UUID memberId) {
            return decision(memberId).map(d -> d.canView(AccessScope.NUTRITION)).orElse(false);
        }

        boolean isCaller(UUID memberId) {
            return decision(memberId).map(AccessDecision::isSelf).orElse(false);
        }

        /** Logged it, ate it, or may see the nutrition of someone who did. */
        boolean maySee(Meal meal, List<MealPortion> portions) {
            if (callerAccountId.equals(meal.getCreatedByUserId())) {
                return true;
            }
            for (MealPortion portion : portions) {
                if (isCaller(portion.getHouseholdMemberId()) || mayViewNutritionOf(portion.getHouseholdMemberId())) {
                    return true;
                }
            }
            return false;
        }

        List<MealView> viewsOf(List<Meal> window) {
            if (window.isEmpty()) {
                return List.of();
            }
            List<UUID> mealIds = window.stream().map(Meal::getId).toList();

            // Batched: a week of meals costs two extra queries rather than two per meal.
            Map<UUID, List<MealItem>> itemsByMeal = mealItems.findByMealIdIn(mealIds).stream()
                    .collect(Collectors.groupingBy(MealItem::getMealId));
            Map<UUID, List<MealPortion>> portionsByMeal = mealPortions.findByMealIdIn(mealIds).stream()
                    .collect(Collectors.groupingBy(MealPortion::getMealId));

            List<MealView> views = new ArrayList<>();
            for (Meal meal : window) {
                List<MealPortion> portions = portionsByMeal.getOrDefault(meal.getId(), List.of());
                if (maySee(meal, portions)) {
                    views.add(viewOf(meal, itemsByMeal.getOrDefault(meal.getId(), List.of()), portions));
                }
            }
            return views;
        }

        MealView viewOf(Meal meal, List<MealItem> items, List<MealPortion> portions) {
            return assemble(meal, items, portions, memberId -> {
                boolean visible = mayViewNutritionOf(memberId);
                if (visible) {
                    decision(memberId).filter(AccessDecision::isCrossMember)
                            .ifPresent(d -> disclosed.putIfAbsent(memberId, d));
                }
                return visible;
            });
        }

        /** One access event per person whose nutrition this request showed to someone else. */
        void auditDisclosures() {
            disclosed.values().forEach(d ->
                    access.auditRead(callerAccountId, d, List.of(AccessScope.NUTRITION), RESOURCE_TYPE_MEAL));
        }

        /**
         * A member who has left the household has no decision — their share is shown without
         * numbers. Checked up front rather than by catching the policy's not-found: an exception
         * leaving a transactional method marks the whole request's transaction rollback-only,
         * even when it is caught here.
         */
        private Optional<AccessDecision> decision(UUID memberId) {
            return decisions.computeIfAbsent(memberId, id -> households.findMember(id)
                    .filter(MemberInfo::active)
                    .map(member -> access.decide(callerAccountId, id)));
        }
    }

    private static MealView assemble(Meal meal,
                                     List<MealItem> items,
                                     List<MealPortion> portions,
                                     java.util.function.Predicate<UUID> nutritionVisibleFor) {
        NutritionAmounts total = totalOf(items);
        List<MealPortionView> portionViews = byMemberId(portions).stream()
                .map(portion -> {
                    boolean visible = nutritionVisibleFor.test(portion.getHouseholdMemberId());
                    return new MealPortionView(
                            portion.getId(),
                            portion.getHouseholdMemberId(),
                            portion.getPortionFraction(),
                            visible ? total.scaledTo(portion.getPortionFraction(), OBSERVATION_SCALE) : null,
                            portion.isInMemberLog(),
                            !visible);
                })
                .toList();

        return new MealView(
                meal.getId(),
                meal.getHouseholdId(),
                meal.getMealType(),
                meal.getName(),
                meal.getConsumedAt(),
                total,
                items.stream().map(MealItemView::from).toList(),
                portionViews,
                meal.getCreatedByUserId());
    }

    private static List<MealPortion> byMemberId(List<MealPortion> portions) {
        return portions.stream()
                .sorted(Comparator.comparing(MealPortion::getHouseholdMemberId))
                .toList();
    }
}

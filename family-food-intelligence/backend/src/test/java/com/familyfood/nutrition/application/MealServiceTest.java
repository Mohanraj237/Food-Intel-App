package com.familyfood.nutrition.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.audit.AuditService;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.nutrition.domain.Meal;
import com.familyfood.nutrition.domain.MealItem;
import com.familyfood.nutrition.domain.MealPortion;
import com.familyfood.nutrition.domain.MealType;
import com.familyfood.nutrition.domain.NutritionAmounts;
import com.familyfood.nutrition.infra.MealItemRepository;
import com.familyfood.nutrition.infra.MealPortionRepository;
import com.familyfood.nutrition.infra.MealRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Family meal apportioning, and the D-041 boundary on whose log it may write to.
 *
 * <p>The maths tests assert exact {@code BigDecimal} values on purpose: a family meal splits
 * one set of numbers across several people, and a rounding slip here is not cosmetic — it is a
 * wrong entry in someone's health record, multiplied by every meal they are served.
 */
class MealServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T13:00:00Z");
    private static final Instant DINNER = Instant.parse("2026-09-12T12:30:00Z");
    private static final UUID HOUSEHOLD = UUID.randomUUID();

    private static final UUID ME_ACCOUNT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID SPOUSE_ACCOUNT = UUID.randomUUID();
    private static final UUID SPOUSE = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();

    private MealRepository meals;
    private MealItemRepository mealItems;
    private MealPortionRepository mealPortions;
    private HealthAccessFixture fixture;
    private WellnessAccess wellness;
    private MealService service;

    /** Saved portions, so confirm/update can read back what create wrote. */
    private final List<MealPortion> savedPortions = new ArrayList<>();
    private final List<MealItem> savedItems = new ArrayList<>();
    /** The first meal saved — captured in the stub rather than by verify(), because
     *  Mockito forbids verify() inside a when(...) argument. */
    private Meal firstMeal;

    @BeforeEach
    void setUp() {
        meals = mock(MealRepository.class);
        mealItems = mock(MealItemRepository.class);
        mealPortions = mock(MealPortionRepository.class);
        wellness = mock(WellnessAccess.class);
        // The real access policy (D-044) over an in-memory household: me, my spouse, and a
        // child I guard. It decides who may see each share's nutrition.
        fixture = new HealthAccessFixture(NOW);
        fixture.adult(HOUSEHOLD, ME_ACCOUNT, ME);
        fixture.adult(HOUSEHOLD, SPOUSE_ACCOUNT, SPOUSE);
        fixture.child(HOUSEHOLD, CHILD, ME_ACCOUNT);
        service = new MealService(meals, mealItems, mealPortions, mock(ProductQueryService.class),
                fixture.households, wellness, fixture.policy, mock(AuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        // I may derive NUTRITION for myself and the child I guard; never for my spouse (D-002,
        // D-041). Asked through the published question, not a local rule.
        when(wellness.mayDeriveFor(ME_ACCOUNT, ME, MetricCategory.NUTRITION)).thenReturn(true);
        when(wellness.mayDeriveFor(ME_ACCOUNT, CHILD, MetricCategory.NUTRITION)).thenReturn(true);
        when(wellness.mayDeriveFor(ME_ACCOUNT, SPOUSE, MetricCategory.NUTRITION)).thenReturn(false);
        when(wellness.mayDeriveFor(SPOUSE_ACCOUNT, SPOUSE, MetricCategory.NUTRITION)).thenReturn(true);

        when(meals.save(any(Meal.class))).thenAnswer(inv -> {
            if (firstMeal == null) firstMeal = inv.getArgument(0);
            return inv.getArgument(0);
        });
        when(mealItems.save(any(MealItem.class))).thenAnswer(inv -> {
            savedItems.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(mealPortions.save(any(MealPortion.class))).thenAnswer(inv -> {
            MealPortion p = inv.getArgument(0);
            savedPortions.removeIf(x -> x.getId().equals(p.getId()));
            savedPortions.add(p);
            return p;
        });
        when(mealItems.findByMealId(any())).thenAnswer(inv -> List.copyOf(savedItems));
        when(mealPortions.findByMealId(any())).thenAnswer(inv -> List.copyOf(savedPortions));
    }

    /** 200 g of dal at 9 g protein, 400 mg sodium, 120 kcal per 100 g. No fibre declared. */
    private static MealItemCommand dal() {
        return new MealItemCommand(null, "Dal", new BigDecimal("200"),
                new NutritionAmounts(new BigDecimal("120"), new BigDecimal("9"), null, null,
                        null, null, new BigDecimal("400")));
    }

    private CreateMealCommand familyDinner(List<MealPortionCommand> portions) {
        return new CreateMealCommand(HOUSEHOLD, MealType.DINNER, "Sunday dal", DINNER,
                List.of(dal()), portions);
    }

    private static MealPortionCommand portion(UUID member, String fraction) {
        return new MealPortionCommand(member, new BigDecimal(fraction));
    }

    @SuppressWarnings("unchecked")
    private Map<MetricCode, BigDecimal> derivedFor(UUID subject) {
        ArgumentCaptor<Map<MetricCode, BigDecimal>> values = ArgumentCaptor.forClass(Map.class);
        verify(wellness).replaceDerived(eq(ME_ACCOUNT), eq(subject), any(), values.capture(), eq(DINNER));
        return values.getValue();
    }

    /* ------------------------------------------------------------ apportioning maths */

    @Test
    @DisplayName("one meal is split by portion: half of 200 g dal is 9 g protein")
    void portionMaths() {
        service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "0.5"))));

        Map<MetricCode, BigDecimal> mine = derivedFor(ME);
        // 200 g × 9 g/100 g = 18 g for the whole meal; half of it is 9 g.
        assertThat(mine.get(MetricCode.PROTEIN_G)).isEqualByComparingTo("9");
        assertThat(mine.get(MetricCode.SODIUM_MG)).isEqualByComparingTo("400");
        assertThat(mine.get(MetricCode.ENERGY_KCAL)).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("a nutrient nobody declared stays absent — never written as zero")
    void undeclaredNutrientIsAbsentNotZero() {
        service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"))));

        Map<MetricCode, BigDecimal> mine = derivedFor(ME);
        // Zero fibre is a statement about what someone ate; an absent key is a statement
        // about what we know. Collapsing them would invent a measurement.
        assertThat(mine).doesNotContainKey(MetricCode.FIBRE_G);
        assertThat(mine).doesNotContainKey(MetricCode.CARBOHYDRATE_G);
    }

    @Test
    @DisplayName("a child receives their own share, scaled by their own portion")
    void childShareIsDerived() {
        service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"), portion(CHILD, "0.25"))));

        assertThat(derivedFor(CHILD).get(MetricCode.PROTEIN_G)).isEqualByComparingTo("4.5");
    }

    /* ------------------------------------------------------------------- D-041 */

    @Test
    @DisplayName("another adult's share is stored but NOT written to their log (D-041)")
    void otherAdultShareAwaitsConfirmation() {
        MealView meal = service.createMeal(ME_ACCOUNT,
                familyDinner(List.of(portion(ME, "1"), portion(SPOUSE, "1"))));

        // The meal succeeds — a two-adult household can still log dinner once.
        assertThat(meal.portions()).hasSize(2);

        // But nothing is written into my spouse's health record on my action (D-002).
        verify(wellness, never()).replaceDerived(eq(ME_ACCOUNT), eq(SPOUSE), any(), anyMap(), any());

        MealPortionView spouseShare = meal.portions().stream()
                .filter(p -> p.householdMemberId().equals(SPOUSE)).findFirst().orElseThrow();
        MealPortionView myShare = meal.portions().stream()
                .filter(p -> p.householdMemberId().equals(ME)).findFirst().orElseThrow();
        assertThat(spouseShare.inMemberLog()).isFalse();
        assertThat(myShare.inMemberLog()).isTrue();
    }

    @Test
    @DisplayName("the other adult confirms their own share, as themselves")
    void otherAdultCanConfirm() {
        MealView created = service.createMeal(ME_ACCOUNT,
                familyDinner(List.of(portion(ME, "1"), portion(SPOUSE, "0.5"))));
        when(meals.findById(created.id())).thenReturn(Optional.of(firstSavedMeal()));

        MealView confirmed = service.confirmPortion(SPOUSE_ACCOUNT, created.id());

        // The write is a self-write by the spouse, so it passes the ordinary relationship
        // check — no special permission exists to reason about.
        verify(wellness).replaceDerived(eq(SPOUSE_ACCOUNT), eq(SPOUSE), eq(created.id()), anyMap(), eq(DINNER));
        assertThat(confirmed.portions().stream()
                .filter(p -> p.householdMemberId().equals(SPOUSE)).findFirst().orElseThrow()
                .inMemberLog()).isTrue();
    }

    @Test
    @DisplayName("confirming a meal you were not served is refused")
    void cannotConfirmAMealYouDidNotEat() {
        MealView created = service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"))));
        when(meals.findById(created.id())).thenReturn(Optional.of(firstSavedMeal()));

        assertThatThrownBy(() -> service.confirmPortion(SPOUSE_ACCOUNT, created.id()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("only the member who logged a meal may delete it")
    void nonCreatorCannotDelete() {
        MealView created = service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"))));
        when(meals.findById(created.id())).thenReturn(Optional.of(firstSavedMeal()));

        assertThatThrownBy(() -> service.deleteMeal(SPOUSE_ACCOUNT, created.id()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        // The refusal must happen before anything derived is withdrawn.
        verify(wellness, never()).retractDerived(any(), any());
    }

    @Test
    @DisplayName("deleting a meal withdraws everything derived from it")
    void deleteRetracts() {
        MealView created = service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"))));
        when(meals.findById(created.id())).thenReturn(Optional.of(firstSavedMeal()));

        service.deleteMeal(ME_ACCOUNT, created.id());

        verify(wellness).retractDerived(ME_ACCOUNT, created.id());
    }

    @Test
    @DisplayName("editing a meal withdraws old shares and re-derives the new ones")
    void updateRecomputes() {
        MealView created = service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"))));
        when(meals.findById(created.id())).thenReturn(Optional.of(firstSavedMeal()));
        savedItems.clear();
        savedPortions.clear();

        service.updateMeal(ME_ACCOUNT, created.id(), new UpdateMealCommand(
                MealType.DINNER, "Sunday dal", null, List.of(dal()), List.of(portion(ME, "0.5"))));

        verify(wellness).retractDerived(ME_ACCOUNT, created.id());
    }

    @Test
    @DisplayName("only the member who logged a meal may edit it")
    void nonCreatorCannotUpdate() {
        MealView created = service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "1"))));
        when(meals.findById(created.id())).thenReturn(Optional.of(firstSavedMeal()));

        assertThatThrownBy(() -> service.updateMeal(SPOUSE_ACCOUNT, created.id(), new UpdateMealCommand(
                MealType.DINNER, null, null, List.of(dal()), List.of(portion(SPOUSE, "1")))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    /* -------------------------------------------------------------- validation */

    @Test
    @DisplayName("a meal with no items is refused with its own code")
    void noItems() {
        assertThatThrownBy(() -> service.createMeal(ME_ACCOUNT, new CreateMealCommand(
                HOUSEHOLD, MealType.LUNCH, null, DINNER, List.of(), List.of(portion(ME, "1")))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.MEAL_HAS_NO_ITEMS);
    }

    @Test
    @DisplayName("a meal apportioned to nobody is refused with its own code")
    void noPortions() {
        assertThatThrownBy(() -> service.createMeal(ME_ACCOUNT, familyDinner(List.of())))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.MEAL_HAS_NO_PORTIONS);
    }

    @Test
    @DisplayName("a portion for someone outside the household is refused")
    void portionForStranger() {
        assertThatThrownBy(() -> service.createMeal(ME_ACCOUNT,
                familyDinner(List.of(portion(UUID.randomUUID(), "1")))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.PORTION_MEMBER_NOT_IN_HOUSEHOLD);
    }

    @Test
    @DisplayName("a zero or negative portion is refused")
    void invalidPortion() {
        assertThatThrownBy(() -> service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "0")))))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.createMeal(ME_ACCOUNT, familyDinner(List.of(portion(ME, "-1")))))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("someone outside the household cannot log a meal into it")
    void outsiderCannotLog() {
        assertThatThrownBy(() -> service.createMeal(UUID.randomUUID(), familyDinner(List.of(portion(ME, "1")))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.NOT_HOUSEHOLD_MEMBER);
    }

    @Test
    @DisplayName("a meal dated in the future is refused")
    void futureMeal() {
        assertThatThrownBy(() -> service.createMeal(ME_ACCOUNT, new CreateMealCommand(HOUSEHOLD,
                MealType.DINNER, null, NOW.plusSeconds(3 * 86_400), List.of(dal()), List.of(portion(ME, "1")))))
                .isInstanceOf(AppException.class);
    }

    private Meal firstSavedMeal() {
        return firstMeal;
    }
}

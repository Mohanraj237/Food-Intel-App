package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.CategoryStandardMap;
import com.familyfood.scoring.domain.FoodType;
import com.familyfood.scoring.infra.CategoryStandardMapRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Food-type resolution and derived-fact wiring in {@link AssessmentOrchestrator} (D-059). */
class AssessmentOrchestratorTest {

    private final CategoryStandardMapRepository maps = mock(CategoryStandardMapRepository.class);
    private final ProductScoreService scores = mock(ProductScoreService.class);
    private final AssessmentOrchestrator orchestrator = new AssessmentOrchestrator(maps, scores);

    @Test
    @DisplayName("no mapping and per-100 g facts → SOLID; per-100 mL → LIQUID")
    void basisDecidesWhenUnmapped() {
        when(maps.findByCategoryCode(any())).thenReturn(List.of());

        assertThat(orchestrator.resolve(input("BISCUITS", "PER_100G")).foodType()).isEqualTo("SOLID");
        assertThat(orchestrator.resolve(input("BEVERAGES", "PER_100ML")).foodType()).isEqualTo("LIQUID");
        assertThat(orchestrator.resolve(input(null, null)).foodType()).isEqualTo("SOLID");
    }

    @Test
    @DisplayName("a mapped LIQUID category stays LIQUID; a mapped SOLID category declared per 100 mL becomes LIQUID")
    void mappingAndBasisCombine() {
        CategoryStandardMap liquid = mapped(FoodType.LIQUID);
        CategoryStandardMap solid = mapped(FoodType.SOLID);
        when(maps.findByCategoryCode("BEVERAGES")).thenReturn(List.of(liquid));
        when(maps.findByCategoryCode("DAIRY")).thenReturn(List.of(solid));

        assertThat(orchestrator.resolve(input("BEVERAGES", "PER_100G")).foodType()).isEqualTo("LIQUID");
        assertThat(orchestrator.resolve(input("DAIRY", "PER_100G")).foodType()).isEqualTo("SOLID");
        assertThat(orchestrator.resolve(input("DAIRY", "PER_100ML")).foodType()).isEqualTo("LIQUID");
    }

    @Test
    @DisplayName("FAT_OIL is never overridden by the declared basis, and an already-resolved input is left alone")
    void fatOilSticks() {
        CategoryStandardMap fatOil = mapped(FoodType.FAT_OIL);
        when(maps.findByCategoryCode("EDIBLE_OIL")).thenReturn(List.of(fatOil));

        assertThat(orchestrator.resolve(input("EDIBLE_OIL", "PER_100ML")).foodType()).isEqualTo("FAT_OIL");
        ProductAssessmentInput preset = input("EDIBLE_OIL", "PER_100G").withFoodType("LIQUID");
        assertThat(orchestrator.resolve(preset)).isSameAs(preset);
    }

    @Test
    @DisplayName("assess() adds INR_STARS only when scored and NOVA_GROUP only when classified")
    void derivedFactsFollowTheScore() {
        when(maps.findByCategoryCode(any())).thenReturn(List.of());
        ProductAssessmentInput scored = new ProductAssessmentInput(UUID.randomUUID(), "BISCUITS", List.of(), List.of(),
                "PER_100G", 4, "OPEN_FOOD_FACTS", null, Map.of());
        ProductHealthView health = new ProductHealthView(scored.productVersionId(), "v2", "SCORED", 22, "D",
                new BigDecimal("1.5"), "FSSAI_INR_DRAFT_2022_DERIVED", "SOLID", List.of(), null, Coverage.empty(),
                List.of(), Instant.EPOCH);
        when(scores.getOrCompute(any())).thenReturn(health);

        AssessmentContext context = orchestrator.assess(scored);

        assertThat(context.health()).isSameAs(health);
        assertThat(context.input().derivedFacts()).containsEntry("INR_STARS", new BigDecimal("1.5"))
                .containsEntry("NOVA_GROUP", BigDecimal.valueOf(4));

        ProductHealthView unscored = ProductHealthView.notYetScored(scored.productVersionId(), "v1", null, Instant.EPOCH);
        when(scores.getOrCompute(any())).thenReturn(unscored);
        AssessmentContext unscoredContext = orchestrator.assess(input("BISCUITS", "PER_100G"));

        assertThat(unscoredContext.input().derivedFacts()).isEmpty();
    }

    private static ProductAssessmentInput input(String category, String basis) {
        return new ProductAssessmentInput(UUID.randomUUID(), category, List.of(), List.of(), basis, null, null, null, Map.of());
    }

    private static CategoryStandardMap mapped(FoodType foodType) {
        CategoryStandardMap row = mock(CategoryStandardMap.class);
        when(row.getFoodType()).thenReturn(foodType);
        return row;
    }
}

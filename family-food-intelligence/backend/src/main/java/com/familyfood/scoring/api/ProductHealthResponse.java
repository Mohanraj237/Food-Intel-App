package com.familyfood.scoring.api;

import com.familyfood.scoring.application.Coverage;
import com.familyfood.scoring.application.ProcessingLevel;
import com.familyfood.scoring.application.ProductHealthView;
import com.familyfood.scoring.application.ScoreDriver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@link ProductHealthView} (frontend: {@code ProductHealth} in
 * {@code frontend/src/lib/scoring.ts}). Backward compatible with the pre-D-059 shape: the
 * original seven fields keep their names and meaning; {@code stars}, {@code ratingScheme},
 * {@code foodType}, {@code drivers}, {@code processingLevel} and {@code coverage} are
 * additions. {@code status="NOT_YET_SCORED"} (score/grade both {@code null}) remains today's
 * honest default while the ACTIVE rule_set has zero score_component rows (D-024).
 */
public record ProductHealthResponse(
        UUID productVersionId,
        String ruleSetVersion,
        String status,
        Integer score,
        String grade,
        BigDecimal stars,
        String ratingScheme,
        String foodType,
        List<ScoreDriverDto> drivers,
        ProcessingLevelDto processingLevel,
        CoverageDto coverage,
        List<ReasonDto> reasons,
        Instant computedAt) {

    public record ScoreDriverDto(String code, String direction, String nutrientCode, BigDecimal value,
                                 String unit, String basis, int points, int maxPoints, BigDecimal threshold,
                                 String standardRef, String explanationKey, boolean isEstimated, boolean assumed) {
        static ScoreDriverDto from(ScoreDriver d) {
            return new ScoreDriverDto(d.code(), d.direction(), d.nutrientCode(), d.value(), d.unit(), d.basis(),
                    d.points(), d.maxPoints(), d.threshold(), d.standardRef(), d.explanationKey(),
                    d.isEstimated(), d.assumed());
        }
    }

    public record ProcessingLevelDto(int novaGroup, boolean isEstimated, String source) {
        static ProcessingLevelDto from(ProcessingLevel p) {
            return p == null ? null : new ProcessingLevelDto(p.novaGroup(), p.isEstimated(), p.source());
        }
    }

    public record CoverageDto(List<String> required, List<String> present, List<String> missing) {
        static CoverageDto from(Coverage c) {
            Coverage safe = c == null ? Coverage.empty() : c;
            return new CoverageDto(safe.required(), safe.present(), safe.missing());
        }
    }

    static ProductHealthResponse from(ProductHealthView view) {
        return new ProductHealthResponse(
                view.productVersionId(),
                view.ruleSetVersion(),
                view.status(),
                view.score(),
                view.grade(),
                view.stars(),
                view.ratingScheme(),
                view.foodType(),
                (view.drivers() == null ? List.<ScoreDriver>of() : view.drivers()).stream().map(ScoreDriverDto::from).toList(),
                ProcessingLevelDto.from(view.processingLevel()),
                CoverageDto.from(view.coverage()),
                view.reasons().stream().map(ReasonDto::from).toList(),
                view.computedAt());
    }
}

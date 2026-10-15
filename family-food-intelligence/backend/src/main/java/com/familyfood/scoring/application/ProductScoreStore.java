package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.ProductScore;
import com.familyfood.scoring.domain.RescoreRun;
import com.familyfood.scoring.infra.ProductScoreRepository;
import com.familyfood.scoring.infra.RescoreRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional edge of {@link ProductScoreService}: reads and writes {@code product_score}
 * and {@code rescore_run} in their own transactions so a score can be persisted from inside
 * a caller's read-only transaction (alternatives, compare) or an after-commit event listener.
 * Separate from the service so the {@code REQUIRES_NEW} boundary is a real proxy call, not a
 * self-invocation Spring would silently ignore.
 */
@Service
public class ProductScoreStore {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ProductScoreRepository productScores;
    private final RescoreRunRepository rescoreRuns;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProductScoreStore(ProductScoreRepository productScores, RescoreRunRepository rescoreRuns,
                             ObjectMapper objectMapper, Clock clock) {
        this.productScores = productScores;
        this.rescoreRuns = rescoreRuns;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ProductScore> find(UUID productVersionId, UUID ruleSetId) {
        return productScores.findByProductVersionIdAndRuleSetId(productVersionId, ruleSetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(ProductHealthView view, UUID ruleSetId, String inputHash) {
        Map<String, Object> components = objectMapper.convertValue(view, MAP);
        Integer processingLevel = view.processingLevel() == null ? null : view.processingLevel().novaGroup();
        Optional<ProductScore> existing = productScores.findByProductVersionIdAndRuleSetId(view.productVersionId(), ruleSetId);
        if (existing.isPresent()) {
            existing.get().replace(view.score(), view.grade(), view.stars(), view.status(), inputHash,
                    processingLevel, components, view.computedAt());
            productScores.save(existing.get());
        } else {
            productScores.save(ProductScore.create(view.productVersionId(), ruleSetId, view.score(), view.grade(),
                    view.stars(), view.status(), inputHash, processingLevel, components, view.computedAt()));
        }
    }

    /** Rebuilds the view a stored row was computed from. */
    public ProductHealthView toView(ProductScore stored) {
        return objectMapper.convertValue(stored.getComponents(), ProductHealthView.class);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long countStored(UUID ruleSetId) {
        return productScores.countByRuleSetId(ruleSetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startRun(UUID ruleSetId, int total, String triggeredBy) {
        RescoreRun run = RescoreRun.start(ruleSetId, total, triggeredBy, clock.instant());
        rescoreRuns.save(run);
        return run.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progressRun(UUID runId, int done, int failed) {
        rescoreRuns.findById(runId).ifPresent(run -> {
            run.progress(done, failed);
            rescoreRuns.save(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRun(UUID runId, int done, int failed, boolean success) {
        rescoreRuns.findById(runId).ifPresent(run -> {
            run.progress(done, failed);
            run.finish(success, clock.instant());
            rescoreRuns.save(run);
        });
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<RescoreRun> findRun(UUID runId) {
        return rescoreRuns.findById(runId);
    }
}

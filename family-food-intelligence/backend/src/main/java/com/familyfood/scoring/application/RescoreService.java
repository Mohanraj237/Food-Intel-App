package com.familyfood.scoring.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.platform.error.AppException;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.infra.RuleSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bulk recomputation of {@code product_score} for every visible ACTIVE product against one
 * rule set (D-059) — run after a rule set is activated by migration, and by
 * {@link RescoreOnStartupRunner} when the ACTIVE set has components but no scores yet.
 * Pages through product ids via catalog's published {@link ProductQueryService}, scores each
 * in its own transaction ({@link ProductScoreStore}), and records progress on a
 * {@code rescore_run} row. One run at a time; a second request while one is in flight is
 * refused, not queued.
 */
@Service
public class RescoreService {

    private static final Logger log = LoggerFactory.getLogger(RescoreService.class);
    static final int PAGE_SIZE = 200;

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final ProductScoreService productScoreService;
    private final ProductScoreStore store;
    private final RuleSetRepository ruleSets;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RescoreService(ProductQueryService productQueryService,
                          AssessmentOrchestrator orchestrator,
                          ProductScoreService productScoreService,
                          ProductScoreStore store,
                          RuleSetRepository ruleSets) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.productScoreService = productScoreService;
        this.store = store;
        this.ruleSets = ruleSets;
    }

    /**
     * Starts a run on a virtual thread and returns its id, or empty if a run is already in
     * flight. Blocking I/O only (D-057).
     */
    public Optional<UUID> rescoreAll(String ruleSetVersion, String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            return Optional.empty();
        }
        try {
            RuleSet ruleSet = requireRuleSet(ruleSetVersion);
            UUID runId = store.startRun(ruleSet.getId(), productQueryService.countActiveProducts(), triggeredBy);
            Thread.ofVirtual().name("rescore-" + ruleSetVersion).start(() -> {
                try {
                    run(ruleSet, runId);
                } finally {
                    running.set(false);
                }
            });
            return Optional.of(runId);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    /** Same as {@link #rescoreAll} but on the calling thread — for tests and one-off jobs. */
    public UUID rescoreAllBlocking(String ruleSetVersion, String triggeredBy) {
        RuleSet ruleSet = requireRuleSet(ruleSetVersion);
        UUID runId = store.startRun(ruleSet.getId(), productQueryService.countActiveProducts(), triggeredBy);
        run(ruleSet, runId);
        return runId;
    }

    private void run(RuleSet ruleSet, UUID runId) {
        int done = 0;
        int failed = 0;
        boolean success = true;
        try {
            int page = 0;
            List<UUID> ids;
            while (!(ids = productQueryService.listActiveProductIds(page, PAGE_SIZE)).isEmpty()) {
                for (UUID productId : ids) {
                    try {
                        var product = productQueryService.getProductUncached(productId);
                        var input = orchestrator.resolve(AssessmentInputMapper.from(product));
                        productScoreService.computeAndStore(input, ruleSet);
                    } catch (AppException hidden) {
                        // No visible current version — nothing to score, not a failure.
                    } catch (RuntimeException e) {
                        failed++;
                        log.warn("rescore of product {} failed: {}", productId, e.toString());
                    }
                    done++;
                }
                store.progressRun(runId, done, failed);
                page++;
            }
        } catch (RuntimeException e) {
            success = false;
            log.error("rescore run {} aborted after {} products", runId, done, e);
        }
        store.finishRun(runId, done, failed, success);
        log.info("rescore run {} {}: {} products, {} failed (rule set {})", runId,
                success ? "completed" : "FAILED", done, failed, ruleSet.getVersion());
    }

    private RuleSet requireRuleSet(String version) {
        return ruleSets.findByVersion(version)
                .orElseThrow(() -> new IllegalArgumentException("no rule_set with version " + version));
    }
}

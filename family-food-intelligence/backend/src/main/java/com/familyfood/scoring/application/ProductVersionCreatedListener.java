package com.familyfood.scoring.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.ProductVersionCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps {@code product_score} current: when catalog commits a new product version (import,
 * contribution approval) or fills enrichment facts into one, the ACTIVE rule set's score is
 * recomputed for it (D-059). Runs after the catalog transaction commits, reads the product
 * fresh (never through the detail cache, which may not have been evicted yet), and never
 * propagates a failure back into the catalog write — the score computes lazily on the next
 * read if this misses.
 */
@Component
public class ProductVersionCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(ProductVersionCreatedListener.class);

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final ProductScoreService productScoreService;

    public ProductVersionCreatedListener(ProductQueryService productQueryService,
                                         AssessmentOrchestrator orchestrator,
                                         ProductScoreService productScoreService) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.productScoreService = productScoreService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductVersionCreated(ProductVersionCreated event) {
        try {
            var product = productQueryService.getProductUncached(event.productId());
            var input = orchestrator.resolve(AssessmentInputMapper.from(product));
            productScoreService.computeAndStore(input, productScoreService.activeRuleSet());
        } catch (RuntimeException e) {
            log.warn("score recompute after {} for product {} skipped: {}", event.reason(), event.productId(), e.toString());
        }
    }
}

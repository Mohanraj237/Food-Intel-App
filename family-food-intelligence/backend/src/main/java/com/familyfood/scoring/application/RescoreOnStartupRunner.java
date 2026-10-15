package com.familyfood.scoring.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * After a migration activates a rule set with real components (V18 and successors), the
 * first start finds an ACTIVE set with zero {@code product_score} rows and kicks off one
 * background rescore so search and lists have scores without waiting for each product to be
 * viewed. Does nothing while the ACTIVE set has no components (today's {@code v1}), and
 * nothing once any score exists. Disabled in the test profile
 * ({@code familyfood.scoring.rescore-on-startup=false}).
 */
@Component
public class RescoreOnStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RescoreOnStartupRunner.class);

    private final ProductScoreService productScoreService;
    private final RescoreService rescoreService;
    private final boolean enabled;

    public RescoreOnStartupRunner(ProductScoreService productScoreService,
                                  RescoreService rescoreService,
                                  @Value("${familyfood.scoring.rescore-on-startup:true}") boolean enabled) {
        this.productScoreService = productScoreService;
        this.rescoreService = rescoreService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            if (!productScoreService.activeSetNeedsInitialRescore()) {
                return;
            }
            String version = productScoreService.activeRuleSet().getVersion();
            rescoreService.rescoreAll(version, "startup")
                    .ifPresentOrElse(
                            runId -> log.info("ACTIVE rule set {} has no product scores yet; started rescore run {}", version, runId),
                            () -> log.info("a rescore run is already in flight; not starting another"));
        } catch (RuntimeException e) {
            // Never fail startup over a background job — the scores compute lazily on demand.
            log.warn("startup rescore check failed: {}", e.toString());
        }
    }
}

package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.ProductScore;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.infra.ScoreComponentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted product scores (D-059): {@code product_score} holds the ACTIVE rule set's result
 * per product version so search, compare, lists and the product page read one row instead of
 * recomputing. A derived product fact (database-design.md §1.4), never a cache of anything
 * about a person (D-056).
 *
 * <p>{@link #getOrCompute} serves the stored row when its {@code input_hash} matches the
 * current facts, and recomputes + upserts otherwise. Only {@code SCORED} and
 * {@code INSUFFICIENT_DATA} results are stored; {@code NOT_YET_SCORED} (no applicable
 * components — the ACTIVE {@code v1} case) is returned without touching the table.
 * A concurrent insert of the same row (two first readers at once) is resolved by re-reading.
 */
@Service
public class ProductScoreService {

    private static final Logger log = LoggerFactory.getLogger(ProductScoreService.class);

    private final ProductHealthScoreServiceImpl scorer;
    private final ProductScoreStore store;
    private final ScoreComponentRepository scoreComponents;

    public ProductScoreService(ProductHealthScoreServiceImpl scorer, ProductScoreStore store,
                               ScoreComponentRepository scoreComponents) {
        this.scorer = scorer;
        this.store = store;
        this.scoreComponents = scoreComponents;
    }

    /** The ACTIVE rule set's score for this input — stored if fresh, otherwise recomputed and stored. */
    public ProductHealthView getOrCompute(ProductAssessmentInput input) {
        return getOrCompute(input, scorer.activeRuleSet());
    }

    public ProductHealthView getOrCompute(ProductAssessmentInput input, RuleSet ruleSet) {
        String hash = inputHash(input);
        Optional<ProductScore> stored = store.find(input.productVersionId(), ruleSet.getId());
        if (stored.isPresent() && hash.equals(stored.get().getInputHash())) {
            return store.toView(stored.get());
        }
        return computeAndStore(input, ruleSet, hash);
    }

    /** Always recomputes; stores the result when there is one worth storing. */
    public ProductHealthView computeAndStore(ProductAssessmentInput input, RuleSet ruleSet) {
        return computeAndStore(input, ruleSet, inputHash(input));
    }

    private ProductHealthView computeAndStore(ProductAssessmentInput input, RuleSet ruleSet, String hash) {
        ProductHealthView view = scorer.score(input, ruleSet);
        if (ProductHealthView.STATUS_NOT_YET_SCORED.equals(view.status())) {
            return view;
        }
        try {
            store.upsert(view, ruleSet.getId(), hash);
        } catch (DataIntegrityViolationException raced) {
            // Two first readers inserted at once; the other row is identical by construction.
            log.debug("product_score insert raced for version {}; re-reading", input.productVersionId());
            return store.find(input.productVersionId(), ruleSet.getId()).map(store::toView).orElse(view);
        }
        return view;
    }

    /** True when the ACTIVE set has components to score with but no product has been scored yet. */
    public boolean activeSetNeedsInitialRescore() {
        RuleSet active = scorer.activeRuleSet();
        boolean hasComponents = !scoreComponents.findByRuleSetId(active.getId()).isEmpty();
        return hasComponents && store.countStored(active.getId()) == 0;
    }

    public RuleSet activeRuleSet() {
        return scorer.activeRuleSet();
    }

    /**
     * SHA-256 over everything the score depends on: category, basis, food type, processing
     * level and every per-100 fact (code, value, estimated flag) in code order. Derived
     * facts and allergens are excluded — neither feeds the score.
     */
    static String inputHash(ProductAssessmentInput input) {
        StringBuilder canonical = new StringBuilder()
                .append("c=").append(input.categoryCode())
                .append("|b=").append(input.basisPer100())
                .append("|f=").append(input.foodType())
                .append("|n=").append(input.novaGroup());
        input.nutrients().stream()
                .sorted(Comparator.comparing(NutrientFact::nutrientCode))
                .forEach(fact -> canonical.append("|").append(fact.nutrientCode())
                        .append("=").append(plain(fact.amountPer100()))
                        .append(fact.estimated() ? "~" : ""));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}

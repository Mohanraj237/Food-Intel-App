package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Basis;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.RuleSetStatus;
import com.familyfood.scoring.domain.RuleVerdict;
import com.familyfood.scoring.domain.ScoringRule;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.scoring.infra.ScoringRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic rule matching and evaluation against {@code scoring_rule.definition} (D-009,
 * D-010, D-011). Every comparison is a plain {@code BigDecimal} comparison between a value
 * already present in {@code input} and a threshold already stored on the rule row — nothing
 * here can be influenced by a model, and nothing here invents a number.
 *
 * <p>A rule's {@code definition.field} names either a nutrient fact ({@code ADDED_SUGAR_G},
 * {@code SODIUM_MG}, …) or an engine-derived fact from {@code input.derivedFacts()}
 * ({@code INR_STARS}, {@code NOVA_GROUP} — added by {@link AssessmentOrchestrator}, D-059).
 * Nutrient facts win when both exist. A derived fact has no per-serving form: it is compared
 * on {@code PER_100G} and reports {@code MISSING_NUTRIENT_DATA} on {@code PER_SERVING},
 * exactly like an undeclared nutrient; when it fires, the reason code is
 * {@code DERIVED_FACT_THRESHOLD} so the UI can phrase it as a rating rather than an amount.
 *
 * <p>Today's real ACTIVE rule_set ("v1") has zero {@code scoring_rule} rows (D-024), so in
 * production {@link #evaluateGoal} always returns {@code INSUFFICIENT_DATA} with a single
 * {@code NO_ACTIVE_RULES} reason for every call — not a bug, the honest MVP default. The full
 * matching engine below is exercised in tests against the DRAFT {@code "v1-draft-examples"}
 * rule_set, whose illustrative rules never reach a real user.
 */
@Service
public class RuleEngineImpl implements RuleEngine {

    private static final Map<RuleVerdict, Integer> SEVERITY_RANK = Map.of(
            RuleVerdict.EVERYDAY, 0,
            RuleVerdict.OCCASIONAL, 1,
            RuleVerdict.LIMIT, 2,
            RuleVerdict.DOESNT_FIT, 3);

    private static final String VERDICT_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
    private static final String VERDICT_EVERYDAY = "EVERYDAY";

    private final RuleSetRepository ruleSets;
    private final ScoringRuleRepository scoringRules;
    private final Clock clock;

    public RuleEngineImpl(RuleSetRepository ruleSets, ScoringRuleRepository scoringRules, Clock clock) {
        this.ruleSets = ruleSets;
        this.scoringRules = scoringRules;
        this.clock = clock;
    }

    @Override
    public String activeRuleSetVersion() {
        return ruleSets.findByStatus(RuleSetStatus.ACTIVE)
                .map(RuleSet::getVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "no ACTIVE rule_set exists — exactly one is seeded by V5__scoring.sql "
                      + "and uq_rule_set_active guarantees at most one; this is a deployment "
                      + "invariant violation, not a normal empty state"));
    }

    @Override
    public RuleOutcome evaluateGoal(String ruleSetVersion, String goalCode,
                                    ProductAssessmentInput input, String ageBand) {
        List<ScoringRule> candidates = inScope(rulesOf(ruleSetVersion), goalCode, input.categoryCode(), ageBand,
                clock.instant());

        if (candidates.isEmpty()) {
            return new RuleOutcome(VERDICT_INSUFFICIENT_DATA,
                    List.of(new Reason(Reason.NO_ACTIVE_RULES, null, null, null, null, null)),
                    List.of());
        }

        Map<String, NutrientFact> factsByCode = factsOf(input);

        List<String> firedRuleIds = new ArrayList<>();
        Map<String, Reason> reasonsByKey = new LinkedHashMap<>();
        ScoringRule mostSevere = null;
        boolean anyComparisonMade = false;

        for (ScoringRule rule : candidates) {
            String field = fieldOf(rule);
            NutrientFact fact = factsByCode.get(field);
            boolean derived = fact != null && isDerived(input, field);

            for (Basis basisToCheck : basesOf(rule.getBasis())) {
                BigDecimal value = fact == null ? null : valueOn(fact, basisToCheck);
                if (value == null) {
                    reasonsByKey.putIfAbsent(
                            Reason.MISSING_NUTRIENT_DATA + "|" + field + "|" + basisToCheck,
                            new Reason(Reason.MISSING_NUTRIENT_DATA, field, basisToCheck.name(),
                                    null, null, rule.getStandardRef()));
                    continue; // missing data: this rule contributes nothing on this basis, not a crash
                }
                anyComparisonMade = true;
                BigDecimal threshold = thresholdOf(rule);
                if (matchesOperator(value, opOf(rule), threshold)) {
                    firedRuleIds.add(rule.getId().toString());
                    String code = derived ? Reason.DERIVED_FACT_THRESHOLD : Reason.NUTRIENT_EXCEEDS_THRESHOLD;
                    reasonsByKey.put(
                            code + "|" + field + "|" + basisToCheck + "|" + rule.getCode(),
                            new Reason(code, field, basisToCheck.name(), value, threshold, rule.getStandardRef()));
                    if (mostSevere == null
                            || SEVERITY_RANK.get(rule.getVerdict()) > SEVERITY_RANK.get(mostSevere.getVerdict())) {
                        mostSevere = rule;
                    }
                }
            }
        }

        // Candidates existed in scope, but not one of them could actually be compared against a
        // real value (every relevant nutrient was undeclared). Defaulting to EVERYDAY here would
        // be an affirmative "this is fine" claim about a nutrient we know nothing about — exactly
        // the fabrication-from-missing-data this product's constitution forbids (D-024's own
        // reasoning applied one level deeper than "no rules matched at all"). Only report a real
        // verdict once at least one comparison was actually made.
        if (!anyComparisonMade) {
            return new RuleOutcome(VERDICT_INSUFFICIENT_DATA, List.copyOf(reasonsByKey.values()), List.of());
        }

        String verdict = mostSevere != null ? mostSevere.getVerdict().name() : VERDICT_EVERYDAY;
        return new RuleOutcome(verdict, List.copyOf(reasonsByKey.values()), List.copyOf(firedRuleIds));
    }

    /**
     * The rules of a rule set. An ACTIVE set's rows never change while the process runs —
     * activation and retirement are migrations applied at startup, and nothing edits an ACTIVE
     * set's rules at runtime — so its rules are held in a plain map after the first read (a
     * batch of 50 products × several goals would otherwise re-read them hundreds of times).
     * This is reference data about the rule set, not about a person, and deliberately not a
     * Spring cache (D-056). DRAFT and RETIRED sets are always read fresh: tests and the
     * scoring console edit DRAFT sets.
     */
    private List<ScoringRule> rulesOf(String ruleSetVersion) {
        List<ScoringRule> held = activeRules.get(ruleSetVersion);
        if (held != null) {
            return held;
        }
        return ruleSets.findByVersion(ruleSetVersion)
                .map(ruleSet -> {
                    List<ScoringRule> rules = List.copyOf(scoringRules.findByRuleSetId(ruleSet.getId()));
                    if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
                        activeRules.put(ruleSetVersion, rules);
                    }
                    return rules;
                })
                .orElseGet(List::of);
    }

    private final java.util.concurrent.ConcurrentHashMap<String, List<ScoringRule>> activeRules =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Nutrient facts first; then each derived fact as a per-100-only fact, unless a nutrient
     * of the same code already exists (a nutrient is a declared label value, a derived fact is
     * the engine's own computation — the label wins). Package-private for unit tests.
     */
    static Map<String, NutrientFact> factsOf(ProductAssessmentInput input) {
        Map<String, NutrientFact> factsByCode = new LinkedHashMap<>();
        for (NutrientFact fact : input.nutrients()) {
            factsByCode.put(fact.nutrientCode(), fact);
        }
        input.derivedFacts().forEach((code, value) ->
                factsByCode.putIfAbsent(code, new NutrientFact(code, null, value, null, false)));
        return factsByCode;
    }

    private static boolean isDerived(ProductAssessmentInput input, String field) {
        return input.derivedFacts().containsKey(field)
                && input.nutrients().stream().noneMatch(n -> n.nutrientCode().equals(field));
    }

    private static List<ScoringRule> inScope(List<ScoringRule> rules, String goalCode, String categoryCode,
                                             String ageBand, Instant now) {
        List<ScoringRule> result = new ArrayList<>();
        for (ScoringRule rule : rules) {
            if (rule.getAppliesToGoal() != null && !rule.getAppliesToGoal().equals(goalCode)) {
                continue;
            }
            if (rule.getAppliesToCategory() != null && !rule.getAppliesToCategory().equals(categoryCode)) {
                continue;
            }
            if (rule.getAppliesToAgeBand() != null && !rule.getAppliesToAgeBand().equals(ageBand)) {
                continue;
            }
            if (rule.getActiveFrom().isAfter(now)) {
                continue;
            }
            if (rule.getActiveTo() != null && !rule.getActiveTo().isAfter(now)) {
                continue;
            }
            result.add(rule);
        }
        return result;
    }

    private static List<Basis> basesOf(Basis basis) {
        return switch (basis) {
            case PER_SERVING -> List.of(Basis.PER_SERVING);
            case PER_100G -> List.of(Basis.PER_100G);
            case BOTH -> List.of(Basis.PER_SERVING, Basis.PER_100G);
        };
    }

    private static BigDecimal valueOn(NutrientFact fact, Basis basis) {
        return basis == Basis.PER_SERVING ? fact.amountPerServing() : fact.amountPer100();
    }

    private static String fieldOf(ScoringRule rule) {
        return (String) rule.getDefinition().get("field");
    }

    private static String opOf(ScoringRule rule) {
        return (String) rule.getDefinition().get("op");
    }

    private static BigDecimal thresholdOf(ScoringRule rule) {
        return toBigDecimal(rule.getDefinition().get("value"));
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(raw.toString());
    }

    /** Package-private and static so it is unit-testable without a Spring context or a database. */
    static boolean matchesOperator(BigDecimal value, String op, BigDecimal threshold) {
        int cmp = value.compareTo(threshold);
        return switch (op) {
            case "GT" -> cmp > 0;
            case "GTE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LTE" -> cmp <= 0;
            case "EQ" -> cmp == 0;
            default -> throw new IllegalStateException("unknown scoring_rule operator: " + op);
        };
    }
}

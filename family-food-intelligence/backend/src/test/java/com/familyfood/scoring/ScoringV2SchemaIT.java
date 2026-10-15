package com.familyfood.scoring;

import com.familyfood.scoring.domain.RuleSetStatus;
import com.familyfood.scoring.domain.ScoringAlgorithm;
import com.familyfood.scoring.infra.ReferenceStandardRepository;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What V15/V16 leave behind on a fresh database (D-058, D-059): the new reference standard,
 * the updated FSSAI_INR row, the additive vocabulary, the search read model, and — still —
 * exactly one ACTIVE rule set with zero rule content. Nothing in these migrations activates
 * anything; that is V17/V18's job after the review is signed.
 */
class ScoringV2SchemaIT extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RuleSetRepository ruleSets;
    @Autowired private ReferenceStandardRepository referenceStandards;

    @Test
    @DisplayName("WHO_SEARO_NPM_2017 exists and FSSAI_INR now names the specific draft at evidence level B")
    void referenceStandards() {
        assertThat(referenceStandards.findById("WHO_SEARO_NPM_2017")).isPresent();
        Map<String, Object> inr = jdbc.queryForMap(
                "SELECT evidence_level, document_uri, published_on::text AS published_on, scope_note FROM reference_standard WHERE code = 'FSSAI_INR'");
        assertThat(inr.get("evidence_level")).isEqualTo("B");
        assertThat((String) inr.get("document_uri")).contains("comments.fssai.gov.in");
        assertThat(inr.get("published_on")).isEqualTo("2022-09-13");
        assertThat((String) inr.get("scope_note")).contains("DRAFT").contains("never as the regulator's own rating");
    }

    @Test
    @DisplayName("still exactly one ACTIVE rule set, v1, on the v1 algorithm, with no rule content")
    void activeRuleSetUnchanged() {
        var active = ruleSets.findByStatus(RuleSetStatus.ACTIVE).orElseThrow();
        assertThat(active.getVersion()).isEqualTo("v1");
        assertThat(active.getAlgorithm()).isEqualTo(ScoringAlgorithm.WEIGHTED_MEAN_V1);
        assertThat(active.getAlgorithmConfig()).isEmpty();
        Integer components = jdbc.queryForObject(
                "SELECT count(*) FROM score_component WHERE rule_set_id = ?", Integer.class, active.getId());
        Integer rules = jdbc.queryForObject(
                "SELECT count(*) FROM scoring_rule WHERE rule_set_id = ?", Integer.class, active.getId());
        assertThat(components).isZero();
        assertThat(rules).isZero();
        Integer maps = jdbc.queryForObject("SELECT count(*) FROM category_standard_map", Integer.class);
        assertThat(maps).isZero();
    }

    @Test
    @DisplayName("the additive vocabulary and the new nutrient codes are seeded")
    void referenceRows() {
        Integer additives = jdbc.queryForObject("SELECT count(*) FROM additive", Integer.class);
        assertThat(additives).isGreaterThan(150);
        Map<String, Object> msg = jdbc.queryForMap("SELECT name, e_number, function_category FROM additive WHERE ins_number = '621'");
        assertThat(msg.get("name")).isEqualTo("Monosodium L-glutamate");
        assertThat(msg.get("e_number")).isEqualTo("E621");
        assertThat(msg.get("function_category")).isEqualTo("Flavour enhancer");
        List<String> codes = jdbc.queryForList("SELECT code FROM nutrient WHERE code IN ('ENERGY_KJ','FVNL_PCT') ORDER BY code", String.class);
        assertThat(codes).containsExactly("ENERGY_KJ", "FVNL_PCT");
        Integer aliases = jdbc.queryForObject("SELECT count(*) FROM category_alias", Integer.class);
        assertThat(aliases).isGreaterThan(80);
    }

    @Test
    @DisplayName("product_search_facts is queryable with the columns search v2 filters on")
    void searchFactsView() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT product_id, category_code, nova_group, diet_flag, total_sugar_100, sodium_100, allergen_codes, score, stars, score_status "
              + "FROM product_search_facts LIMIT 1");
        assertThat(rows).isNotNull(); // shape is what matters; a fresh database may hold no products
    }
}

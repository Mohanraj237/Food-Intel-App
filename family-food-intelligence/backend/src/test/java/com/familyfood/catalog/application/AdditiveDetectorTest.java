package com.familyfood.catalog.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Label-token additive marking (V15, D-059): what counts as an INS/E number and what must not. */
class AdditiveDetectorTest {

    @Test
    @DisplayName("INS and E prefixed forms are recognised with optional spacing, dashes and roman sub-numbers")
    void recognisesPrefixedForms() {
        assertThat(AdditiveDetector.codesIn("ACIDITY REGULATOR (INS 330)")).containsExactly("330");
        assertThat(AdditiveDetector.codesIn("Emulsifier of vegetable origin [INS 472e]")).containsExactly("472e");
        assertThat(AdditiveDetector.codesIn("RAISING AGENTS [INS 503(ii), 500(ii)]")).containsExactly("503(ii)", "500(ii)");
        assertThat(AdditiveDetector.codesIn("Preservative (E211)")).containsExactly("211");
        assertThat(AdditiveDetector.codesIn("Thickener E-1422")).containsExactly("1422");
        assertThat(AdditiveDetector.codesIn("Colour ins-150d")).containsExactly("150d");
        assertThat(AdditiveDetector.codesIn("FLOUR TREATMENT AGENTS [INS 1101 (II)]")).containsExactly("1101(ii)");
    }

    @Test
    @DisplayName("a bracketed list of bare numbers after a class name is an additive list")
    void recognisesBareBracketedLists() {
        assertThat(AdditiveDetector.codesIn("Emulsifiers (322, 471)")).containsExactly("322", "471");
        assertThat(AdditiveDetector.codesIn("Stabilizers [407, 412, 415]")).containsExactly("407", "412", "415");
        assertThat(AdditiveDetector.primaryCode("Flavour enhancers (621, 627, 631)")).contains("621");
    }

    @Test
    @DisplayName("bare numbers that are quantities, percentages or years are never additives")
    void ignoresNonAdditiveNumbers() {
        assertThat(AdditiveDetector.codesIn("Refined wheat flour (maida) 68%")).isEmpty();
        assertThat(AdditiveDetector.codesIn("Sugar")).isEmpty();
        assertThat(AdditiveDetector.codesIn("Milk solids (2% fat)")).isEmpty();
        assertThat(AdditiveDetector.codesIn("Cocoa solids (min 500 mg per 100 g)")).isEmpty();
        assertThat(AdditiveDetector.codesIn("Vitamin B12 (0.5 mcg)")).isEmpty();
        assertThat(AdditiveDetector.codesIn("Batch INS 999999")).isEmpty();   // not an INS number at all
        assertThat(AdditiveDetector.codesIn(null)).isEmpty();
        assertThat(AdditiveDetector.codesIn("")).isEmpty();
    }

    @Test
    @DisplayName("codes are normalised to the reference table's spelling and de-duplicated")
    void normalisesCodes() {
        assertThat(AdditiveDetector.normalise("503 (II)")).isEqualTo("503(ii)");
        assertThat(AdditiveDetector.normalise("150D")).isEqualTo("150d");
        assertThat(AdditiveDetector.codesIn("INS 330, E330, ins330")).containsExactly("330");
    }
}

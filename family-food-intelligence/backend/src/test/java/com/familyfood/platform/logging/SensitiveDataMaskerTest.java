package com.familyfood.platform.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The masking layer is the enforcement point for AC-9.4 ("no health condition, allergen,
 * child datum, token, password or raw search string appears in any log").
 *
 * <p>These tests assert behaviour a reviewer can check by reading them: given a realistic
 * log payload, the sensitive value is gone from the output.
 */
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker("test-pepper");

    @ParameterizedTest
    @DisplayName("health and dietary fields are redacted wherever they appear")
    @ValueSource(strings = {
            "condition", "conditions", "allergen", "allergens", "goals",
            "dietaryPreference", "ageBand", "sex", "childProfile"
    })
    void healthFieldsAreRedacted(String field) {
        Object masked = masker.maskStructure(Map.of(field, "diabetes-type-2"));

        assertThat(masked.toString())
                .doesNotContain("diabetes-type-2")
                .contains(SensitiveData.REDACTED);
    }

    @ParameterizedTest
    @DisplayName("credentials and tokens are redacted")
    @ValueSource(strings = {"password", "accessToken", "refresh_token", "apiKey", "otp"})
    void credentialFieldsAreRedacted(String field) {
        Object masked = masker.maskStructure(Map.of(field, "s3cr3t-value-here"));

        assertThat(masked.toString()).doesNotContain("s3cr3t-value-here");
    }

    @Test
    @DisplayName("nested structures are masked to depth")
    void nestedStructuresAreMasked() {
        Map<String, Object> payload = Map.of(
                "household", Map.of(
                        "members", List.of(
                                Map.of("displayLabel", "Priya", "allergens", List.of("PEANUT")))));

        String masked = masker.maskStructure(payload).toString();

        assertThat(masked).doesNotContain("Priya");
        assertThat(masked).doesNotContain("PEANUT");
    }

    @Test
    @DisplayName("a raw search query never survives masking")
    void searchQueryIsRedacted() {
        // Search text is free text a user typed. It can contain anything, including a
        // condition they are quietly researching (D-018).
        Object masked = masker.maskStructure(Map.of("query", "sugar free biscuits diabetic"));

        assertThat(masked.toString()).doesNotContain("diabetic");
    }

    @Test
    @DisplayName("email addresses are removed from free text")
    void emailsAreStrippedFromFreeText() {
        String masked = masker.maskText(
                "Registration failed for priya.sharma@example.com after 3 attempts");

        assertThat(masked)
                .doesNotContain("priya.sharma@example.com")
                .contains("Registration failed");
    }

    @Test
    @DisplayName("bearer tokens are stripped from free text")
    void bearerTokensAreStripped() {
        String masked = masker.maskText(
                "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123");

        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    @DisplayName("key=value pairs in a concatenated message are masked")
    void keyValuePairsInFreeTextAreMasked() {
        // The realistic failure mode: someone concatenates a DTO into a log message.
        String masked = masker.maskText(
                "profile update accountId=123 allergens=PEANUT,MILK goals=REDUCE_ADDED_SUGAR");

        assertThat(masked)
                .doesNotContain("PEANUT")
                .doesNotContain("REDUCE_ADDED_SUGAR")
                .contains("accountId=123");   // non-sensitive fields survive
    }

    @Test
    @DisplayName("IP addresses are pseudonymised rather than dropped, so events still correlate")
    void ipAddressesArePseudonymised() {
        Object masked = masker.maskStructure(Map.of("ip", "203.0.113.42"));

        String result = masked.toString();
        assertThat(result).doesNotContain("203.0.113.42");
        assertThat(result).doesNotContain(SensitiveData.REDACTED);   // hashed, not redacted
    }

    @Test
    @DisplayName("pseudonymisation is stable for the same pepper and unstable across peppers")
    void pseudonymisationIsStableWithinAPepper() {
        String a = SensitiveData.pseudonymise("203.0.113.42", "pepper-one");
        String b = SensitiveData.pseudonymise("203.0.113.42", "pepper-one");
        String c = SensitiveData.pseudonymise("203.0.113.42", "pepper-two");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("field-name matching ignores case and separators")
    void fieldMatchingIsNormalised() {
        assertThat(SensitiveData.isDenied("ACCESS_TOKEN")).isTrue();
        assertThat(SensitiveData.isDenied("access-token")).isTrue();
        assertThat(SensitiveData.isDenied("accessToken")).isTrue();
        assertThat(SensitiveData.isDenied("productName")).isFalse();
    }

    @Test
    @DisplayName("masking never throws and never returns null for real input")
    void maskingIsTotal() {
        assertThat(masker.maskText("")).isEmpty();
        assertThat(masker.maskText(null)).isNull();
        assertThat(masker.maskStructure(null)).isNull();
        assertThat(masker.maskStructure(42)).isEqualTo(42);
    }
}

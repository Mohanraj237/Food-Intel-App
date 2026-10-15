package com.familyfood.search.application;

import com.familyfood.platform.error.AppException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Structured search filters (D-061). Every value is supplied by the caller — typed into the
 * filter sheet, or read out of their own words by {@link SearchQueryParser}. None of these is
 * a threshold this product sets: "under 10 g sugar" means the person asked for under 10 g.
 *
 * <p>All nutrient values are per 100 g (or per 100 ml for liquids), in the catalogue's
 * canonical units — grams, except sodium in milligrams and energy in kcal. A nutrient filter
 * excludes a product that does not declare that nutrient; the response reports how many
 * were excluded that way so the UI never implies they were checked and failed.
 */
public record SearchFilters(
        String category,
        BigDecimal maxAddedSugar,
        BigDecimal maxTotalSugar,
        BigDecimal maxSodium,
        BigDecimal maxSatFat,
        BigDecimal maxEnergy,
        BigDecimal minProtein,
        BigDecimal minFibre,
        List<String> excludeAllergens,
        String dietary,
        Integer maxNova) {

    public static final Set<String> ALLERGEN_CODES = Set.of(
            "MILK", "EGG", "FISH", "CRUSTACEAN", "TREE_NUT", "PEANUT", "WHEAT", "GLUTEN", "SOY",
            "SESAME", "SULPHITE", "MUSTARD");
    public static final Set<String> DIETS = Set.of("VEG", "VEGAN");

    public SearchFilters {
        excludeAllergens = excludeAllergens == null ? List.of() : List.copyOf(excludeAllergens);
    }

    public static SearchFilters none() {
        return new SearchFilters(null, null, null, null, null, null, null, null, List.of(), null, null);
    }

    /** Validates caller-supplied values: non-negative amounts, known codes, NOVA 1–4. */
    public SearchFilters validated() {
        requireNonNegative("maxAddedSugar", maxAddedSugar);
        requireNonNegative("maxTotalSugar", maxTotalSugar);
        requireNonNegative("maxSodium", maxSodium);
        requireNonNegative("maxSatFat", maxSatFat);
        requireNonNegative("maxEnergy", maxEnergy);
        requireNonNegative("minProtein", minProtein);
        requireNonNegative("minFibre", minFibre);
        for (String code : excludeAllergens) {
            if (!ALLERGEN_CODES.contains(code)) {
                throw AppException.validation("excludeAllergens", "unknown allergen " + code);
            }
        }
        if (dietary != null && !DIETS.contains(dietary)) {
            throw AppException.validation("dietary", "must be VEG or VEGAN");
        }
        if (maxNova != null && (maxNova < 1 || maxNova > 4)) {
            throw AppException.validation("maxNova", "must be 1-4");
        }
        return this;
    }

    /**
     * Explicit filters win; anything the caller left empty is taken from {@code parsed}.
     * Allergen exclusions are unioned — asking to hide peanut in the sheet and saying
     * "no milk" in the query hides both.
     */
    public SearchFilters overlay(SearchFilters parsed) {
        Set<String> allergens = new LinkedHashSet<>(parsed.excludeAllergens);
        allergens.addAll(excludeAllergens);
        return new SearchFilters(
                category != null ? category : parsed.category,
                maxAddedSugar != null ? maxAddedSugar : parsed.maxAddedSugar,
                maxTotalSugar != null ? maxTotalSugar : parsed.maxTotalSugar,
                maxSodium != null ? maxSodium : parsed.maxSodium,
                maxSatFat != null ? maxSatFat : parsed.maxSatFat,
                maxEnergy != null ? maxEnergy : parsed.maxEnergy,
                minProtein != null ? minProtein : parsed.minProtein,
                minFibre != null ? minFibre : parsed.minFibre,
                new ArrayList<>(allergens),
                dietary != null ? dietary : parsed.dietary,
                maxNova != null ? maxNova : parsed.maxNova);
    }

    public boolean hasAny() {
        return category != null || maxAddedSugar != null || maxTotalSugar != null || maxSodium != null
                || maxSatFat != null || maxEnergy != null || minProtein != null || minFibre != null
                || !excludeAllergens.isEmpty() || dietary != null || maxNova != null;
    }

    /** Parses the {@code excludeAllergens} query parameter (comma-separated codes). */
    public static List<String> parseAllergenCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String part : csv.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty() && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    private static void requireNonNegative(String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw AppException.validation(field, "must not be negative");
        }
    }
}

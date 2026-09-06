package com.familyfood.capture.application;

import com.familyfood.capture.application.SubmitContributionCommand.NutrientContribution;
import com.familyfood.capture.domain.ProductContribution;
import com.familyfood.capture.infra.ProductContributionRepository;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.platform.error.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intake for the manual-entry contribution queue (system-architecture.md §6). Every
 * submission lands {@code PENDING} and nothing here ever touches {@code catalog}'s tables —
 * this is the intake side only; ops review is a separate, later piece of work.
 *
 * <p>{@link #submit} re-validates everything itself rather than trusting the API layer's
 * bean-validation annotations, the same defense-in-depth already used by
 * {@code HouseholdService} (e.g. {@code parseRelationship}) and {@code HouseholdMember}'s
 * own factories: a service method that reaches the database is the last line of defense,
 * not the first.
 */
@Service
public class ContributionService {

    static final int MAX_PRODUCT_NAME = 200;
    static final int MAX_BRAND_NAME = 200;
    static final int MAX_CATEGORY_CODE = 40;
    static final int MAX_GTIN = 20;
    static final int MAX_PACK_SIZE_UNIT = 20;
    static final int MAX_SERVING_DECLARED_TEXT = 200;
    static final int MAX_INGREDIENTS_TEXT = 4000;
    static final int MAX_ALLERGEN_CODE = 30;
    static final int MAX_ALLERGEN_CODES = 50;
    static final int MAX_NUTRIENT_ENTRIES = 60;

    private final ProductContributionRepository contributions;
    private final ProductQueryService catalog;
    private final Clock clock;

    public ContributionService(ProductContributionRepository contributions,
                               ProductQueryService catalog,
                               Clock clock) {
        this.contributions = contributions;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public ContributionView submit(UUID callerAccountId, SubmitContributionCommand command) {
        String productName = requireProductName(command.productName());
        String categoryCode = validateCategoryCode(command.categoryCode());
        List<Map<String, Object>> nutrients = validateNutrients(command.nutrients());
        List<String> allergenCodes = validateAllergenCodes(command.allergenCodes());

        String gtin = validateLength("gtin", command.gtin(), MAX_GTIN);
        String brandName = validateLength("brandName", command.brandName(), MAX_BRAND_NAME);
        String packSizeUnit = validateLength("packSizeUnit", command.packSizeUnit(), MAX_PACK_SIZE_UNIT);
        String servingDeclaredText = validateLength(
                "servingDeclaredText", command.servingDeclaredText(), MAX_SERVING_DECLARED_TEXT);
        String ingredientsRawText = validateLength(
                "ingredientsRawText", command.ingredientsRawText(), MAX_INGREDIENTS_TEXT);

        if (command.packSizeValue() != null && command.packSizeValue().signum() <= 0) {
            throw AppException.validation("packSizeValue", "must be greater than zero");
        }

        Instant now = clock.instant();
        ProductContribution contribution = contributions.save(ProductContribution.submit(
                callerAccountId, gtin, brandName, productName, categoryCode,
                command.packSizeValue(), packSizeUnit, servingDeclaredText,
                nutrients, ingredientsRawText, allergenCodes, now));

        return new ContributionView(
                contribution.getId(), contribution.getStatus().name(), contribution.getSubmittedAt());
    }

    /** A submitter's own contributions, for data export (AC-9.2). Never another account's. */
    @Transactional(readOnly = true)
    public List<ContributionSummary> listMine(UUID accountId) {
        return contributions.findBySubmittedByUserId(accountId).stream()
                .map(c -> new ContributionSummary(
                        c.getId(), c.getProductName(), c.getStatus().name(), c.getSubmittedAt()))
                .toList();
    }

    public record ContributionSummary(UUID id, String productName, String status, Instant submittedAt) {}

    /**
     * Account erasure's treatment of this module (D-027, decision-log.md): a contribution's
     * content is retained, only the link back to the erased account is removed. Trusted-
     * caller, like {@code ProfileService.eraseAllVersions} — the orchestrator
     * ({@code datarights.application.DataRightsService}) already knows {@code accountId} is
     * the caller's own.
     */
    @Transactional
    public int anonymizeSubmitter(UUID accountId) {
        List<ProductContribution> own = contributions.findBySubmittedByUserId(accountId);
        own.forEach(contribution -> {
            contribution.anonymizeSubmitter();
            contributions.save(contribution);
        });
        return own.size();
    }

    // -------------------------------------------------------------------- field validation

    private String requireProductName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw AppException.validation("productName", "required");
        }
        if (trimmed.length() > MAX_PRODUCT_NAME) {
            throw AppException.validation("productName", "must be at most " + MAX_PRODUCT_NAME + " characters");
        }
        return trimmed;
    }

    /** {@code null}/blank is allowed — a contribution may not know the category yet. */
    private String validateCategoryCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        String trimmed = categoryCode.trim();
        if (trimmed.length() > MAX_CATEGORY_CODE) {
            throw AppException.validation("categoryCode", "must be at most " + MAX_CATEGORY_CODE + " characters");
        }
        boolean known = catalog.listCategories().stream()
                .anyMatch(category -> category.code().equals(trimmed));
        if (!known) {
            throw AppException.validation("categoryCode", "unknown category");
        }
        return trimmed;
    }

    private List<Map<String, Object>> validateNutrients(List<NutrientContribution> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        if (entries.size() > MAX_NUTRIENT_ENTRIES) {
            throw AppException.validation("nutrients", "too many entries");
        }

        List<Map<String, Object>> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            NutrientContribution entry = entries.get(i);
            String field = "nutrients[" + i + "]";

            if (entry.nutrientCode() == null || entry.nutrientCode().isBlank()) {
                throw AppException.validation(field + ".nutrientCode", "required");
            }
            if (entry.amountPer100() != null && entry.amountPer100().signum() < 0) {
                throw AppException.validation(field + ".amountPer100", "must not be negative");
            }
            if (entry.amountPerServing() != null && entry.amountPerServing().signum() < 0) {
                throw AppException.validation(field + ".amountPerServing", "must not be negative");
            }

            // Map.copyOf/Map.of reject null values, and amountPer100/amountPerServing are
            // each legitimately null (a household may only know one of the two) —
            // Collections.unmodifiableMap is the null-tolerant equivalent.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nutrientCode", entry.nutrientCode().trim());
            row.put("amountPer100", entry.amountPer100());
            row.put("amountPerServing", entry.amountPerServing());
            result.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(result);
    }

    private List<String> validateAllergenCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        if (codes.size() > MAX_ALLERGEN_CODES) {
            throw AppException.validation("allergenCodes", "too many entries");
        }
        List<String> result = new ArrayList<>(codes.size());
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw AppException.validation("allergenCodes", "must not contain a blank entry");
            }
            String trimmed = code.trim();
            if (trimmed.length() > MAX_ALLERGEN_CODE) {
                throw AppException.validation("allergenCodes", "entry must be at most " + MAX_ALLERGEN_CODE + " characters");
            }
            result.add(trimmed);
        }
        return List.copyOf(result);
    }

    /** Trims, treats a blank string as absent, and enforces {@code max} on what remains. */
    private String validateLength(String field, String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw AppException.validation(field, "must be at most " + max + " characters");
        }
        return trimmed;
    }
}

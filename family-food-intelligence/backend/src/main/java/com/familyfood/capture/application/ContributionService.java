package com.familyfood.capture.application;

import com.familyfood.capture.application.SubmitContributionCommand.NutrientContribution;
import com.familyfood.capture.domain.ContributionStatus;
import com.familyfood.capture.domain.ProductContribution;
import com.familyfood.capture.infra.ProductContributionRepository;
import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.NutrientRefDto;
import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.GtinType;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.identity.application.OperatorAccess;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ProductIngestionService ingestion;
    private final OperatorAccess operatorAccess;
    private final Clock clock;

    public ContributionService(ProductContributionRepository contributions,
                               ProductQueryService catalog,
                               ProductIngestionService ingestion,
                               OperatorAccess operatorAccess,
                               Clock clock) {
        this.contributions = contributions;
        this.catalog = catalog;
        this.ingestion = ingestion;
        this.operatorAccess = operatorAccess;
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

        // D-026: a required terms-of-contribution acknowledgment, not a consent-ledger
        // grant — see V9__contribution_acknowledgment.sql for the full reasoning.
        if (!command.catalogueContributionAcknowledged()) {
            throw AppException.validation("catalogueContributionAcknowledged", "required");
        }

        Instant now = clock.instant();
        ProductContribution contribution = contributions.save(ProductContribution.submit(
                callerAccountId, gtin, brandName, productName, categoryCode,
                command.packSizeValue(), packSizeUnit, servingDeclaredText,
                nutrients, ingredientsRawText, allergenCodes,
                command.catalogueContributionAcknowledged(), now));

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

    // -------------------------------------------------------------------------- ops review
    //
    // The smallest useful review console (docs/product/post-mvp-backlog.md's "correction
    // queue ops console" item): a queue, a detail view, approve/reject. Authorization is a
    // real server-side check against identity.application.OperatorAccess — never a
    // client-side assumption — checked on every call, not cached in a token (the same
    // "re-check at request time" precedent as household membership, D-023).

    @Transactional(readOnly = true)
    public List<ContributionQueueEntry> listQueue(UUID callerAccountId, ContributionStatus status) {
        requireOperator(callerAccountId);
        return contributions.findByStatusOrderBySubmittedAtAsc(status).stream()
                .map(c -> new ContributionQueueEntry(
                        c.getId(), c.getGtin(), c.getProductName(), c.getBrandName(),
                        c.getCategoryCode(), c.getStatus().name(), c.getSubmittedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContributionDetailView getDetail(UUID callerAccountId, UUID contributionId) {
        requireOperator(callerAccountId);
        ProductContribution c = findOrThrow(contributionId);
        return new ContributionDetailView(
                c.getId(), c.getGtin(), c.getBrandName(), c.getProductName(), c.getCategoryCode(),
                c.getPackSizeValue(), c.getPackSizeUnit(), c.getServingDeclaredText(),
                c.getNutrients(), c.getIngredientsRawText(), c.getAllergenCodes(),
                c.getStatus().name(), c.getSubmittedAt(), c.getReviewedAt(), c.getReviewedBy(),
                c.getReviewNotes());
    }

    /**
     * Approves a pending contribution: creates the real catalogue product/version through
     * {@link ProductIngestionService} (the same write path the Open Food Facts bulk seed
     * uses), then records the review outcome on the contribution row in the same
     * transaction — either both happen or neither does.
     */
    @Transactional
    public UUID approve(UUID callerAccountId, UUID contributionId) {
        requireOperator(callerAccountId);
        ProductContribution c = findOrThrow(contributionId);
        if (c.getStatus() != ContributionStatus.PENDING) {
            throw new AppException(ErrorCode.CONTRIBUTION_ALREADY_REVIEWED);
        }

        Instant now = clock.instant();
        UUID productId = ingestion.ingestNewProduct(toIngestCommand(c, callerAccountId, now));

        c.approve(callerAccountId.toString(), now);
        contributions.save(c);
        return productId;
    }

    /** Rejects a pending contribution. {@code reason} is required — never a silent no. */
    @Transactional
    public void reject(UUID callerAccountId, UUID contributionId, String reason) {
        requireOperator(callerAccountId);
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw AppException.validation("reason", "required");
        }
        ProductContribution c = findOrThrow(contributionId);
        if (c.getStatus() != ContributionStatus.PENDING) {
            throw new AppException(ErrorCode.CONTRIBUTION_ALREADY_REVIEWED);
        }
        c.reject(callerAccountId.toString(), trimmedReason, clock.instant());
        contributions.save(c);
    }

    public record ContributionQueueEntry(UUID id, String gtin, String productName, String brandName,
                                          String categoryCode, String status, Instant submittedAt) {
    }

    public record ContributionDetailView(UUID id, String gtin, String brandName, String productName,
                                          String categoryCode, BigDecimal packSizeValue, String packSizeUnit,
                                          String servingDeclaredText, List<Map<String, Object>> nutrients,
                                          String ingredientsRawText, List<String> allergenCodes,
                                          String status, Instant submittedAt, Instant reviewedAt,
                                          String reviewedBy, String reviewNotes) {
    }

    private void requireOperator(UUID callerAccountId) {
        if (!operatorAccess.isOperator(callerAccountId)) {
            throw new AppException(ErrorCode.NOT_AN_OPERATOR);
        }
    }

    private ProductContribution findOrThrow(UUID contributionId) {
        return contributions.findById(contributionId)
                .orElseThrow(() -> AppException.notFound("contribution"));
    }

    /**
     * Maps a contribution's loosely-typed submission (a raw jsonb nutrient map, no unit of
     * its own — D-015's per-field provenance does not apply until this exact promotion) into
     * the real catalogue's per-field-provenance shape. Unit comes from the nutrient
     * reference table (the only honest source for it); the caller (this contribution's
     * household member) never typed a unit.
     */
    private IngestProductCommand toIngestCommand(ProductContribution c, UUID operatorAccountId, Instant now) {
        Map<String, String> unitsByCode = catalog.listNutrients().stream()
                .collect(java.util.stream.Collectors.toMap(NutrientRefDto::code, NutrientRefDto::canonicalUnit));

        List<IngestProductCommand.NutrientValue> nutrientValues = new ArrayList<>();
        for (Map<String, Object> row : c.getNutrients()) {
            String code = (String) row.get("nutrientCode");
            String unit = unitsByCode.get(code);
            if (unit == null) {
                continue; // unknown nutrient code somehow slipped past submission validation
            }
            // A whole-number amount (e.g. a submitter typing "500", not "500.0") round-trips
            // through the jsonb column as a java.lang.Integer/Long, not a BigDecimal — Jackson's
            // untyped Object deserialization only produces BigDecimal for a value with a decimal
            // point. A hard (BigDecimal) cast here threw ClassCastException on approval for any
            // such contribution (found during Phase 6 hardening; a whole-number nutrient value
            // is a completely ordinary submission, not an edge case).
            BigDecimal amountPer100 = toBigDecimal(row.get("amountPer100"));
            if (amountPer100 == null) {
                continue; // per-serving-only entries have nothing to put in amount_per_100
            }
            nutrientValues.add(new IngestProductCommand.NutrientValue(
                    code, amountPer100, unit, NutrientBasis.PER_100G));
        }

        List<IngestProductCommand.AllergenValue> allergenValues = c.getAllergenCodes().stream()
                .map(code -> new IngestProductCommand.AllergenValue(code, AllergenDeclaration.CONTAINS))
                .toList();

        List<String> ingredientTokens = c.getIngredientsRawText() == null
                ? List.of() : List.of(c.getIngredientsRawText());

        return new IngestProductCommand(
                c.getProductName(),
                c.getBrandName(),
                c.getCategoryCode(),
                c.getPackSizeValue(),
                c.getPackSizeUnit(),
                c.getGtin(),
                inferGtinType(c.getGtin()),
                null,
                c.getServingDeclaredText(),
                c.getIngredientsRawText(),
                ingredientTokens,
                nutrientValues,
                allergenValues,
                SourceType.USER_SUBMITTED,
                CaptureMethod.MANUAL_ENTRY,
                c.getSubmittedByUserId(),
                null,
                null,
                VersionStatus.NEEDS_VERIFICATION,
                DataQuality.VERIFIED,
                operatorAccountId,
                now);
    }

    /** See the caller's comment: a jsonb-round-tripped numeric value is not reliably a BigDecimal. */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        throw new IllegalStateException("unexpected nutrient amount type: " + value.getClass());
    }

    private static GtinType inferGtinType(String gtin) {
        if (gtin == null) {
            return null;
        }
        return switch (gtin.length()) {
            case 8 -> GtinType.EAN8;
            case 12 -> GtinType.UPCA;
            case 13 -> GtinType.EAN13;
            case 14 -> GtinType.ITF14;
            default -> null;
        };
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

        java.util.Set<String> knownNutrientCodes = catalog.listNutrients().stream()
                .map(NutrientRefDto::code).collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            NutrientContribution entry = entries.get(i);
            String field = "nutrients[" + i + "]";

            if (entry.nutrientCode() == null || entry.nutrientCode().isBlank()) {
                throw AppException.validation(field + ".nutrientCode", "required");
            }
            if (!knownNutrientCodes.contains(entry.nutrientCode().trim())) {
                throw AppException.validation(field + ".nutrientCode", "unknown nutrient");
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
        java.util.Set<String> knownAllergenCodes = catalog.listAllergens().stream()
                .map(com.familyfood.catalog.application.dto.AllergenRefDto::code)
                .collect(java.util.stream.Collectors.toSet());

        List<String> result = new ArrayList<>(codes.size());
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw AppException.validation("allergenCodes", "must not contain a blank entry");
            }
            String trimmed = code.trim();
            if (trimmed.length() > MAX_ALLERGEN_CODE) {
                throw AppException.validation("allergenCodes", "entry must be at most " + MAX_ALLERGEN_CODE + " characters");
            }
            // Mirrors validateCategoryCode's real reference-table check: allergen_code
            // is NOT NULL REFERENCES allergen(code) on product_allergen, so an unknown
            // code accepted here would not fail until ops-review approval tries to write
            // it — as an opaque database constraint violation, not a clear 400 (found
            // during Phase 6 hardening review).
            if (!knownAllergenCodes.contains(trimmed)) {
                throw AppException.validation("allergenCodes", "unknown allergen code: " + trimmed);
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

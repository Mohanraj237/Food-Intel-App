package com.familyfood.capture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One manually-entered product submitted to the contribution queue (system-architecture.md
 * §6). Always created {@link ContributionStatus#PENDING} and never auto-published: this
 * entity has no relationship of any kind into {@code catalog.domain} — a row here becomes a
 * real product only if a human reviewer, working from a separate ops console, decides to
 * create one. Nothing in this module ever writes {@code catalog.product} or
 * {@code catalog.product_version}.
 *
 * <p>{@code nutrients} is a small jsonb array — {@code [{nutrientCode, amountPer100,
 * amountPerServing}]} — rather than a join table, because a contribution is a single
 * unreviewed submission, not yet the per-field-provenance model {@code catalog.ProductNutrient}
 * implements for verified facts (D-015 does not apply until a reviewer promotes this).
 *
 * <p>{@code categoryCode} stores a {@code category.code} (text), not a {@code category.id}
 * (uuid), mirroring {@code scoring.domain.ScoringRule#appliesToCategory} — see that class's
 * javadoc and V5__scoring.sql for why: it compares directly against the code a caller
 * submits without a UUID lookup, and keeps this module free of a JPA relationship into
 * catalog's entities.
 */
@Entity
@Table(name = "product_contribution")
public class ProductContribution {

    @Id
    private UUID id;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column
    private String gtin;

    @Column(name = "brand_name")
    private String brandName;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "category_code")
    private String categoryCode;

    @Column(name = "pack_size_value")
    private BigDecimal packSizeValue;

    @Column(name = "pack_size_unit")
    private String packSizeUnit;

    @Column(name = "serving_declared_text")
    private String servingDeclaredText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> nutrients = List.of();

    @Column(name = "ingredients_raw_text")
    private String ingredientsRawText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergen_codes", nullable = false, columnDefinition = "text[]")
    private List<String> allergenCodes = List.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_notes")
    private String reviewNotes;

    protected ProductContribution() {
        // JPA
    }

    /**
     * Records a new submission, always {@link ContributionStatus#PENDING}. Callers
     * (in practice only {@code ContributionService}) are expected to have already
     * validated and trimmed every argument — this factory copies collections defensively
     * but does not re-validate their contents.
     */
    public static ProductContribution submit(UUID submittedByUserId,
                                              String gtin,
                                              String brandName,
                                              String productName,
                                              String categoryCode,
                                              BigDecimal packSizeValue,
                                              String packSizeUnit,
                                              String servingDeclaredText,
                                              List<Map<String, Object>> nutrients,
                                              String ingredientsRawText,
                                              List<String> allergenCodes,
                                              Instant now) {
        ProductContribution contribution = new ProductContribution();
        contribution.id = UUID.randomUUID();
        contribution.submittedByUserId = submittedByUserId;
        contribution.gtin = gtin;
        contribution.brandName = brandName;
        contribution.productName = productName;
        contribution.categoryCode = categoryCode;
        contribution.packSizeValue = packSizeValue;
        contribution.packSizeUnit = packSizeUnit;
        contribution.servingDeclaredText = servingDeclaredText;
        contribution.nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
        contribution.ingredientsRawText = ingredientsRawText;
        contribution.allergenCodes = allergenCodes == null ? List.of() : List.copyOf(allergenCodes);
        contribution.status = ContributionStatus.PENDING;
        contribution.submittedAt = now;
        return contribution;
    }

    /**
     * Erasure's treatment of a contribution (D-027, decision-log.md): the submission's
     * factual content is retained — it is an unreviewed catalogue-improvement record, not
     * personal data about the submitter — but the link back to the erased account is
     * removed. Idempotent: an already-anonymised row has nothing left to clear.
     */
    public void anonymizeSubmitter() {
        this.submittedByUserId = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmittedByUserId() {
        return submittedByUserId;
    }

    public String getGtin() {
        return gtin;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public BigDecimal getPackSizeValue() {
        return packSizeValue;
    }

    public String getPackSizeUnit() {
        return packSizeUnit;
    }

    public String getServingDeclaredText() {
        return servingDeclaredText;
    }

    public List<Map<String, Object>> getNutrients() {
        return nutrients;
    }

    public String getIngredientsRawText() {
        return ingredientsRawText;
    }

    public List<String> getAllergenCodes() {
        return allergenCodes;
    }

    public ContributionStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }
}

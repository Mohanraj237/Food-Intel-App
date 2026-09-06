package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A citable public standard (D-009): a {@link ScoringRule} or {@link ScoreComponent} cannot
 * exist without one — {@code standard_ref} is a NOT NULL foreign key to this table's
 * {@code code}. Reference data, created and seeded by
 * {@code V1__extensions_and_reference_data.sql} — not by this module's own migration
 * (V5__scoring.sql only adds foreign keys to it); this module only reads it.
 *
 * <p>Every field here is bibliographic metadata — who published it, where, a document URL,
 * and a plain-language description of what the document covers. None of it is, or may ever
 * become, a numeric nutrient threshold (D-024).
 */
@Entity
@Table(name = "reference_standard")
public class ReferenceStandard {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_level", nullable = false)
    private EvidenceLevel evidenceLevel;

    @Column(name = "document_uri", nullable = false)
    private String documentUri;

    @Column(name = "published_on")
    private LocalDate publishedOn;

    @Column(name = "retrieved_on", nullable = false)
    private LocalDate retrievedOn;

    @Column(name = "scope_note", nullable = false)
    private String scopeNote;

    @Column(name = "review_due_on")
    private LocalDate reviewDueOn;

    protected ReferenceStandard() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public EvidenceLevel getEvidenceLevel() {
        return evidenceLevel;
    }

    public String getDocumentUri() {
        return documentUri;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public LocalDate getRetrievedOn() {
        return retrievedOn;
    }

    public String getScopeNote() {
        return scopeNote;
    }

    public LocalDate getReviewDueOn() {
        return reviewDueOn;
    }
}

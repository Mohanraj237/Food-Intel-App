package com.familyfood.consent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The exact policy text a person saw when they consented.
 *
 * <p>Every {@link ConsentRecord} points at one of these, which is what stops a later
 * policy change from retroactively broadening an existing consent. Publishing a new
 * version does not re-consent anybody; it closes the old version and leaves the existing
 * records pointing at the text their subjects actually read.
 */
@Entity
@Table(name = "consent_policy_version")
public class ConsentPolicyVersion {

    @Id
    private UUID id;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "document_uri", nullable = false)
    private String documentUri;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected ConsentPolicyVersion() {
        // JPA
    }

    public boolean isEffectiveAt(Instant when) {
        return !effectiveFrom.isAfter(when) && (effectiveTo == null || effectiveTo.isAfter(when));
    }

    public UUID getId() {
        return id;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public String getVersion() {
        return version;
    }

    public String getDocumentUri() {
        return documentUri;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }
}

package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * INS additive reference row (V15__catalog_enrichment.sql): the Codex CXG 36-1989 name and
 * functional class for an INS number, nothing more. Reference data — seeded by migration,
 * read-only here. Carries no safety or health information and must never be used to imply
 * any.
 */
@Entity
@Table(name = "additive")
public class Additive {

    @Id
    @Column(name = "ins_number")
    private String insNumber;

    @Column(name = "e_number")
    private String eNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "function_category")
    private String functionCategory;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    protected Additive() {
        // JPA
    }

    public String getInsNumber() {
        return insNumber;
    }

    public String getENumber() {
        return eNumber;
    }

    public String getName() {
        return name;
    }

    public String getFunctionCategory() {
        return functionCategory;
    }

    public String getSourceRef() {
        return sourceRef;
    }
}

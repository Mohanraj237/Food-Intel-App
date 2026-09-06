package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A brand. {@code normalizedName} is the lower-cased, unaccented matching form
 * maintained by the caller, not by a trigger (V4__catalog.sql).
 */
@Entity
@Table(name = "brand")
public class Brand {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "owner_company")
    private String ownerCompany;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Brand() {
        // JPA
    }

    public static Brand create(String name, String normalizedName, String ownerCompany,
                               String countryCode) {
        Brand brand = new Brand();
        brand.id = UUID.randomUUID();
        brand.name = name;
        brand.normalizedName = normalizedName;
        brand.ownerCompany = ownerCompany;
        brand.countryCode = countryCode == null ? "IN" : countryCode;
        return brand;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getOwnerCompany() {
        return ownerCompany;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

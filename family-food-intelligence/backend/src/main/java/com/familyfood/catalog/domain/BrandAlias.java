package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A spelling people actually type for a brand ("Britania", "Maggie", "Kellogs" — V4
 * {@code brand_alias}). The trigram search surface and the search-suggestion index read
 * these; {@code ProductIngestionService} writes the seeded ones when a brand is first
 * created ({@code BrandAliases}).
 */
@Entity
@Table(name = "brand_alias")
public class BrandAlias {

    @Id
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(nullable = false)
    private String alias;

    @Column(name = "normalized_alias", nullable = false)
    private String normalizedAlias;

    @Column(nullable = false)
    private String language;

    protected BrandAlias() {
        // JPA
    }

    public static BrandAlias create(UUID brandId, String alias, String normalizedAlias) {
        BrandAlias brandAlias = new BrandAlias();
        brandAlias.id = UUID.randomUUID();
        brandAlias.brandId = brandId;
        brandAlias.alias = alias;
        brandAlias.normalizedAlias = normalizedAlias;
        brandAlias.language = "en";
        return brandAlias;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public String getAlias() {
        return alias;
    }

    public String getNormalizedAlias() {
        return normalizedAlias;
    }

    public String getLanguage() {
        return language;
    }
}

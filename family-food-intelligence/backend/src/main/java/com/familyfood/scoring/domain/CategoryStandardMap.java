package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Which category of an external standard a catalogue category maps to, and which INR
 * {@link FoodType} its products default to (V16__scoring_v2_engine.sql). Reviewed content
 * — rows arrive with the rule set that uses them ({@code V17}), never from application code.
 * Read-only here.
 */
@Entity
@Table(name = "category_standard_map")
@IdClass(CategoryStandardMap.Key.class)
public class CategoryStandardMap {

    @Id
    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    @Id
    @Column(name = "standard_ref", nullable = false)
    private String standardRef;

    @Column(name = "external_code")
    private String externalCode;

    @Column(name = "external_name")
    private String externalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_type", nullable = false)
    private FoodType foodType;

    @Column(nullable = false)
    private String rationale;

    protected CategoryStandardMap() {
        // JPA
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getStandardRef() {
        return standardRef;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public String getExternalName() {
        return externalName;
    }

    public FoodType getFoodType() {
        return foodType;
    }

    public String getRationale() {
        return rationale;
    }

    /** Composite key: (category_code, standard_ref). */
    public static class Key implements Serializable {
        private String categoryCode;
        private String standardRef;

        public Key() {
        }

        public Key(String categoryCode, String standardRef) {
            this.categoryCode = categoryCode;
            this.standardRef = standardRef;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(categoryCode, other.categoryCode)
                    && Objects.equals(standardRef, other.standardRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryCode, standardRef);
        }
    }
}

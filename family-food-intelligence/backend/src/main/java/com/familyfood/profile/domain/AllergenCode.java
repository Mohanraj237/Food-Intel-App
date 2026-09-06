package com.familyfood.profile.domain;

/**
 * The FSSAI-declarable allergen set seeded into the {@code allergen} reference table by
 * {@code V1__extensions_and_reference_data.sql}.
 *
 * <p>The {@code profile} module does not own that reference table — it belongs to no
 * module yet (the {@code catalog} module is expected to grow an {@code Allergen} entity
 * and repository in Phase 3, per {@code docs/architecture/system-architecture.md}). This
 * enum mirrors the reference table's current contents purely so that a client request can
 * be validated at the boundary without profile reading another module's persistence
 * layer. The database foreign key ({@code profile_allergen.allergen_code references
 * allergen(code)}) remains the ultimate authority; if the reference table ever gains or
 * removes a code, this enum needs a matching change (and, per V1's own comment, that is a
 * schema-adjacent event, not a routine one).
 */
public enum AllergenCode {
    MILK,
    EGG,
    FISH,
    CRUSTACEAN,
    TREE_NUT,
    PEANUT,
    WHEAT,
    GLUTEN,
    SOY,
    SESAME,
    SULPHITE,
    MUSTARD
}

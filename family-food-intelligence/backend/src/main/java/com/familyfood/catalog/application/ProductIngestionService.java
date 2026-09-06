package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.Brand;
import com.familyfood.catalog.domain.Category;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductAllergen;
import com.familyfood.catalog.domain.ProductBarcode;
import com.familyfood.catalog.domain.ProductIngredient;
import com.familyfood.catalog.domain.ProductNutrient;
import com.familyfood.catalog.domain.ProductSource;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.infra.BrandRepository;
import com.familyfood.catalog.infra.CategoryRepository;
import com.familyfood.catalog.infra.ProductAllergenRepository;
import com.familyfood.catalog.infra.ProductBarcodeRepository;
import com.familyfood.catalog.infra.ProductIngredientRepository;
import com.familyfood.catalog.infra.ProductNutrientRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductSourceRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * The single write path into the catalogue (D-014/D-015): creates a new {@link Product}
 * and its first {@link ProductVersion}, with per-field provenance on every nutrient,
 * ingredient and allergen row. Used by the Open Food Facts bulk seed
 * ({@code catalog.seed.OpenFoodFactsSeedRunner}) and by ops-review contribution approval
 * ({@code ops.application.ContributionReviewService}) — one implementation, so both honour
 * the same invariants rather than each hand-writing the entity graph.
 *
 * <p>This service only ever creates a brand-new product. It does not attempt to match an
 * existing product by GTIN/name and add a superseding version — every caller today
 * (a first-time bulk import into an empty catalogue; an ops approval whose precondition is
 * "no active product already exists for this barcode") only ever needs a new product. A
 * future correction/merge flow is a separate, more careful piece of work and is out of
 * scope here.
 */
@Service
public class ProductIngestionService {

    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final ProductSourceRepository sources;
    private final ProductNutrientRepository nutrients;
    private final ProductIngredientRepository ingredients;
    private final ProductAllergenRepository allergens;
    private final ProductBarcodeRepository barcodes;
    private final BrandRepository brands;
    private final CategoryRepository categories;

    public ProductIngestionService(ProductRepository products,
                                    ProductVersionRepository versions,
                                    ProductSourceRepository sources,
                                    ProductNutrientRepository nutrients,
                                    ProductIngredientRepository ingredients,
                                    ProductAllergenRepository allergens,
                                    ProductBarcodeRepository barcodes,
                                    BrandRepository brands,
                                    CategoryRepository categories) {
        this.products = products;
        this.versions = versions;
        this.sources = sources;
        this.nutrients = nutrients;
        this.ingredients = ingredients;
        this.allergens = allergens;
        this.barcodes = barcodes;
        this.brands = brands;
        this.categories = categories;
    }

    @Transactional
    public UUID ingestNewProduct(IngestProductCommand cmd) {
        UUID brandId = cmd.brandName() == null || cmd.brandName().isBlank()
                ? null : resolveBrand(cmd.brandName());
        UUID categoryId = cmd.categoryCode() == null
                ? null
                : categories.findByCode(cmd.categoryCode()).map(Category::getId).orElse(null);

        Product product = Product.create(
                cmd.name(), normalize(cmd.name()), brandId,
                cmd.packSizeValue(), cmd.packSizeUnit(), categoryId, ProductStatus.ACTIVE);
        products.save(product);

        ProductVersion version = ProductVersion.create(
                product.getId(), 1, cmd.versionStatus(), true,
                cmd.servingSizeG(), cmd.servingSizeDeclared(), null, null,
                cmd.ingredientsRawText(), cmd.ingredientsRawText() == null ? null : "en",
                null, cmd.captureMethod(), cmd.capturedByUserId(), cmd.dataQuality());
        if (cmd.verifiedByUserId() != null) {
            version.markVerified(cmd.verifiedByUserId(), cmd.verifiedAt());
        }
        versions.save(version);

        ProductSource source = ProductSource.create(
                version.getId(), cmd.sourceType(), cmd.sourceRef(), cmd.licenseNote(),
                cmd.capturedByUserId(), null);
        sources.save(source);

        int position = 1;
        for (String token : cmd.ingredientTokens()) {
            ingredients.save(ProductIngredient.create(
                    version.getId(), null, token, position++, false, source.getId()));
        }

        for (IngestProductCommand.NutrientValue n : cmd.nutrients()) {
            nutrients.save(ProductNutrient.create(
                    version.getId(), n.nutrientCode(), n.amountPer100(), null, n.unit(),
                    n.basisPer100(), n.basisPer100(), false, null, source.getId()));
        }

        for (IngestProductCommand.AllergenValue a : cmd.allergens()) {
            allergens.save(ProductAllergen.create(
                    version.getId(), a.allergenCode(), a.declarationType(), source.getId(), null));
        }

        if (cmd.gtin() != null && !cmd.gtin().isBlank() && cmd.gtinType() != null) {
            barcodes.save(ProductBarcode.create(
                    product.getId(), cmd.gtin(), cmd.gtinType(), true, cmd.sourceType().name()));
        }

        return product.getId();
    }

    private UUID resolveBrand(String rawName) {
        String trimmed = rawName.trim();
        String normalized = normalize(trimmed);
        return brands.findByNormalizedName(normalized)
                .map(Brand::getId)
                .orElseGet(() -> brands.save(Brand.create(trimmed, normalized, null, "IN")).getId());
    }

    /** Mirrors the lower-case/unaccented matching form the database uses for brand names. */
    public static String normalize(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFKD);
        String stripped = decomposed.replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}

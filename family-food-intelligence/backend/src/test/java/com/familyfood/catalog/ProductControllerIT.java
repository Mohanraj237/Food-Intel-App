package com.familyfood.catalog;

import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.Brand;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.Category;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.GtinType;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductAllergen;
import com.familyfood.catalog.domain.ProductBarcode;
import com.familyfood.catalog.domain.ProductIngredient;
import com.familyfood.catalog.domain.ProductNutrient;
import com.familyfood.catalog.domain.ProductSource;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.infra.BrandRepository;
import com.familyfood.catalog.infra.CategoryRepository;
import com.familyfood.catalog.infra.ProductAllergenRepository;
import com.familyfood.catalog.infra.ProductBarcodeRepository;
import com.familyfood.catalog.infra.ProductIngredientRepository;
import com.familyfood.catalog.infra.ProductNutrientRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductSourceRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end product lookup through the real HTTP + database stack. Both endpoints are
 * anonymous — no auth header is sent, matching {@code SecurityConfig}'s permit-all
 * matchers for {@code /api/v1/products/**}.
 *
 * <p>Reference rows (category, nutrient, allergen, ingredient) come from the Flyway
 * migrations themselves (V1__extensions_and_reference_data.sql, V4__catalog.sql) — tests
 * look them up by their seeded codes rather than inserting duplicates.
 */
class ProductControllerIT extends IntegrationTestBase {

    @Autowired private BrandRepository brandRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVersionRepository versionRepository;
    @Autowired private ProductSourceRepository sourceRepository;
    @Autowired private ProductNutrientRepository nutrientRepository;
    @Autowired private ProductIngredientRepository ingredientRepository;
    @Autowired private ProductAllergenRepository allergenRepository;
    @Autowired private ProductBarcodeRepository barcodeRepository;

    @Test
    @DisplayName("GET /api/v1/products/{id} returns full detail matching the frontend contract")
    void getProductReturnsFullDetail() throws Exception {
        Category biscuits = categoryRepository.findByCode("BISCUITS").orElseThrow();
        Brand brand = brandRepository.save(Brand.create("Britannia", "britannia",
                "Britannia Industries", "IN"));
        Product product = productRepository.save(Product.create(
                "Good Day Cashew", "good day cashew", brand.getId(),
                new BigDecimal("75.00"), "g", biscuits.getId(), ProductStatus.ACTIVE));
        ProductVersion version = versionRepository.save(ProductVersion.create(
                product.getId(), 1, VersionStatus.VERIFIED, true,
                new BigDecimal("30.000"), "1 pack (30g)", true, new BigDecimal("2.50"),
                "Refined wheat flour, Sugar, Cashew, Palm oil", "en", null,
                CaptureMethod.MANUAL_ENTRY, null, DataQuality.VERIFIED));
        ProductSource source = sourceRepository.save(ProductSource.create(
                version.getId(), SourceType.MANUAL_ENTRY, null, null, null, null));

        nutrientRepository.save(ProductNutrient.create(version.getId(), "ENERGY_KCAL",
                new BigDecimal("500"), new BigDecimal("150"), "kcal",
                NutrientBasis.PER_100G, NutrientBasis.BOTH, false, null, source.getId()));
        nutrientRepository.save(ProductNutrient.create(version.getId(), "SODIUM_MG",
                new BigDecimal("300"), null, "mg",
                NutrientBasis.PER_100G, NutrientBasis.PER_100G, false, null, source.getId()));

        ingredientRepository.save(ProductIngredient.create(version.getId(), "WHEAT_FLOUR",
                "Refined wheat flour", 1, false, source.getId()));
        ingredientRepository.save(ProductIngredient.create(version.getId(), null,
                "Cashew nuts (5%)", 2, false, source.getId()));

        allergenRepository.save(ProductAllergen.create(version.getId(), "WHEAT",
                AllergenDeclaration.CONTAINS, source.getId(), null));

        barcodeRepository.save(ProductBarcode.create(product.getId(), "8901234500017",
                GtinType.EAN13, true, "MANUAL_ENTRY"));

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.brandName").value("Britannia"))
                .andExpect(jsonPath("$.name").value("Good Day Cashew"))
                .andExpect(jsonPath("$.packSize.unit").value("g"))
                .andExpect(jsonPath("$.categoryCode").value("BISCUITS"))
                .andExpect(jsonPath("$.categoryName").value("Biscuits and cookies"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.dataQuality").value("VERIFIED"))
                .andExpect(jsonPath("$.servingDeclaredText").value("1 pack (30g)"))
                // keyFacts: only IS_CORE nutrients, sorted by nutrient.sort_order —
                // ENERGY_KCAL (10) before SODIUM_MG (100).
                .andExpect(jsonPath("$.keyFacts.length()").value(2))
                .andExpect(jsonPath("$.keyFacts[0].nutrientCode").value("ENERGY_KCAL"))
                .andExpect(jsonPath("$.keyFacts[0].displayName").value("Energy"))
                .andExpect(jsonPath("$.keyFacts[1].nutrientCode").value("SODIUM_MG"))
                // detail-only fields
                .andExpect(jsonPath("$.currentVersion.versionNo").value(1))
                .andExpect(jsonPath("$.currentVersion.dataQuality").value("VERIFIED"))
                .andExpect(jsonPath("$.currentVersion.captureMethod").value("MANUAL_ENTRY"))
                .andExpect(jsonPath("$.currentVersion.servingPlausible").value(true))
                .andExpect(jsonPath("$.nutrients.length()").value(2))
                .andExpect(jsonPath("$.nutrients[0].source.type").value("MANUAL_ENTRY"))
                .andExpect(jsonPath("$.ingredients.length()").value(2))
                .andExpect(jsonPath("$.ingredients[0].rawText").value("Refined wheat flour"))
                .andExpect(jsonPath("$.ingredients[0].canonicalName")
                        .value("Refined wheat flour (maida)"))
                .andExpect(jsonPath("$.ingredients[1].canonicalName").doesNotExist())
                .andExpect(jsonPath("$.allergens.length()").value(1))
                .andExpect(jsonPath("$.allergens[0].code").value("WHEAT"))
                .andExpect(jsonPath("$.allergens[0].displayName").value("Wheat"))
                .andExpect(jsonPath("$.allergens[0].declarationType").value("CONTAINS"))
                .andExpect(jsonPath("$.barcodes.length()").value(1))
                .andExpect(jsonPath("$.barcodes[0].gtin").value("8901234500017"))
                .andExpect(jsonPath("$.barcodes[0].gtinType").value("EAN13"))
                .andExpect(jsonPath("$.barcodes[0].isPrimary").value(true))
                .andExpect(jsonPath("$.hasOpenConflict").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} for an unknown id returns 404 PRODUCT_NOT_FOUND")
    void getProductUnknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} for a non-ACTIVE product returns 404 PRODUCT_NOT_FOUND")
    void getProductNotActiveReturns404() throws Exception {
        Product discovered = productRepository.save(Product.create(
                "Unreviewed Snack", "unreviewed snack", null, null, null, null,
                ProductStatus.DISCOVERED));

        mockMvc.perform(get("/api/v1/products/{id}", discovered.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} for an ACTIVE product with no current version returns 404")
    void getProductNoCurrentVersionReturns404() throws Exception {
        Product product = productRepository.save(Product.create(
                "Half Captured Snack", "half captured snack", null, null, null, null,
                ProductStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/products/barcode/{gtin} resolves to the mapped product")
    void getProductByBarcodeResolves() throws Exception {
        Product product = productRepository.save(Product.create(
                "Marie Gold", "marie gold", null, null, null, null, ProductStatus.ACTIVE));
        versionRepository.save(ProductVersion.create(product.getId(), 1, VersionStatus.VERIFIED,
                true, null, null, null, null, null, null, null,
                CaptureMethod.MANUAL_ENTRY, null, DataQuality.UNVERIFIED));
        barcodeRepository.save(ProductBarcode.create(product.getId(), "8901234500024",
                GtinType.EAN13, true, "MANUAL_ENTRY"));

        mockMvc.perform(get("/api/v1/products/barcode/{gtin}", "8901234500024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Marie Gold"));
    }

    @Test
    @DisplayName("GET /api/v1/products/barcode/{gtin} for an unknown GTIN returns 404")
    void getProductByBarcodeUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/barcode/{gtin}", "8900000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/products/barcode/{gtin} mapped to two active products returns 409 BARCODE_AMBIGUOUS")
    void getProductByBarcodeConflictReturns409() throws Exception {
        Product productA = productRepository.save(Product.create(
                "Ambiguous A", "ambiguous a", null, null, null, null, ProductStatus.ACTIVE));
        Product productB = productRepository.save(Product.create(
                "Ambiguous B", "ambiguous b", null, null, null, null, ProductStatus.ACTIVE));
        String sharedGtin = "8901234500031";
        barcodeRepository.save(ProductBarcode.create(productA.getId(), sharedGtin,
                GtinType.EAN13, true, "MANUAL_ENTRY"));
        barcodeRepository.save(ProductBarcode.create(productB.getId(), sharedGtin,
                GtinType.EAN13, true, "MANUAL_ENTRY"));

        mockMvc.perform(get("/api/v1/products/barcode/{gtin}", sharedGtin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BARCODE_AMBIGUOUS"));
    }
}

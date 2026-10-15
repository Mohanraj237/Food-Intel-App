package com.familyfood.catalog.application;

import com.familyfood.catalog.application.dto.AllergenRowDto;
import com.familyfood.catalog.application.dto.BarcodeRowDto;
import com.familyfood.catalog.application.dto.CategoryDto;
import com.familyfood.catalog.application.dto.CurrentVersionDto;
import com.familyfood.catalog.application.dto.IngredientRowDto;
import com.familyfood.catalog.application.dto.KeyFactDto;
import com.familyfood.catalog.application.dto.NutrientRowDto;
import com.familyfood.catalog.application.dto.PackSizeDto;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.catalog.application.dto.SourceRefDto;
import com.familyfood.catalog.domain.Additive;
import com.familyfood.catalog.domain.Allergen;
import com.familyfood.catalog.domain.Brand;
import com.familyfood.catalog.domain.Category;
import com.familyfood.catalog.domain.Ingredient;
import com.familyfood.catalog.domain.Nutrient;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductAllergen;
import com.familyfood.catalog.domain.ProductBarcode;
import com.familyfood.catalog.domain.ProductConflict;
import com.familyfood.catalog.domain.ProductIngredient;
import com.familyfood.catalog.domain.ProductNutrient;
import com.familyfood.catalog.domain.ProductSource;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.infra.AdditiveRepository;
import com.familyfood.catalog.infra.AllergenRepository;
import com.familyfood.catalog.infra.BrandRepository;
import com.familyfood.catalog.infra.CategoryAliasRepository;
import com.familyfood.catalog.infra.CategoryRepository;
import com.familyfood.catalog.infra.IngredientRepository;
import com.familyfood.catalog.infra.NutrientRepository;
import com.familyfood.catalog.infra.ProductAllergenRepository;
import com.familyfood.catalog.infra.ProductBarcodeRepository;
import com.familyfood.catalog.infra.ProductConflictRepository;
import com.familyfood.catalog.infra.ProductIngredientRepository;
import com.familyfood.catalog.infra.ProductNutrientRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductSourceRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only product and category lookups.
 *
 * <p>Only versions with {@code isCurrent=true} and a non-rejected status are ever shown
 * to a user; only products with {@code status=ACTIVE} are ever shown. Neither invariant
 * is optional — a product without a current version, or one that has been rejected or
 * merged away, has nothing safe to display (see V4__catalog.sql).
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private static final Set<com.familyfood.catalog.domain.VersionStatus> HIDDEN_VERSION_STATUSES = Set.of(
            com.familyfood.catalog.domain.VersionStatus.REJECTED,
            com.familyfood.catalog.domain.VersionStatus.SUPERSEDED);

    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final ProductBarcodeRepository barcodes;
    private final ProductSourceRepository sources;
    private final ProductNutrientRepository nutrients;
    private final ProductIngredientRepository ingredients;
    private final ProductAllergenRepository allergens;
    private final ProductConflictRepository conflicts;
    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final NutrientRepository nutrientRefs;
    private final AllergenRepository allergenRefs;
    private final IngredientRepository ingredientRefs;
    private final AdditiveRepository additiveRefs;
    private final CategoryAliasRepository categoryAliasRefs;
    private final ProductImageService productImages;
    private final UnknownBarcodeService unknownBarcodes;

    public ProductQueryService(ProductRepository products,
                               ProductVersionRepository versions,
                               ProductBarcodeRepository barcodes,
                               ProductSourceRepository sources,
                               ProductNutrientRepository nutrients,
                               ProductIngredientRepository ingredients,
                               ProductAllergenRepository allergens,
                               ProductConflictRepository conflicts,
                               BrandRepository brands,
                               CategoryRepository categories,
                               NutrientRepository nutrientRefs,
                               AllergenRepository allergenRefs,
                               IngredientRepository ingredientRefs,
                               AdditiveRepository additiveRefs,
                               CategoryAliasRepository categoryAliasRefs,
                               ProductImageService productImages,
                               UnknownBarcodeService unknownBarcodes) {
        this.products = products;
        this.versions = versions;
        this.barcodes = barcodes;
        this.sources = sources;
        this.nutrients = nutrients;
        this.ingredients = ingredients;
        this.allergens = allergens;
        this.conflicts = conflicts;
        this.brands = brands;
        this.categories = categories;
        this.nutrientRefs = nutrientRefs;
        this.allergenRefs = allergenRefs;
        this.ingredientRefs = ingredientRefs;
        this.additiveRefs = additiveRefs;
        this.categoryAliasRefs = categoryAliasRefs;
        this.productImages = productImages;
        this.unknownBarcodes = unknownBarcodes;
    }

    // ------------------------------------------------------------------------ single product

    /** Cached (D-056). An unknown or hidden product throws, and a throw is never cached. */
    @Cacheable(cacheNames = CatalogCaches.PRODUCT, key = "#id")
    public ProductDetailDto getProduct(UUID id) {
        return loadProduct(id);
    }

    /**
     * The same detail read fresh from the database — for after-commit consumers (score
     * recomputation, rescore jobs) that must see facts written moments ago regardless of
     * whether the cache has been evicted yet.
     */
    public ProductDetailDto getProductUncached(UUID id) {
        return loadProduct(id);
    }

    /** Ids of ACTIVE products in stable id order — paging for bulk jobs (rescore, D-059). */
    public List<UUID> listActiveProductIds(int page, int size) {
        return products.findIdsByStatus(ProductStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    public int countActiveProducts() {
        return (int) Math.min(Integer.MAX_VALUE, products.countByStatus(ProductStatus.ACTIVE));
    }

    private ProductDetailDto loadProduct(UUID id) {
        Product product = products.findById(id)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductVersion version = versions.findByProductIdAndCurrentTrue(id)
                .filter(v -> !HIDDEN_VERSION_STATUSES.contains(v.getStatus()))
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductSummaryDto summary = buildSummaries(List.of(product), Map.of(id, version)).get(0);
        return buildDetail(summary, product, version);
    }

    /**
     * Not cached: which product a GTIN maps to, and whether it has become ambiguous, is read
     * fresh on every scan (AC-4.4). The product page the scan then opens reads the cached
     * detail by id.
     */
    public ProductDetailDto getProductByBarcode(String gtin) {
        List<ProductBarcode> matches = barcodes.findByGtinAndActiveTrue(gtin);
        if (matches.isEmpty()) {
            // Which barcodes people scan that we lack is catalogue-improvement data (V15).
            unknownBarcodes.recordMiss(gtin);
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        boolean anyConflict = matches.stream().anyMatch(ProductBarcode::isConflictFlag);
        Set<UUID> distinctProducts = matches.stream()
                .map(ProductBarcode::getProductId)
                .collect(Collectors.toSet());

        if (anyConflict || distinctProducts.size() > 1) {
            // AC-4.4: never guess between two active products claiming the same GTIN.
            throw new AppException(ErrorCode.BARCODE_AMBIGUOUS);
        }

        return getProduct(distinctProducts.iterator().next());
    }

    // ---------------------------------------------------------------------------- categories

    /**
     * What people type for each category → the category code (V15 {@code category_alias}),
     * for search's query parser. Cached with the category list: both change only with a
     * migration.
     */
    @Cacheable(cacheNames = CatalogCaches.CATEGORY_ALIASES, key = "'all'")
    public Map<String, String> categoryAliases() {
        Map<String, String> aliases = new java.util.TreeMap<>();
        categoryAliasRefs.findAll().forEach(a -> aliases.put(a.getAlias(), a.getCategoryCode()));
        return aliases;
    }

    @Cacheable(cacheNames = CatalogCaches.CATEGORIES, key = "'all'")
    public List<CategoryDto> listCategories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.getCode(), c.getName(), c.getPath(), c.getSortOrder(),
                        c.getTypicalServingMinG(), c.getTypicalServingMaxG()))
                .toList();
    }

    /**
     * Same-category candidates for {@code recommendation} (D-013) — active products only,
     * excluding the product being compared against, in no particular ranking order (the
     * caller decides ranking; this is a candidate pool, not a result).
     */
    public List<ProductSummaryDto> listActiveInCategory(String categoryCode, UUID excludeProductId, int limit) {
        var category = categories.findByCode(categoryCode).orElse(null);
        if (category == null) {
            return List.of();
        }
        List<Product> candidates = products.findByCategoryIdAndStatusAndIdNot(
                category.getId(), ProductStatus.ACTIVE, excludeProductId,
                org.springframework.data.domain.PageRequest.of(0, limit));

        Map<UUID, ProductVersion> versionByProductId = versions
                .findByProductIdInAndCurrentTrue(candidates.stream().map(Product::getId).toList()).stream()
                .filter(v -> !HIDDEN_VERSION_STATUSES.contains(v.getStatus()))
                .collect(Collectors.toMap(ProductVersion::getProductId, Function.identity()));

        return buildSummaries(candidates, versionByProductId);
    }

    /**
     * The nutrient reference vocabulary, for anything that needs a code's canonical unit
     * without duplicating V1__extensions_and_reference_data.sql's table — currently
     * ops-review contribution approval (a contribution's raw nutrient map has no unit of
     * its own; the reference row is the only honest source for it).
     */
    public List<com.familyfood.catalog.application.dto.NutrientRefDto> listNutrients() {
        return nutrientRefs.findAllByOrderBySortOrderAsc().stream()
                .map(n -> new com.familyfood.catalog.application.dto.NutrientRefDto(
                        n.getCode(), n.getDisplayName(), n.getCanonicalUnit(), n.getSortOrder(), n.isCore()))
                .toList();
    }

    /**
     * The allergen reference vocabulary (the FSSAI-declarable set) — used to validate a
     * contribution's submitted allergen codes against the same closed set
     * {@code product_allergen.allergen_code}'s foreign key enforces, so a bad code is
     * rejected honestly at submission time instead of failing ops-review approval later
     * with an opaque database constraint violation.
     */
    public List<com.familyfood.catalog.application.dto.AllergenRefDto> listAllergens() {
        return allergenRefs.findAllByOrderBySortOrderAsc().stream()
                .map(a -> new com.familyfood.catalog.application.dto.AllergenRefDto(
                        a.getCode(), a.getDisplayName(), a.isFssaiDeclarable(), a.getSortOrder()))
                .toList();
    }

    // -------------------------------------------------------------- batch summaries (search)

    /**
     * Summaries for the given product ids, in the given order. A product with no
     * current version (or whose current version is hidden) is silently dropped rather
     * than failing the whole batch — the search result simply has fewer items than ids.
     */
    public List<ProductSummaryDto> getSummaries(List<UUID> orderedIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Product> productsById = products.findAllById(orderedIds).stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Product> orderedProducts = orderedIds.stream()
                .map(productsById::get)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, ProductVersion> versionByProductId = versions
                .findByProductIdInAndCurrentTrue(orderedIds).stream()
                .filter(v -> !HIDDEN_VERSION_STATUSES.contains(v.getStatus()))
                .collect(Collectors.toMap(ProductVersion::getProductId, Function.identity()));

        return buildSummaries(orderedProducts, versionByProductId);
    }

    // --------------------------------------------------------------------------- assembly

    private List<ProductSummaryDto> buildSummaries(List<Product> productList,
                                                    Map<UUID, ProductVersion> versionByProductId) {
        Set<UUID> brandIds = productList.stream()
                .map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> categoryIds = productList.stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, Brand> brandsById = brands.findAllById(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));
        Map<UUID, Category> categoriesById = categories.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<UUID> versionIds = versionByProductId.values().stream()
                .map(ProductVersion::getId).toList();
        Map<UUID, List<ProductNutrient>> nutrientsByVersion = nutrients
                .findByProductVersionIdIn(versionIds).stream()
                .collect(Collectors.groupingBy(ProductNutrient::getProductVersionId));

        Map<String, Nutrient> coreNutrientsByCode = nutrientRefs.findByCoreTrue().stream()
                .collect(Collectors.toMap(Nutrient::getCode, Function.identity()));

        Map<UUID, String> imageUrlsByProduct = productImages.currentImageUrls(
                productList.stream().map(Product::getId).toList());

        Map<UUID, List<String>> declaredAllergensByVersion = versionIds.isEmpty() ? Map.of()
                : allergens.findByProductVersionIdIn(versionIds).stream()
                        .filter(pa -> pa.getDeclarationType() != com.familyfood.catalog.domain.AllergenDeclaration.NOT_DECLARED)
                        .collect(Collectors.groupingBy(ProductAllergen::getProductVersionId,
                                Collectors.mapping(ProductAllergen::getAllergenCode, Collectors.toList())));

        List<ProductSummaryDto> result = new ArrayList<>();
        for (Product product : productList) {
            ProductVersion version = versionByProductId.get(product.getId());
            if (version == null) {
                continue;
            }
            Brand brand = product.getBrandId() == null ? null : brandsById.get(product.getBrandId());
            Category category = product.getCategoryId() == null ? null : categoriesById.get(product.getCategoryId());

            List<ProductNutrient> versionNutrients = nutrientsByVersion.getOrDefault(version.getId(), List.of());
            List<KeyFactDto> keyFacts = versionNutrients.stream()
                    .filter(pn -> coreNutrientsByCode.containsKey(pn.getNutrientCode()))
                    .sorted(Comparator.comparingInt(pn -> coreNutrientsByCode.get(pn.getNutrientCode()).getSortOrder()))
                    .map(pn -> {
                        Nutrient ref = coreNutrientsByCode.get(pn.getNutrientCode());
                        return new KeyFactDto(pn.getNutrientCode(), ref.getDisplayName(),
                                pn.getAmountPerServing(), pn.getAmountPer100(), pn.getUnit());
                    })
                    .toList();

            PackSizeDto packSize = (product.getPackSizeValue() == null && product.getPackSizeUnit() == null)
                    ? null
                    : new PackSizeDto(product.getPackSizeValue(), product.getPackSizeUnit());

            result.add(new ProductSummaryDto(
                    product.getId(),
                    brand != null ? brand.getName() : "",
                    product.getName(),
                    packSize,
                    category != null ? category.getCode() : null,
                    category != null ? category.getName() : null,
                    imageUrlsByProduct.get(product.getId()),
                    version.getDataQuality().name(),
                    version.getServingSizeG(),
                    version.getServingSizeDeclared(),
                    keyFacts,
                    version.getDietFlag().name(),
                    version.isDietFlagEstimated(),
                    version.getNovaGroup(),
                    declaredAllergensByVersion.getOrDefault(version.getId(), List.of()).stream().sorted().toList(),
                    null));
        }
        return result;
    }

    private ProductDetailDto buildDetail(ProductSummaryDto summary, Product product, ProductVersion version) {
        Map<UUID, ProductSource> sourcesById = sources.findByProductVersionId(version.getId()).stream()
                .collect(Collectors.toMap(ProductSource::getId, Function.identity()));

        List<ProductNutrient> versionNutrients = nutrients.findByProductVersionId(version.getId());
        Map<String, Nutrient> nutrientRefsByCode = nutrientRefs
                .findAllById(versionNutrients.stream().map(ProductNutrient::getNutrientCode).toList())
                .stream().collect(Collectors.toMap(Nutrient::getCode, Function.identity()));

        List<NutrientRowDto> nutrientRows = versionNutrients.stream()
                .sorted(Comparator.comparingInt(pn -> nutrientRefsByCode.containsKey(pn.getNutrientCode())
                        ? nutrientRefsByCode.get(pn.getNutrientCode()).getSortOrder() : Integer.MAX_VALUE))
                .map(pn -> {
                    Nutrient ref = nutrientRefsByCode.get(pn.getNutrientCode());
                    ProductSource source = sourcesById.get(pn.getSourceId());
                    SourceRefDto sourceDto = source == null ? null
                            : new SourceRefDto(source.getSourceType().name(), source.getSourceRef(), source.getCapturedAt());
                    return new NutrientRowDto(
                            pn.getNutrientCode(),
                            ref != null ? ref.getDisplayName() : pn.getNutrientCode(),
                            pn.getAmountPer100(),
                            pn.getAmountPerServing(),
                            pn.getUnit(),
                            pn.getBasisPer100() == null ? null : pn.getBasisPer100().name(),
                            pn.isEstimated(),
                            pn.getConfidence(),
                            sourceDto);
                })
                .toList();

        List<ProductIngredient> versionIngredients = ingredients
                .findByProductVersionIdOrderByPositionAsc(version.getId());
        Map<String, Ingredient> ingredientRefsByCode = ingredientRefs.findAllById(
                versionIngredients.stream()
                        .map(ProductIngredient::getIngredientCode)
                        .filter(Objects::nonNull)
                        .toList())
                .stream().collect(Collectors.toMap(Ingredient::getCode, Function.identity()));

        Map<String, Additive> additivesByCode = additiveRefs.findAllById(
                versionIngredients.stream()
                        .map(ProductIngredient::getAdditiveCode)
                        .filter(Objects::nonNull)
                        .toList())
                .stream().collect(Collectors.toMap(Additive::getInsNumber, Function.identity()));

        List<IngredientRowDto> ingredientRows = versionIngredients.stream()
                .map(pi -> {
                    Ingredient ref = pi.getIngredientCode() == null ? null : ingredientRefsByCode.get(pi.getIngredientCode());
                    Additive additive = pi.getAdditiveCode() == null ? null : additivesByCode.get(pi.getAdditiveCode());
                    return new IngredientRowDto(pi.getRawText(), pi.getPosition(),
                            ref != null ? ref.getCanonicalName() : null, pi.isAdditive(),
                            ref != null ? ref.getInsNumber() : null,
                            pi.getAdditiveCode(),
                            additive != null ? additive.getName() : null,
                            additive != null ? additive.getFunctionCategory() : null);
                })
                .toList();

        List<ProductAllergen> versionAllergens = allergens.findByProductVersionId(version.getId());
        Map<String, Allergen> allergenRefsByCode = allergenRefs.findAllById(
                versionAllergens.stream().map(ProductAllergen::getAllergenCode).toList())
                .stream().collect(Collectors.toMap(Allergen::getCode, Function.identity()));

        List<AllergenRowDto> allergenRows = versionAllergens.stream()
                .map(pa -> {
                    Allergen ref = allergenRefsByCode.get(pa.getAllergenCode());
                    return new AllergenRowDto(pa.getAllergenCode(),
                            ref != null ? ref.getDisplayName() : pa.getAllergenCode(),
                            pa.getDeclarationType().name());
                })
                .toList();

        List<BarcodeRowDto> barcodeRows = barcodes.findByProductIdAndActiveTrue(product.getId()).stream()
                .map(b -> new BarcodeRowDto(b.getGtin(), b.getGtinType() == null ? null : b.getGtinType().name(), b.isPrimary()))
                .toList();

        boolean hasOpenConflict = conflicts.existsByProductVersionIdAndStatus(version.getId(), ProductConflict.Status.OPEN);

        ProductSource novaSource = version.getNovaSourceId() == null ? null : sourcesById.get(version.getNovaSourceId());
        CurrentVersionDto currentVersion = new CurrentVersionDto(
                version.getId(), version.getVersionNo(), version.getEffectiveFrom(),
                version.getServingSizeG(), version.getServingSizeDeclared(), version.getServingPlausible(),
                version.getDataQuality().name(), version.getCapturedAt(), version.getVerifiedAt(),
                version.getCaptureMethod().name(),
                version.getNovaGroup(),
                novaSource == null ? null : novaSource.getSourceType().name(),
                version.getDietFlag().name(),
                version.isDietFlagEstimated());

        return ProductDetailDto.from(summary, currentVersion, nutrientRows, ingredientRows,
                allergenRows, barcodeRows, hasOpenConflict);
    }
}

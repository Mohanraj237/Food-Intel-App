package com.familyfood.catalog.application;

import java.util.List;
import java.util.Map;

/**
 * Seeded brand aliases — the misspellings and short forms Indian shoppers actually type,
 * keyed by the brand's normalized name. The same list V15__catalog_enrichment.sql applies to
 * brands that already existed; {@link ProductIngestionService} applies it when a brand row
 * is first created, so a fresh import gets the same aliases. Keep the two in step.
 */
final class BrandAliases {

    static final Map<String, List<String>> BY_NORMALIZED_BRAND = Map.ofEntries(
            Map.entry("britannia", List.of("britania", "brittania")),
            Map.entry("amul", List.of("amool")),
            Map.entry("parle", List.of("parle g", "parle-g")),
            Map.entry("haldiram's", List.of("haldiram", "haldirams")),
            Map.entry("lay's", List.of("lays")),
            Map.entry("maggi", List.of("maggie")),
            Map.entry("cadbury", List.of("cadburys", "cadbury's")),
            Map.entry("kellogg's", List.of("kellogs", "kelloggs", "kellogg")),
            Map.entry("nestle", List.of("nestlé")),
            Map.entry("coca-cola", List.of("coca cola", "coke")),
            Map.entry("mother dairy", List.of("motherdairy")),
            Map.entry("hershey's", List.of("hersheys")),
            Map.entry("mcvitie's", List.of("mcvities")),
            Map.entry("bagrry's", List.of("bagrrys")),
            Map.entry("too yumm", List.of("tooyumm")),
            Map.entry("sunfeast", List.of("sun feast")),
            Map.entry("kurkure", List.of("kur kure")),
            Map.entry("paper boat", List.of("paperboat")),
            Map.entry("pepsico", List.of("pepsi")),
            Map.entry("saffola", List.of("safola")),
            Map.entry("bikaji", List.of("bikaji namkeen")),
            Map.entry("yoga bar", List.of("yogabar")));

    private BrandAliases() {
    }

    static List<String> aliasesFor(String normalizedBrandName) {
        return BY_NORMALIZED_BRAND.getOrDefault(normalizedBrandName, List.of());
    }
}

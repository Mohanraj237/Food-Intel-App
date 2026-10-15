package com.familyfood.search.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic natural-language search parsing (D-061): turns "biscuits under 10g sugar",
 * "high protein veg snacks" or "no nuts dairy free chocolate" into structured
 * {@link SearchFilters}, a {@link SearchSort}, and the residual text to match by name.
 *
 * <p>Table-driven and closed: a phrase this class does not recognise is left in the
 * residual query and matched as text — never guessed at. Every number in a filter is the
 * number the person typed; the only constant here is the salt-to-sodium conversion
 * (sodium = salt ÷ 2.5, the factor in Regulation (EU) No 1169/2011 Annex I, a chemical
 * unit conversion rather than a health threshold) so "under 1 g salt" filters on sodium,
 * the nutrient the catalogue stores. No model is involved; LLM intent extraction is a later,
 * flag-gated fallback that must produce the same {@link SearchFilters} shape.
 */
public final class SearchQueryParser {

    public record ParsedQuery(String residual, SearchFilters filters, SearchSort sort, List<InterpretedTerm> interpreted) {
    }

    private static final BigDecimal SALT_TO_SODIUM_DIVISOR = new BigDecimal("2.5");
    private static final BigDecimal MG_PER_G = new BigDecimal("1000");

    private enum Nutrient {
        ADDED_SUGAR("ADDED_SUGAR_G", "g"), TOTAL_SUGAR("TOTAL_SUGAR_G", "g"), SODIUM("SODIUM_MG", "mg"),
        SALT("SALT_G", "g"), SAT_FAT("SAT_FAT_G", "g"), ENERGY("ENERGY_KCAL", "kcal"),
        PROTEIN("PROTEIN_G", "g"), FIBRE("FIBRE_G", "g");

        final String code;
        final String canonicalUnit;

        Nutrient(String code, String canonicalUnit) {
            this.code = code;
            this.canonicalUnit = canonicalUnit;
        }
    }

    private static final String NUTRIENT_WORDS =
            "added\\s+sugars?|total\\s+sugars?|sugars?|sodium|salt|saturated\\s+fat|sat(?:urated)?\\.?\\s*fat|"
          + "calories|kcal|energy|proteins?|fib(?:re|er)";

    /** "under 10g sugar", "less than 200 mg sodium", "max 5 g sat fat", "<= 300 kcal". */
    private static final Pattern MAX_NUTRIENT = Pattern.compile(
            "(?:\\b(?:under|below|less\\s+than|lower\\s+than|max(?:imum)?|at\\s+most|up\\s*to|within)|<=?)\\s*"
          + "(\\d+(?:\\.\\d+)?)\\s*(g|gm|grams?|mg|kcal|cal(?:ories)?)?\\s*(?:of\\s+)?(" + NUTRIENT_WORDS + ")\\b",
            Pattern.CASE_INSENSITIVE);
    /** "sugar under 10g" (nutrient first). */
    private static final Pattern NUTRIENT_MAX = Pattern.compile(
            "\\b(" + NUTRIENT_WORDS + ")\\s*(?:under|below|less\\s+than|lower\\s+than|max(?:imum)?|at\\s+most|up\\s*to|<=?)\\s*"
          + "(\\d+(?:\\.\\d+)?)\\s*(g|gm|grams?|mg|kcal|cal(?:ories)?)?\\b",
            Pattern.CASE_INSENSITIVE);
    /** "at least 10 g protein", "over 5g fibre", "more than 20 g of protein", ">= 8 g protein". */
    private static final Pattern MIN_NUTRIENT = Pattern.compile(
            "(?:\\b(?:at\\s+least|over|above|more\\s+than|min(?:imum)?)|>=?)\\s*"
          + "(\\d+(?:\\.\\d+)?)\\s*(g|gm|grams?)?\\s*(?:of\\s+)?(proteins?|fib(?:re|er))\\b",
            Pattern.CASE_INSENSITIVE);

    private record Phrase(Pattern pattern, String type, String code) {
    }

    /** Longest phrases first so "low added sugar" wins over "low sugar". */
    private static final List<Phrase> PHRASES = List.of(
            phrase("low\\s+added\\s+sugars?|least\\s+added\\s+sugar|no\\s+added\\s+sugar", "SORT", "ADDED_SUGAR_ASC"),
            phrase("low\\s+sugars?|less\\s+sugar|least\\s+sugar|sugar\\s+free|without\\s+sugar", "SORT", "SUGAR_ASC"),
            phrase("low\\s+(?:sodium|salt)|less\\s+(?:sodium|salt)|least\\s+(?:sodium|salt)", "SORT", "SODIUM_ASC"),
            phrase("low\\s+(?:sat(?:urated)?\\s*fat)|less\\s+(?:sat(?:urated)?\\s*fat)", "SORT", "SAT_FAT_ASC"),
            phrase("high\\s+proteins?|protein\\s+rich|rich\\s+in\\s+protein|most\\s+protein", "SORT", "PROTEIN_DESC"),
            phrase("high\\s+fib(?:re|er)|fib(?:re|er)\\s+rich|rich\\s+in\\s+fib(?:re|er)|most\\s+fib(?:re|er)", "SORT", "FIBRE_DESC"),
            phrase("healthiest|best\\s+rated|highest\\s+rated|top\\s+rated|best\\s+score", "SORT", "SCORE_DESC"),
            phrase("minimally\\s+processed|less\\s+processed|least\\s+processed|not\\s+ultra[\\s-]?processed|unprocessed", "MAX_NOVA", "3"),
            // "non veg" / "non-vegan" must never read as the opposite; they stay in the residual text.
            phrase("(?<!non[\\s-]?)vegan", "DIET", "VEGAN"),
            phrase("(?<!non[\\s-]?)(?:pure\\s+veg|vegetarian|veggie|veg)", "DIET", "VEG"),
            phrase("(?:no|without)\\s+(?:tree\\s+)?nuts?|nut[\\s-]?free|tree[\\s-]?nut[\\s-]?free", "EXCLUDE_ALLERGEN", "TREE_NUT"),
            phrase("(?:no|without)\\s+peanuts?|peanut[\\s-]?free", "EXCLUDE_ALLERGEN", "PEANUT"),
            phrase("(?:no|without)\\s+(?:milk|dairy)|dairy[\\s-]?free|milk[\\s-]?free|lactose[\\s-]?free", "EXCLUDE_ALLERGEN", "MILK"),
            phrase("(?:no|without)\\s+gluten|gluten[\\s-]?free", "EXCLUDE_ALLERGEN", "GLUTEN"),
            phrase("(?:no|without)\\s+wheat|wheat[\\s-]?free", "EXCLUDE_ALLERGEN", "WHEAT"),
            phrase("(?:no|without)\\s+eggs?|egg[\\s-]?free|eggless", "EXCLUDE_ALLERGEN", "EGG"),
            phrase("(?:no|without)\\s+soy(?:a|abean)?|soy[\\s-]?free", "EXCLUDE_ALLERGEN", "SOY"),
            phrase("(?:no|without)\\s+sesame|sesame[\\s-]?free", "EXCLUDE_ALLERGEN", "SESAME"),
            phrase("(?:no|without)\\s+mustard|mustard[\\s-]?free", "EXCLUDE_ALLERGEN", "MUSTARD"));

    /** Filler words dropped from the residual once a phrase has been read. */
    private static final Pattern FILLER = Pattern.compile(
            "\\b(?:with|and|for|the|a|an|of|in|per|100\\s*g|100g|that|is|are|which|show\\s+me|find|me|some|any|options?|products?|items?|foods?)\\b",
            Pattern.CASE_INSENSITIVE);

    private final Map<String, String> categoryAliases;

    /** @param categoryAliases lower-case alias → category code, from {@code category_alias} (V15). */
    public SearchQueryParser(Map<String, String> categoryAliases) {
        Map<String, String> sorted = new LinkedHashMap<>();
        categoryAliases.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .forEach(e -> sorted.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue()));
        this.categoryAliases = sorted;
    }

    public ParsedQuery parse(String query) {
        if (query == null || query.isBlank()) {
            return new ParsedQuery(null, SearchFilters.none(), SearchSort.RELEVANCE, List.of());
        }
        String text = " " + query.trim().replaceAll("\\s+", " ") + " ";
        List<InterpretedTerm> interpreted = new ArrayList<>();
        Map<Nutrient, BigDecimal> max = new LinkedHashMap<>();
        Map<Nutrient, BigDecimal> min = new LinkedHashMap<>();
        List<String> allergens = new ArrayList<>();
        String diet = null;
        Integer maxNova = null;
        SearchSort sort = SearchSort.RELEVANCE;
        String category = null;

        // --- numeric limits (read first: "under 10g sugar" must not become "low sugar") ---
        Matcher m = MAX_NUTRIENT.matcher(text);
        StringBuilder rest = new StringBuilder();
        while (m.find()) {
            Nutrient nutrient = nutrientOf(m.group(3));
            BigDecimal value = new BigDecimal(m.group(1));
            addLimit(max, interpreted, "MAX_NUTRIENT", nutrient, value, m.group(2), m.group().trim());
            m.appendReplacement(rest, " ");
        }
        m.appendTail(rest);
        text = rest.toString();

        m = NUTRIENT_MAX.matcher(text);
        rest = new StringBuilder();
        while (m.find()) {
            Nutrient nutrient = nutrientOf(m.group(1));
            BigDecimal value = new BigDecimal(m.group(2));
            addLimit(max, interpreted, "MAX_NUTRIENT", nutrient, value, m.group(3), m.group().trim());
            m.appendReplacement(rest, " ");
        }
        m.appendTail(rest);
        text = rest.toString();

        m = MIN_NUTRIENT.matcher(text);
        rest = new StringBuilder();
        while (m.find()) {
            Nutrient nutrient = nutrientOf(m.group(3));
            BigDecimal value = new BigDecimal(m.group(1));
            addLimit(min, interpreted, "MIN_NUTRIENT", nutrient, value, m.group(2), m.group().trim());
            m.appendReplacement(rest, " ");
        }
        m.appendTail(rest);
        text = rest.toString();

        // --- closed phrases ---
        for (Phrase phrase : PHRASES) {
            Matcher pm = phrase.pattern().matcher(text);
            if (!pm.find()) {
                continue;
            }
            String source = pm.group().trim();
            switch (phrase.type()) {
                case "SORT" -> {
                    if (sort == SearchSort.RELEVANCE) {
                        sort = SearchSort.valueOf(phrase.code());
                        interpreted.add(new InterpretedTerm("SORT", phrase.code(), null, null, source));
                    }
                }
                case "MAX_NOVA" -> {
                    maxNova = Integer.valueOf(phrase.code());
                    interpreted.add(new InterpretedTerm("MAX_NOVA", null, new BigDecimal(phrase.code()), null, source));
                }
                case "DIET" -> {
                    if (diet == null) {
                        diet = phrase.code();
                        interpreted.add(new InterpretedTerm("DIET", phrase.code(), null, null, source));
                    }
                }
                case "EXCLUDE_ALLERGEN" -> {
                    if (!allergens.contains(phrase.code())) {
                        allergens.add(phrase.code());
                        interpreted.add(new InterpretedTerm("EXCLUDE_ALLERGEN", phrase.code(), null, null, source));
                    }
                }
                default -> throw new IllegalStateException("unknown phrase type " + phrase.type());
            }
            text = pm.replaceAll(" ");
        }

        // --- a category word (longest alias first); one category only ---
        // It becomes a filter only when nothing else is left to match: "biscuits under 10g
        // sugar" browses the category, but "Amul butter" searches for Amul butter by name
        // rather than for "Amul" inside whatever category butter happens to be filed under.
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> alias : categoryAliases.entrySet()) {
            Pattern word = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(alias.getKey()) + "(?![\\p{L}\\d])");
            Matcher am = word.matcher(lower);
            if (am.find()) {
                String without = text.substring(0, am.start()) + " " + text.substring(am.end());
                if (clean(without).isEmpty()) {
                    category = alias.getValue();
                    interpreted.add(new InterpretedTerm("CATEGORY", category, null, null, alias.getKey()));
                    text = without;
                }
                break;
            }
        }

        String residual = clean(text);

        BigDecimal maxSodium = max.get(Nutrient.SODIUM);
        if (max.containsKey(Nutrient.SALT)) {
            BigDecimal fromSalt = max.get(Nutrient.SALT).multiply(MG_PER_G).divide(SALT_TO_SODIUM_DIVISOR, 1, RoundingMode.HALF_UP);
            maxSodium = maxSodium == null ? fromSalt : maxSodium.min(fromSalt);
        }

        SearchFilters filters = new SearchFilters(
                category,
                max.get(Nutrient.ADDED_SUGAR),
                max.get(Nutrient.TOTAL_SUGAR),
                maxSodium,
                max.get(Nutrient.SAT_FAT),
                max.get(Nutrient.ENERGY),
                min.get(Nutrient.PROTEIN),
                min.get(Nutrient.FIBRE),
                allergens,
                diet,
                maxNova);
        return new ParsedQuery(residual.isEmpty() ? null : residual, filters, sort, List.copyOf(interpreted));
    }

    private static String clean(String text) {
        return FILLER.matcher(text).replaceAll(" ").replaceAll("[,;]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static void addLimit(Map<Nutrient, BigDecimal> limits, List<InterpretedTerm> interpreted, String type,
                                 Nutrient nutrient, BigDecimal typed, String typedUnit, String source) {
        BigDecimal canonical = toCanonical(nutrient, typed, typedUnit);
        limits.put(nutrient, canonical);
        interpreted.add(new InterpretedTerm(type, nutrient.code, canonical, nutrient.canonicalUnit, source));
    }

    /** Converts what was typed into the catalogue's unit: sodium in mg, energy in kcal, the rest in g. */
    private static BigDecimal toCanonical(Nutrient nutrient, BigDecimal value, String typedUnit) {
        String unit = typedUnit == null ? "" : typedUnit.toLowerCase(Locale.ROOT);
        if (nutrient == Nutrient.SODIUM) {
            // "200 mg sodium" or a bare "200 sodium" are milligrams; "0.5 g sodium" is grams.
            return unit.startsWith("g") ? value.multiply(MG_PER_G) : value;
        }
        if (nutrient != Nutrient.ENERGY && unit.equals("mg")) {
            return value.divide(MG_PER_G, 4, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        return value;
    }

    private static Nutrient nutrientOf(String word) {
        String w = word.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (w.startsWith("added")) return Nutrient.ADDED_SUGAR;
        if (w.startsWith("total") || w.startsWith("sugar")) return Nutrient.TOTAL_SUGAR;
        if (w.equals("sodium")) return Nutrient.SODIUM;
        if (w.equals("salt")) return Nutrient.SALT;
        if (w.contains("fat")) return Nutrient.SAT_FAT;
        if (w.startsWith("cal") || w.equals("kcal") || w.equals("energy")) return Nutrient.ENERGY;
        if (w.startsWith("protein")) return Nutrient.PROTEIN;
        if (w.startsWith("fib")) return Nutrient.FIBRE;
        throw new IllegalStateException("unmapped nutrient word " + word);
    }

    private static Phrase phrase(String regex, String type, String code) {
        return new Phrase(Pattern.compile("(?<![\\p{L}\\d])(?:" + regex + ")(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE), type, code);
    }
}

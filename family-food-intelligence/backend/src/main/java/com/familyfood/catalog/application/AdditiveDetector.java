package com.familyfood.catalog.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the INS / E numbers a label token carries, so {@code product_ingredient} can
 * be marked {@code is_additive} and linked to the {@code additive} reference row (V15). Pure
 * text matching over the verbatim label token — nothing here decides what an additive means.
 *
 * <p>Forms recognised (case-insensitive, optional spacing):
 * {@code INS 330}, {@code INS-503(ii)}, {@code INS 150d}, {@code E211}, {@code E-471},
 * {@code E 1422}, and a bare {@code (330)} / {@code [500(ii), 503(ii)]} list following a
 * class name such as {@code RAISING AGENTS}. A bare number with no INS/E prefix and no
 * surrounding brackets is <em>not</em> treated as an additive (a "2% milk solids" must
 * not become E2).
 */
public final class AdditiveDetector {

    /** {@code INS 503(ii)}, {@code INS-150d}, {@code E 471}, {@code E-1422}. */
    private static final Pattern PREFIXED = Pattern.compile(
            "\\b(?:INS|E)[\\s\\-]?(\\d{3,4}(?!\\d)[a-z]?(?:\\s?\\([ivx]+\\))?)", Pattern.CASE_INSENSITIVE);

    /**
     * A bracketed list of numbers: {@code (330, 331(iii))}, {@code [INS 503(ii), 500(ii)]}.
     * A square-bracket list runs to its closing bracket; a round-bracket list may contain
     * roman sub-numbers in their own round brackets.
     */
    private static final Pattern BRACKETED_LIST = Pattern.compile(
            "\\[([^\\]]*)\\]|\\(((?:[^()]|\\([ivx]+\\))*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_IN_LIST = Pattern.compile("(?<![\\d.])(\\d{3,4}[a-z]?(?:\\s?\\([ivx]+\\))?)(?![\\d.%])",
            Pattern.CASE_INSENSITIVE);

    private AdditiveDetector() {
    }

    /** Every INS code found in the token, normalised ({@code 503(ii)}, {@code 150d}), in order. */
    public static List<String> codesIn(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher prefixed = PREFIXED.matcher(rawToken);
        boolean anyPrefixed = false;
        while (prefixed.find()) {
            anyPrefixed = true;
            add(codes, prefixed.group(1));
        }
        // A prefixed form like "RAISING AGENTS [INS 503(ii), 500(ii)]" lists the second
        // number bare; a class name followed by a bracketed list ("EMULSIFIERS (322, 471)")
        // has none prefixed. Both forms are label conventions on Indian packs.
        Matcher list = BRACKETED_LIST.matcher(rawToken);
        while (list.find()) {
            String inside = list.group(1) != null ? list.group(1) : list.group(2);
            if (inside == null || inside.isBlank()) {
                continue;
            }
            if (!anyPrefixed && !PREFIXED.matcher(inside).find() && !looksLikeAdditiveList(inside)) {
                continue;
            }
            Matcher bare = BARE_IN_LIST.matcher(inside);
            while (bare.find()) {
                add(codes, bare.group(1));
            }
        }
        return List.copyOf(codes);
    }

    /** The first code, if any — what {@code product_ingredient.additive_code} links to. */
    public static Optional<String> primaryCode(String rawToken) {
        List<String> codes = codesIn(rawToken);
        return codes.isEmpty() ? Optional.empty() : Optional.of(codes.get(0));
    }

    /** Normalises {@code "503 (II)"} → {@code "503(ii)"}, {@code "150D"} → {@code "150d"}. */
    static String normalise(String raw) {
        return raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static void add(List<String> codes, String raw) {
        String code = normalise(raw);
        if (!codes.contains(code)) {
            codes.add(code);
        }
    }

    /** A bracketed list is an additive list when every comma-separated item is a bare code. */
    private static boolean looksLikeAdditiveList(String inside) {
        String[] items = inside.split(",");
        if (items.length == 0) {
            return false;
        }
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.matches("(?i)\\d{3,4}[a-z]?(?:\\s?\\([ivx]+\\))?")) {
                return false;
            }
        }
        return true;
    }
}

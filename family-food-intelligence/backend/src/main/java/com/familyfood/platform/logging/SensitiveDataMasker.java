package com.familyfood.platform.logging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks denied fields anywhere in a value before it reaches a log appender.
 *
 * <p>Two entry points, because sensitive data arrives in two shapes:
 * <ul>
 *   <li>{@link #maskStructure(Object)} — walks maps and lists, the normal path for
 *       structured logging arguments.
 *   <li>{@link #maskText(String)} — a defensive pass over free text (an exception
 *       message, a serialised body someone logged by hand) that rewrites
 *       {@code key=value} and {@code "key":"value"} pairs whose key is denied.
 * </ul>
 *
 * <p>The text pass exists because the structural pass cannot help when a developer
 * concatenates a DTO into a message string. It is a backstop, not the primary control:
 * the primary control is that request/response bodies are never logged at all.
 */
public final class SensitiveDataMasker {

    private static final int MAX_DEPTH = 8;

    /** Matches {@code key=value}, {@code key: value}, {@code "key":"value"}. */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "([\"']?)([A-Za-z_][A-Za-z0-9_-]{0,40})\\1\\s*[=:]\\s*([\"']?)([^\\s,;}\\])\"']{1,512})\\3");

    /** An email-shaped token appearing anywhere in free text. */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,255}\\.[A-Za-z]{2,24}");

    /** A bearer token or long opaque secret. */
    private static final Pattern BEARER = Pattern.compile(
            "(?i)bearer\\s+[A-Za-z0-9._~+/=-]{16,}");

    private final String pepper;

    public SensitiveDataMasker(String pepper) {
        this.pepper = pepper;
    }

    /** Recursively masks maps and collections; other values pass through unchanged. */
    public Object maskStructure(Object value) {
        return maskStructure(value, 0);
    }

    private Object maskStructure(Object value, int depth) {
        if (value == null || depth > MAX_DEPTH) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (SensitiveData.isDenied(key)) {
                    out.put(key, SensitiveData.REDACTED);
                } else if (SensitiveData.isHashed(key)) {
                    out.put(key, SensitiveData.pseudonymise(String.valueOf(e.getValue()), pepper));
                } else {
                    out.put(key, maskStructure(e.getValue(), depth + 1));
                }
            }
            return out;
        }
        if (value instanceof Iterable<?> it) {
            List<Object> out = new ArrayList<>();
            for (Object o : it) {
                out.add(maskStructure(o, depth + 1));
            }
            return out;
        }
        if (value instanceof CharSequence cs) {
            return maskText(cs.toString());
        }
        return value;
    }

    /** Best-effort masking of free text. Never throws; a masking failure must not lose a log. */
    public String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;

        result = BEARER.matcher(result).replaceAll("Bearer " + SensitiveData.REDACTED);

        Matcher kv = KEY_VALUE.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (kv.find()) {
            String key = kv.group(2);
            String replacement;
            if (SensitiveData.isDenied(key)) {
                replacement = kv.group(1) + key + kv.group(1) + "=" + SensitiveData.REDACTED;
            } else if (SensitiveData.isHashed(key)) {
                replacement = kv.group(1) + key + kv.group(1) + "="
                        + SensitiveData.pseudonymise(kv.group(4), pepper);
            } else {
                replacement = kv.group(0);
            }
            kv.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        kv.appendTail(sb);
        result = sb.toString();

        result = EMAIL.matcher(result).replaceAll(SensitiveData.REDACTED);
        return result;
    }
}

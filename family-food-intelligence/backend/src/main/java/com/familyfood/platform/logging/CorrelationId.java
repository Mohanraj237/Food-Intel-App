package com.familyfood.platform.logging;

import org.slf4j.MDC;

import java.util.UUID;

/** The per-request identifier that ties a client-visible error back to a server log line. */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationId() {}

    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "none" : id;
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /**
     * Accepts a client-supplied id only if it is short and alphanumeric — an untrusted
     * header is otherwise a log-injection vector (newlines forging log lines) and an
     * unbounded-memory vector in the MDC.
     */
    public static String sanitiseOrGenerate(String candidate) {
        if (candidate != null && !candidate.isBlank() && candidate.length() <= 64
                && candidate.chars().allMatch(c ->
                Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}

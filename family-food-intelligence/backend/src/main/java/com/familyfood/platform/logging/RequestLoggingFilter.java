package com.familyfood.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Assigns a correlation id, records one access line per request, and observes latency.
 *
 * <p>Deliberately logs the request <em>path template shape</em> and never the query
 * string or body. A query string on this API can carry a search term, and a body can
 * carry a health profile — neither belongs in an access log (AC-9.4).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = CorrelationId.sanitiseOrGenerate(
                request.getHeader(CorrelationId.HEADER));
        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();

            MDC.put("method", request.getMethod());
            MDC.put("path", scrubPath(request.getRequestURI()));
            MDC.put("status", Integer.toString(status));
            MDC.put("durationMs", Long.toString(millis));
            try {
                if (status >= 500) {
                    log.error("request_completed");
                } else if (status >= 400) {
                    log.warn("request_completed");
                } else {
                    log.info("request_completed");
                }
            } finally {
                MDC.remove("method");
                MDC.remove("path");
                MDC.remove("status");
                MDC.remove("durationMs");
                CorrelationId.clear();
            }
        }
    }

    /**
     * Replaces UUID path segments with {@code {id}}. Keeps access logs aggregatable and
     * avoids retaining identifiers that, joined against other tables, re-identify a person.
     */
    private static String scrubPath(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.replaceAll(
                "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                "/{id}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus");
    }
}

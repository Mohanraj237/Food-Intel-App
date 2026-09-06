package com.familyfood.platform.security;

import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.logging.CorrelationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders authentication and authorization failures as the same RFC 7807 documents the
 * rest of the API produces, so a client never has to parse two error formats.
 *
 * <p>Spring Security's defaults would emit an empty 401/403 with a {@code WWW-Authenticate}
 * header; that is both inconsistent and, for a browser client, actively harmful (it can
 * trigger a native basic-auth dialog).
 */
@Component
public class AuthenticationEntryPointHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Set by {@link JwtAuthenticationFilter} so we can distinguish expired from invalid. */
    public static final String ERROR_ATTRIBUTE = "familyfood.authError";

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPointHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attr = request.getAttribute(ERROR_ATTRIBUTE);
        ErrorCode code = attr instanceof ErrorCode ec ? ec : ErrorCode.AUTHENTICATION_REQUIRED;
        write(request, response, code);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, ErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", code.typeUri());
        body.put("title", code.name());
        body.put("status", code.status().value());
        body.put("code", code.name());
        body.put("messageKey", code.messageKey());
        body.put("correlationId", CorrelationId.current());
        body.put("instance", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

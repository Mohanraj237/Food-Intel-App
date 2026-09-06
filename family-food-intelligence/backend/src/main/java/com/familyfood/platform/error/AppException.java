package com.familyfood.platform.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base for every deliberate application failure.
 *
 * <p>The {@code messageKey} resolves in the locale bundle (D-019); the message that
 * reaches a user is never assembled here and never contains implementation detail
 * (Constitution §44). Anything not represented by one of these becomes a generic
 * 500 with a correlation id.
 */
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public AppException(ErrorCode code) {
        this(code, Map.of(), null);
    }

    public AppException(ErrorCode code, Map<String, Object> details) {
        this(code, details, null);
    }

    public AppException(ErrorCode code, Map<String, Object> details, Throwable cause) {
        // The technical message is for logs only. It must never carry a personal value.
        super(code.name(), cause);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public HttpStatus status() {
        return code.status();
    }

    // ---- Convenience factories for the cases used across modules -------------------

    public static AppException notFound(String resourceType) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", resourceType));
    }

    public static AppException forbidden() {
        return new AppException(ErrorCode.ACCESS_DENIED);
    }

    public static AppException conflict(ErrorCode code) {
        return new AppException(code);
    }

    public static AppException validation(String field, String reason) {
        return new AppException(ErrorCode.VALIDATION_FAILED, Map.of("field", field, "reason", reason));
    }
}

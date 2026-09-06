package com.familyfood.platform.error;

import com.familyfood.platform.logging.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates every failure into RFC 7807 {@code application/problem+json}.
 *
 * <p>Two invariants (Constitution §44):
 * <ul>
 *   <li>No stack trace, SQL fragment, internal class name or secret ever reaches a client.
 *       Unmapped exceptions become a bare 500 carrying only a correlation id, which is
 *       the sole thread back to the server log.
 *   <li>The body carries a {@code messageKey}, not English prose. The client resolves
 *       wording from its locale bundle, so user-facing copy stays in one reviewed place.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleApp(AppException ex, HttpServletRequest request) {
        // Deliberate failures are expected traffic; log at WARN without a stack trace
        // unless the status is a server fault.
        if (ex.status().is5xxServerError()) {
            log.error("app_error code={} path={}", ex.code(), request.getRequestURI(), ex);
        } else {
            log.warn("app_error code={} path={}", ex.code(), request.getRequestURI());
        }
        return problem(ex.code(), ex.details(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest request) {
        // Field names and constraint keys only. Rejected values are NOT echoed: a
        // rejected value can be a password, an allergen or a health condition.
        List<Map<String, String>> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> violations.add(Map.of(
                "field", fe.getField(),
                "rule", fe.getCode() == null ? "invalid" : fe.getCode())));
        ex.getBindingResult().getGlobalErrors().forEach(ge -> violations.add(Map.of(
                "field", ge.getObjectName(),
                "rule", ge.getCode() == null ? "invalid" : ge.getCode())));

        log.warn("validation_failed path={} violations={}", request.getRequestURI(), violations.size());
        return problem(ErrorCode.VALIDATION_FAILED, Map.of("violations", violations), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex,
                                          HttpServletRequest request) {
        // The parser message can quote the offending payload. Never forward it.
        log.warn("malformed_request path={}", request.getRequestURI());
        return problem(ErrorCode.MALFORMED_REQUEST, Map.of(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex,
                                        HttpServletRequest request) {
        return problem(ErrorCode.PAYLOAD_TOO_LARGE, Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("access_denied path={}", request.getRequestURI());
        return problem(ErrorCode.ACCESS_DENIED, Map.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex,
                                              HttpServletRequest request) {
        return problem(ErrorCode.AUTHENTICATION_REQUIRED, Map.of(), request);
    }

    /**
     * An unmapped path.
     *
     * <p>Both exceptions land here: {@code NoHandlerFoundException} when no controller
     * matches, and {@code NoResourceFoundException} when the static-resource handler is
     * the last thing to try and also fails. Without this, Spring 6 lets the latter reach
     * the catch-all below and a mistyped URL is reported as a 500.
     *
     * <p>That matters more than usual here. This product's error contract exists to keep
     * "we do not have this" distinct from "we broke" (Constitution §46) — reporting a
     * wrong URL as a server fault says the wrong one. It also logs at WARN rather than
     * ERROR, because an internet-facing service is probed for {@code /.env} and
     * {@code /wp-login.php} continuously, and letting that noise into the error log
     * buries the failures that matter.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ProblemDetail handleNoHandler(Exception ex, HttpServletRequest request) {
        log.warn("no_handler path={} method={}", request.getRequestURI(), request.getMethod());
        return problem(ErrorCode.RESOURCE_NOT_FOUND, Map.of(), request);
    }

    /**
     * A path parameter that cannot be converted to its declared type — e.g. a non-UUID
     * segment on a route whose {@code @PathVariable} is a {@code UUID}. Treated the same
     * as an unmapped path (404, not 500): the caller typed a resource identifier that this
     * API could never have issued, which is a "we do not have this" fact, not a server
     * fault (Constitution §46), and mirrors the {@link #handleNoHandler} precedent above.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest request) {
        log.warn("path_argument_type_mismatch path={}", request.getRequestURI());
        return problem(ErrorCode.RESOURCE_NOT_FOUND, Map.of(), request);
    }

    /** A path that exists but not for this verb. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                  HttpServletRequest request) {
        log.warn("method_not_supported path={} method={}",
                request.getRequestURI(), request.getMethod());
        return problem(ErrorCode.RESOURCE_NOT_FOUND, Map.of(), request);
    }

    /**
     * Anything unmapped. The client gets a correlation id and nothing else; the detail
     * lives in the server log where it belongs.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled_exception path={} method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, Map.of(), request);
    }

    private ProblemDetail problem(ErrorCode code, Map<String, Object> details,
                                  HttpServletRequest request) {
        HttpStatus status = code.status();
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(code.typeUri()));
        // A stable, non-personal title. Human wording comes from the locale bundle.
        pd.setTitle(code.name());
        pd.setProperty("code", code.name());
        pd.setProperty("messageKey", code.messageKey());
        pd.setProperty("correlationId", CorrelationId.current());
        if (!details.isEmpty()) {
            pd.setProperty("details", details);
        }
        // Never set `detail`: Spring would otherwise echo the exception message.
        pd.setInstance(URI.create(request.getRequestURI()));
        return pd;
    }
}

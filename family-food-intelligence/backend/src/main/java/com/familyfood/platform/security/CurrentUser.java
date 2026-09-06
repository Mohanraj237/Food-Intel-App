package com.familyfood.platform.security;

import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The only sanctioned way to learn who is calling.
 *
 * <p>Controllers must never read an account id, household id or member id from a request
 * path, query parameter or body and treat it as the caller's identity. That is the exact
 * shape of an IDOR, and an ArchUnit rule plus the security test suite exist to catch it.
 */
@Component
public class CurrentUser {

    /** The caller, or empty when the request is anonymous (permitted on public endpoints). */
    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** The caller, or a 401. Use on any path that touches a person. */
    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new AppException(ErrorCode.AUTHENTICATION_REQUIRED));
    }
}

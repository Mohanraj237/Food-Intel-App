package com.familyfood.identity.api;

import com.familyfood.identity.application.AuthenticationService;
import com.familyfood.identity.application.AuthenticationService.AuthResult;
import com.familyfood.identity.application.AuthenticationService.RequestContext;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Thin by design: bind, extract ambient request facts, delegate, map the result
 * (Constitution §36). No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationService authentication;
    private final CurrentUser currentUser;

    public AuthController(AuthenticationService authentication, CurrentUser currentUser) {
        this.authentication = authentication;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account",
               description = "Creates an account and returns a session. Consent for "
                           + "individual processing purposes is granted separately, per "
                           + "purpose, and is checked at processing time.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        AuthResult result = authentication.register(
                request.email(), request.password().toCharArray(), contextOf(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(result));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest http) {
        AuthResult result = authentication.login(
                request.email(), request.password().toCharArray(), contextOf(http));
        return AuthResponse.from(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new session",
               description = "Refresh tokens rotate on every use. Presenting a token that "
                           + "has already been used revokes every session for the account.")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request,
                                HttpServletRequest http) {
        return AuthResponse.from(authentication.refresh(request.refreshToken(), contextOf(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End this session")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authentication.logout(request.refreshToken(), currentUser.require().accountId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "End every session for this account")
    public ResponseEntity<Void> logoutEverywhere() {
        authentication.logoutEverywhere(currentUser.require().accountId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Trusts {@code X-Forwarded-For} only because {@code server.forward-headers-strategy}
     * is set and the app is expected to sit behind a proxy that overwrites it. Used for
     * throttling only, and stored hashed — never retained in raw form.
     */
    private static RequestContext contextOf(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : http.getRemoteAddr();
        return new RequestContext(ip, http.getHeader("User-Agent"));
    }
}

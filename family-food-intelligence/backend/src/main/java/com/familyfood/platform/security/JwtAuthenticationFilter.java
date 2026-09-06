package com.familyfood.platform.security;

import com.familyfood.platform.error.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads a bearer token, verifies it, and populates the security context.
 *
 * <p>An absent token is not an error: many endpoints are deliberately reachable
 * anonymously so that a user can search and read product facts before signing in (D-020).
 * Endpoints that need a principal declare that themselves; this filter only establishes
 * who is calling, never whether they may.
 *
 * <p>An <em>invalid</em> token, by contrast, is rejected immediately rather than treated
 * as anonymous — silently downgrading a bad token would mask token theft and confuse the
 * client about its own session state.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AuthenticatedUser user = jwtService.verifyAccessToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, AUTHORITIES);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AppException e) {
                // Clear any partial state and let the entry point render the problem
                // document. Do not continue the chain as anonymous.
                SecurityContextHolder.clearContext();
                request.setAttribute(AuthenticationEntryPointHandler.ERROR_ATTRIBUTE, e.code());
            }
        }

        chain.doFilter(request, response);
    }
}

package com.familyfood.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The HTTP security chain.
 *
 * <p>Anonymous access is deliberate and narrow: search, product read and reference data
 * only (D-020). Everything that touches a person — profile, household, consent, verdicts,
 * capture — requires a principal, and then requires a second, database-backed permission
 * check inside the application layer. The URL matcher here is a coarse first gate, never
 * the authorization decision (Constitution §32).
 *
 * <p>CSRF is disabled because the API is stateless and token-bearing: there is no
 * ambient credential a cross-site request could ride on. That holds only while the
 * refresh token is delivered as a body value rather than a cookie; if that ever changes,
 * CSRF protection must be re-enabled for the cookie-bearing endpoints.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final AuthenticationEntryPointHandler authHandler;

    @Value("${familyfood.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          AuthenticationEntryPointHandler authHandler) {
        this.jwtFilter = jwtFilter;
        this.authHandler = authHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(authHandler)
                    .accessDeniedHandler(authHandler))
            .headers(h -> h
                    .frameOptions(f -> f.deny())
                    .contentTypeOptions(Customizer.withDefaults())
                    .referrerPolicy(r -> r.policy(
                            org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31_536_000)))
            .authorizeHttpRequests(auth -> auth
                    // --- operational ---
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/health").permitAll()
                    .requestMatchers("/actuator/**").denyAll()

                    // --- API documentation (disabled in prod by profile config) ---
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()

                    // --- authentication ---
                    .requestMatchers("/api/v1/auth/register",
                                     "/api/v1/auth/login",
                                     "/api/v1/auth/refresh").permitAll()

                    // --- anonymous product browsing (D-020) ---
                    // Product facts and the product-level assessment are public.
                    // Per-person fit is not: it lives under /api/v1/households/**.
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/reference/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/consent/policies/**").permitAll()

                    // --- everything else needs a principal ---
                    .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Explicit origins only. Never "*" — this API serves credentialed requests.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type",
                "X-Correlation-Id", "Accept", "Accept-Language"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

package com.familyfood.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password minimum 12 characters with no composition rules. NIST SP 800-63B
 *                 guidance: length beats forced character classes, which mostly produce
 *                 predictable substitutions. Maximum 128 to bound Argon2 work per request.
 */
public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {}

package com.familyfood.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deliberately not annotated {@code @Email}: a validation error shaped differently for a
 * malformed address than for a wrong password is a weak enumeration oracle.
 */
public record LoginRequest(

        @NotBlank
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 128)
        String password
) {}

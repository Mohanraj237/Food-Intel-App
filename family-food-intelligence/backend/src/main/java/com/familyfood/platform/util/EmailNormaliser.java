package com.familyfood.platform.util;

import java.util.Locale;

/**
 * Canonical form for an email address before storage or comparison.
 *
 * <p>Lowercasing and trimming only. We deliberately do NOT strip dots or plus-tags:
 * treating {@code a.b@gmail.com} and {@code ab@gmail.com} as the same address is a
 * provider-specific behaviour, and applying it universally would merge genuinely distinct
 * accounts on providers that treat those as different mailboxes.
 */
public final class EmailNormaliser {

    private EmailNormaliser() {}

    public static String normalise(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

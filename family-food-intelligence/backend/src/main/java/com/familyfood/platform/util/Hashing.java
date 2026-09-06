package com.familyfood.platform.util;

import com.familyfood.platform.config.AppProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Peppered one-way hashing for values we must be able to compare but must not retain:
 * email addresses in the throttling table, IP addresses, user agents, and free-text
 * search queries (D-018).
 *
 * <p>Distinct from {@code PasswordHasher}, which is deliberately slow. These values need
 * constant-time-ish lookup at request rate, so this is a fast keyed digest. That is
 * acceptable because the threat here is <em>retention</em> — not being able to read the
 * value out of a dump — rather than offline cracking of a credential. The pepper is a
 * deployment secret and is never stored alongside the hashes.
 */
@Component
public class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] pepper;

    public Hashing(AppProperties properties) {
        this.pepper = properties.privacy().hashPepper().getBytes(StandardCharsets.UTF_8);
    }

    /** Stable pseudonym for a value. Same input plus same pepper gives the same output. */
    public String pseudonymise(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            byte[] out = digest.digest(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Digest of an opaque token for storage. No pepper and no lowercasing: the input is
     * already high-entropy and case-significant, and we need the ability to look the row
     * up by hash without knowing the deployment pepper.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** A 256-bit URL-safe random token for refresh tokens and invitations. */
    public static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

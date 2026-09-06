package com.familyfood.platform.security;

import com.familyfood.platform.config.AppProperties;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id password hashing (D-008).
 *
 * <p>Argon2id rather than BCrypt because it is memory-hard: the attacker's advantage from
 * commodity GPUs is bounded by memory bandwidth rather than by clock speed. Parameters
 * are configuration, not constants, so they can be raised as hardware improves without a
 * code change — and raising them is safe because the cost parameters are encoded in the
 * stored hash, so existing hashes keep verifying.
 *
 * <p>Defaults follow OWASP's Argon2id guidance (19 MiB, t=2, p=1) as a floor.
 */
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder;

    /**
     * A real hash of a value that corresponds to no account, computed once at startup.
     * Used only to equalise timing when no account exists — see {@link #matches}.
     */
    private final String dummyHash;

    public PasswordHasher(AppProperties properties) {
        AppProperties.Auth auth = properties.auth();
        this.encoder = new Argon2PasswordEncoder(
                auth.argon2SaltLength(),
                auth.argon2HashLength(),
                auth.argon2Parallelism(),
                auth.argon2MemoryKb(),
                auth.argon2Iterations());
        this.dummyHash = encoder.encode("no-account-timing-equaliser");
    }

    public String hash(char[] rawPassword) {
        // CharSequence rather than String at the call boundary so callers can zero the
        // array afterwards; the encoder itself unavoidably materialises a String.
        return encoder.encode(java.nio.CharBuffer.wrap(rawPassword));
    }

    public boolean matches(char[] rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            // Still run a real comparison so a missing-account path costs the same time
            // as a wrong-password path (timing side channel, AC-1.3).
            encoder.matches(java.nio.CharBuffer.wrap(rawPassword), dummyHash);
            return false;
        }
        return encoder.matches(java.nio.CharBuffer.wrap(rawPassword), storedHash);
    }

    /** Signals that a stored hash used weaker parameters and should be re-hashed on next login. */
    public boolean needsRehash(String storedHash) {
        return encoder.upgradeEncoding(storedHash);
    }
}

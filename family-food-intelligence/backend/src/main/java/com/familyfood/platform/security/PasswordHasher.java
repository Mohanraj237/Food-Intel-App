package com.familyfood.platform.security;

import com.familyfood.platform.config.AppProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.CharBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
 *
 * <p><b>Hashing runs on its own small pool of platform threads, never on the caller's
 * thread.</b> Requests run on virtual threads, which share a few carrier threads, one per
 * core. A virtual thread only yields its carrier when it blocks, and Argon2 never blocks: it
 * computes, and holds 19 MiB, for tens of milliseconds. A burst of sign-ins would therefore
 * occupy most of the carriers, and every other request would queue behind them. Here, the
 * caller parks while a bounded pool does the work. That bounds both the CPU and the memory
 * given to hashing, and the carriers stay free. Both the missing-account and the
 * wrong-password paths go through the same pool, so they still take comparable time (AC-1.3).
 */
@Component
public class PasswordHasher implements DisposableBean {

    /**
     * Half the cores: enough to keep sign-in throughput, while leaving the rest of the machine
     * to the carriers. Callers hash inside a transaction, so the database pool already limits
     * how many can be waiting here at once.
     */
    static final int DEFAULT_CONCURRENCY = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    private final PasswordEncoder encoder;
    private final ExecutorService hashingPool;

    /**
     * A real hash of a value that corresponds to no account, computed once at startup.
     * Used only to equalise timing when no account exists — see {@link #matches}.
     */
    private final String dummyHash;

    @Autowired
    public PasswordHasher(AppProperties properties) {
        this(argon2(properties.auth()), newHashingPool(DEFAULT_CONCURRENCY));
    }

    PasswordHasher(PasswordEncoder encoder, ExecutorService hashingPool) {
        this.encoder = encoder;
        this.hashingPool = hashingPool;
        this.dummyHash = encoder.encode("no-account-timing-equaliser");
    }

    public String hash(char[] rawPassword) {
        // CharSequence rather than String at the call boundary so callers can zero the
        // array afterwards; the encoder itself unavoidably materialises a String. The
        // caller waits for the result, so the array is not zeroed while still in use.
        return onHashingPool(() -> encoder.encode(CharBuffer.wrap(rawPassword)));
    }

    public boolean matches(char[] rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            // Still run a real comparison so a missing-account path costs the same time
            // as a wrong-password path (timing side channel, AC-1.3).
            onHashingPool(() -> encoder.matches(CharBuffer.wrap(rawPassword), dummyHash));
            return false;
        }
        return onHashingPool(() -> encoder.matches(CharBuffer.wrap(rawPassword), storedHash));
    }

    /** Signals that a stored hash used weaker parameters and should be re-hashed on next login. */
    public boolean needsRehash(String storedHash) {
        // Parses the encoded parameters only; no hashing, so it stays on the caller's thread.
        return encoder.upgradeEncoding(storedHash);
    }

    @Override
    public void destroy() {
        hashingPool.shutdown();
    }

    private <T> T onHashingPool(Callable<T> work) {
        Future<T> result = hashingPool.submit(work);
        try {
            return result.get();
        } catch (InterruptedException e) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a password hash", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("password hashing failed", cause);
        }
    }

    private static PasswordEncoder argon2(AppProperties.Auth auth) {
        return new Argon2PasswordEncoder(
                auth.argon2SaltLength(),
                auth.argon2HashLength(),
                auth.argon2Parallelism(),
                auth.argon2MemoryKb(),
                auth.argon2Iterations());
    }

    static ExecutorService newHashingPool(int threads) {
        return Executors.newFixedThreadPool(threads,
                Thread.ofPlatform().name("argon2-", 1).daemon(true).factory());
    }
}

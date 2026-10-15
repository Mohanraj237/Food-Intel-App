package com.familyfood.platform.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Argon2 runs on a bounded pool of platform threads, never on the request's virtual thread,
 * and behaves exactly as before for its callers.
 */
class PasswordHasherTest {

    private final List<PasswordHasher> hashers = new ArrayList<>();

    @AfterEach
    void shutDownPools() {
        hashers.forEach(PasswordHasher::destroy);
    }

    private PasswordHasher hasher(PasswordEncoder encoder, int threads) {
        PasswordHasher hasher = new PasswordHasher(encoder, PasswordHasher.newHashingPool(threads));
        hashers.add(hasher);
        return hasher;
    }

    /** Stands in for Argon2: runs {@code work} on every call, then returns a fixed answer. */
    private static PasswordEncoder encoderThatRuns(Runnable work) {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                work.run();
                return "hash";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                work.run();
                return false;
            }
        };
    }

    @Test
    @DisplayName("hashing runs on the argon2 platform threads, not on the calling virtual thread")
    void hashesOffTheCallersThread() throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        PasswordHasher hasher = hasher(encoderThatRuns(() -> worker.set(Thread.currentThread())), 2);

        Thread caller = Thread.ofVirtual().start(() -> hasher.hash("secret".toCharArray()));
        caller.join();

        assertThat(worker.get()).isNotSameAs(caller);
        assertThat(worker.get().isVirtual()).isFalse();
        assertThat(worker.get().getName()).startsWith("argon2-");
    }

    @Test
    @DisplayName("48 concurrent callers never run more hashes at once than the pool has threads")
    void concurrencyIsBoundedByThePool() throws Exception {
        int poolThreads = 3;
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        PasswordHasher hasher = hasher(encoderThatRuns(() -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
        }), poolThreads);
        peak.set(0); // the constructor's one-off dummy hash is not part of this test

        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 48; i++) {
                results.add(callers.submit(() -> hasher.hash("secret".toCharArray())));
            }
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("hash");
            }
        }

        // Saturated, and never exceeded.
        assertThat(peak.get()).isEqualTo(poolThreads);
    }

    @Test
    @DisplayName("a missing account still costs one real comparison (AC-1.3)")
    void missingAccountStillCompares() {
        AtomicInteger comparisons = new AtomicInteger();
        PasswordHasher hasher = hasher(new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "dummy";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                comparisons.incrementAndGet();
                return true;
            }
        }, 2);

        assertThat(hasher.matches("secret".toCharArray(), null)).isFalse();
        assertThat(hasher.matches("secret".toCharArray(), "  ")).isFalse();
        assertThat(comparisons.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("an encoder failure reaches the caller unchanged")
    void encoderFailurePropagates() {
        PasswordHasher hasher = hasher(new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                if (raw.toString().equals("boom")) {
                    throw new IllegalArgumentException("boom");
                }
                return "hash";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return false;
            }
        }, 2);

        assertThatThrownBy(() -> hasher.hash("boom".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
    }

    @Test
    @DisplayName("real Argon2id: the hash verifies, a wrong password and a missing hash do not")
    void realArgon2RoundTrip() {
        PasswordHasher hasher = hasher(new Argon2PasswordEncoder(16, 32, 1, 19456, 2), 2);

        String hash = hasher.hash("correct horse".toCharArray());

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hasher.matches("correct horse".toCharArray(), hash)).isTrue();
        assertThat(hasher.matches("wrong horse".toCharArray(), hash)).isFalse();
        assertThat(hasher.matches("correct horse".toCharArray(), null)).isFalse();
        assertThat(hasher.needsRehash(hash)).isFalse();
    }
}

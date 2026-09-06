package com.familyfood.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One authentication attempt, recorded for throttling and anomaly detection.
 *
 * <p>Both the email and the IP are stored hashed: a leak of this table must not enumerate
 * users or reveal where they signed in from (D-018).
 */
@Entity
@Table(name = "login_attempt")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_hash", nullable = false)
    private String emailHash;

    @Column(name = "ip_hash", nullable = false)
    private String ipHash;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {
        // JPA
    }

    public static LoginAttempt record(String emailHash, String ipHash, boolean succeeded,
                                      Instant at) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.emailHash = emailHash;
        attempt.ipHash = ipHash;
        attempt.succeeded = succeeded;
        attempt.attemptedAt = at;
        return attempt;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}

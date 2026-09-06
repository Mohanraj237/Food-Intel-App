package com.familyfood.platform.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.HexFormat;

/**
 * The single place that decides what may not be logged, and the helpers for the two
 * safe alternatives: hashing and redaction.
 *
 * <p>This is the enforcement point for AC-9.4 and Constitution §31/§43. The deny-list
 * is asserted by {@code SensitiveDataMaskerTest} and by a log-inspection test that runs
 * a realistic request flow and greps the captured output.
 *
 * <p>The rule is a deny-list rather than an allow-list because logging is additive and
 * incidental: a developer adding a field to a DTO should not silently start logging a
 * health condition. Anything whose key matches here is replaced before serialisation,
 * wherever in an object graph it appears.
 */
public final class SensitiveData {

    private SensitiveData() {}

    public static final String REDACTED = "[redacted]";

    /**
     * Field names that must never appear in a log, in any casing or separator style.
     * Compared after normalising to lowercase with {@code _} and {@code -} stripped.
     */
    public static final Set<String> DENIED_FIELDS = Set.of(
            // credentials and tokens
            "password", "newpassword", "currentpassword", "passwordhash", "secret",
            "token", "accesstoken", "refreshtoken", "idtoken", "authorization",
            "apikey", "clientsecret", "jwt", "credential", "otp", "pin",
            // direct identifiers
            "email", "emailaddress", "phone", "phonenumber", "mobile", "displaylabel",
            // health and dietary data (Constitution §31, §43)
            "condition", "conditions", "conditioncode", "declaredcondition",
            "allergen", "allergens", "allergencode", "profileallergen",
            "goal", "goals", "goalcode", "dietarypreference", "diet",
            "ageband", "sex", "healthprofile", "profiledata",
            // child data — strictest category (Pack 06 CH-005/009)
            "childprofile", "childname", "childdata", "guardianrelationship",
            // free text that can carry anything
            "query", "searchquery", "searchtext", "q", "note", "notes",
            "rawtext", "ocrtext", "ingredientsraw", "message", "prompt",
            // consent evidence can embed identifiers
            "evidence", "consentevidence"
    );

    /**
     * Field names that are useful in a log but must be reduced to a hash first.
     * Correlating events without retaining the value.
     */
    public static final Set<String> HASHED_FIELDS = Set.of(
            "ip", "ipaddress", "remoteaddr", "useragent", "queryhash", "emailhash"
    );

    public static String normaliseKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase().replace("_", "").replace("-", "");
    }

    public static boolean isDenied(String key) {
        return DENIED_FIELDS.contains(normaliseKey(key));
    }

    public static boolean isHashed(String key) {
        return HASHED_FIELDS.contains(normaliseKey(key));
    }

    /**
     * One-way, truncated digest for correlation-only use. Peppered so the digest is not
     * reversible by dictionary attack against a known small domain such as an email or
     * an IPv4 address.
     *
     * <p>Not a password hash — see {@code PasswordHasher} for that.
     */
    public static String pseudonymise(String value, String pepper) {
        if (value == null || value.isBlank()) {
            return REDACTED;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            byte[] out = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            // 12 hex chars is ample to correlate within one dataset and useless outside it.
            return HexFormat.of().formatHex(out).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

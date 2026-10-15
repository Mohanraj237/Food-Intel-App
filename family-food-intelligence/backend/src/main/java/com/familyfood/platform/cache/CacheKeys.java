package com.familyfood.platform.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/**
 * The Redis key convention (D-056):
 *
 * <pre>{@code <prefix><cache name>:<version>::<key>      e.g.  ffi:catalog.product:3fa1b2c4::0b6e5f1e-...}</pre>
 *
 * <ul>
 *   <li><b>prefix</b> — {@code familyfood.cache.key-prefix}, {@code ffi:} by default.
 *   <li><b>cache name</b> — {@code <module>.<what>}, from the owning module's {@link CacheSpec}.
 *   <li><b>version</b> — eight hex characters fingerprinting two things: the structure of the
 *       cached type, and the database schema version the entries were read from. Either one
 *       changing moves the cache to a fresh keyspace, and old entries are never read again —
 *       they simply expire. That matters because Redis, unlike an in-process cache, outlives
 *       a deploy:
 *       <ul>
 *         <li>A changed record (a field added, removed, renamed or retyped) would otherwise be
 *             read from entries written in the old shape, with Jackson filling the new field
 *             with null or false — silently, for up to a TTL, and in both directions while old
 *             and new instances share one Redis.
 *         <li>A migration that corrects data (an allergen declaration, a category) would
 *             otherwise be hidden behind entries read before it ran.
 *       </ul>
 *   <li><b>key</b> — the entry's natural id: a product UUID, or {@code all} for a whole list.
 * </ul>
 *
 * <p>The fingerprint covers record component names and generic types, recursively. It does
 * not see Jackson annotations or enum constants; a change that only renames a JSON property
 * through {@code @JsonProperty} needs the owning cache renamed by hand.
 */
final class CacheKeys {

    private CacheKeys() {}

    static String prefix(String keyPrefix, String cacheName, String version) {
        return keyPrefix + cacheName + ":" + version + "::";
    }

    static String versionOf(JavaType type, String schemaVersion) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((structureOf(type) + "@schema=" + schemaVersion).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JCA algorithm; no conforming JVM lacks it.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** The canonical text the type half of the fingerprint hashes. Package-private for tests. */
    static String structureOf(JavaType type) {
        StringBuilder out = new StringBuilder();
        describe(type, out, new HashSet<>());
        return out.toString();
    }

    private static void describe(JavaType type, StringBuilder out, Set<Class<?>> enclosing) {
        if (type.isArrayType()) {
            describe(type.getContentType(), out, enclosing);
            out.append("[]");
            return;
        }
        Class<?> raw = type.getRawClass();
        out.append(raw.getName());
        if (type.containedTypeCount() > 0) {
            out.append('<');
            for (int i = 0; i < type.containedTypeCount(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                describe(type.containedType(i), out, enclosing);
            }
            out.append('>');
        }
        // A record that contains itself is described once; its inner occurrence is just a name.
        if (!raw.isRecord() || !enclosing.add(raw)) {
            return;
        }
        out.append('{');
        TypeFactory types = TypeFactory.defaultInstance();
        for (RecordComponent component : raw.getRecordComponents()) {
            out.append(component.getName()).append(':');
            describe(types.resolveMemberType(component.getGenericType(), type.getBindings()), out, enclosing);
            out.append(';');
        }
        out.append('}');
        enclosing.remove(raw);
    }
}

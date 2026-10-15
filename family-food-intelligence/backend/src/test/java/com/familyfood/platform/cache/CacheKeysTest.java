package com.familyfood.platform.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The Redis key convention and the fingerprint that versions it (D-056). */
class CacheKeysTest {

    record Leaf(String code, BigDecimal amount) {}

    record Branch(UUID id, List<Leaf> leaves, boolean flag) {}

    record Node(String name, List<Node> children) {}

    private static JavaType type(TypeReference<?> reference) {
        return TypeFactory.defaultInstance().constructType(reference);
    }

    @Test
    @DisplayName("a key prefix is <prefix><cache name>:<version>::")
    void prefixFollowsTheConvention() {
        assertThat(CacheKeys.prefix("ffi:", "catalog.product", "0a1b2c3d"))
                .isEqualTo("ffi:catalog.product:0a1b2c3d::");
    }

    @Test
    @DisplayName("the version is eight hex characters and the same on every computation")
    void versionIsShortAndStable() {
        String version = CacheKeys.versionOf(type(new TypeReference<Branch>() {}), "14");

        assertThat(version).matches("[0-9a-f]{8}");
        assertThat(CacheKeys.versionOf(type(new TypeReference<Branch>() {}), "14")).isEqualTo(version);
    }

    @Test
    @DisplayName("a new database schema version gives the same type a new version")
    void schemaVersionChangesTheVersion() {
        JavaType branch = type(new TypeReference<Branch>() {});

        assertThat(CacheKeys.versionOf(branch, "15")).isNotEqualTo(CacheKeys.versionOf(branch, "14"));
    }

    @Test
    @DisplayName("the structure names every record component and its generic type, recursively")
    void structureCoversNestedRecordsAndGenerics() {
        String leaf = Leaf.class.getName() + "{code:java.lang.String;amount:java.math.BigDecimal;}";

        assertThat(CacheKeys.structureOf(type(new TypeReference<Branch>() {})))
                .isEqualTo(Branch.class.getName() + "{id:java.util.UUID;leaves:java.util.List<" + leaf
                        + ">;flag:boolean;}");
    }

    @Test
    @DisplayName("different value types get different versions")
    void differentTypesDiffer() {
        String leaves = CacheKeys.versionOf(type(new TypeReference<List<Leaf>>() {}), "14");
        String branches = CacheKeys.versionOf(type(new TypeReference<List<Branch>>() {}), "14");
        String leaf = CacheKeys.versionOf(type(new TypeReference<Leaf>() {}), "14");

        assertThat(leaves).isNotEqualTo(branches).isNotEqualTo(leaf);
    }

    @Test
    @DisplayName("a record that contains itself is described once, not forever")
    void selfReferenceTerminates() {
        assertThat(CacheKeys.structureOf(type(new TypeReference<Node>() {})))
                .isEqualTo(Node.class.getName() + "{name:java.lang.String;children:java.util.List<"
                        + Node.class.getName() + ">;}");
    }
}

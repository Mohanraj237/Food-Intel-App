package com.familyfood.architecture;

import com.familyfood.platform.cache.CacheSpec;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * What may be cached (D-056).
 *
 * <p>Only public, non-personal catalogue data. Nothing about a person — profile, verdict,
 * consent state, health data — is cached. Those are read fresh, so a consent withdrawal
 * takes effect at once (D-018), and they never leave the database for a second store that
 * would need its own access control, retention and erasure. Allowing another module to cache
 * is a deliberate change to this test, made with a decision-log entry, never a side effect.
 */
class CachingBoundaryTest {

    private static final String CATALOG = "com.familyfood.catalog..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.familyfood");
    }

    @Test
    @DisplayName("caching annotations appear only in the catalogue module")
    void cachingAnnotationsStayInTheCatalogue() {
        ArchRule methods = noMethods()
                .that().areAnnotatedWith(Cacheable.class)
                .or().areAnnotatedWith(CachePut.class)
                .or().areAnnotatedWith(CacheEvict.class)
                .or().areAnnotatedWith(Caching.class)
                .should().beDeclaredInClassesThat().resideOutsideOfPackage(CATALOG)
                .because("only public catalogue data may be cached; nothing about a person (D-056)");
        ArchRule types = noClasses()
                .that().areAnnotatedWith(Cacheable.class)
                .or().areAnnotatedWith(CachePut.class)
                .or().areAnnotatedWith(CacheEvict.class)
                .or().areAnnotatedWith(Caching.class)
                .should().resideOutsideOfPackage(CATALOG)
                .because("only public catalogue data may be cached; nothing about a person (D-056)")
                .allowEmptyShould(true);

        methods.check(classes);
        types.check(classes);
    }

    @Test
    @DisplayName("only the catalogue module declares caches")
    void onlyTheCatalogueDeclaresCaches() {
        ArchRule rule = noMethods()
                .that().haveRawReturnType(CacheSpec.class)
                .should().beDeclaredInClassesThat().resideOutsideOfPackage(CATALOG)
                .because("a cache declared elsewhere would hold data from a module that may not "
                       + "cache (D-056)");

        rule.check(classes);
    }

    @Test
    @DisplayName("nothing outside platform.cache reads or writes a cache directly")
    void cachesAreReachedOnlyThroughTheAnnotations() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.familyfood.platform.cache..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.cache.CacheManager")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.cache.Cache")
                .orShould().dependOnClassesThat().resideInAPackage("org.springframework.data.redis..")
                .because("direct cache access would skip the fallback to the database and the "
                       + "circuit breaker, and escape the rules above (D-056)");

        rule.check(classes);
    }
}

package com.familyfood.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Module boundaries (D-021).
 *
 * <p>Two of these rules are not style preferences — they are the mechanism by which two
 * product promises become structural facts:
 *
 * <ul>
 *   <li>{@code scoring} cannot reach {@code ai}. This is what makes "AI never computes a
 *       verdict" true by construction rather than by discipline (D-011).
 *   <li>{@code recommendation} cannot reach any commercial concept. PANTRY's pre-launch
 *       checklist requires the <em>capability</em> for paid ranking to be absent from the
 *       codebase, not merely unused (D-012).
 * </ul>
 *
 * <p>A failure here fails the build. That is the intent.
 *
 * <p>Several rules carry {@code allowEmptyShould(true)} because their target module does
 * not exist yet. They are inert now and become live the moment the module lands — which
 * is the point at which they matter. {@link ModuleExistenceTest} asserts that the modules
 * actually arrive, so a rule cannot stay inert by accident.
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.familyfood");
    }

    // ---------------------------------------------------------------- the two that matter

    @Test
    @DisplayName("scoring must never depend on ai — no model output can become a verdict (D-011)")
    void scoringMustNotDependOnAi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..scoring..")
                .should().dependOnClassesThat().resideInAPackage("..ai..")
                .because("the deterministic engine computes; AI only explains what it "
                       + "already computed. A dependency here would make it possible for a "
                       + "model to influence a score, a threshold or a verdict (D-011).");
                // scoring exists (Phase 4, D-024) — this rule is now genuinely live, not inert.

        rule.check(classes);
    }

    @Test
    @DisplayName("recommendation must never depend on a commercial concept (D-012)")
    void recommendationMustNotDependOnCommercialConcepts() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..recommendation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..commerce..", "..partner..", "..sponsor..",
                        "..advertising..", "..billing..", "..payment..")
                .because("no code path may exist by which a commercial relationship alters "
                       + "a nutrition ranking. The capability must be absent, not merely "
                       + "unused (D-012).")
                // Inert until the recommendation module exists (Phase 4).
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("no class anywhere may reference a sponsorship or paid-placement concept")
    void noSponsorshipConceptsExistAtAll() {
        ArchRule rule = noClasses()
                .should().haveSimpleNameContaining("Sponsor")
                .orShould().haveSimpleNameContaining("PaidPlacement")
                .orShould().haveSimpleNameContaining("Advertisement")
                .orShould().haveSimpleNameContaining("PromotedProduct")
                .because("D-012 makes this a permanent architectural boundary, not a "
                       + "backlog item.")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    // ------------------------------------------------------------------- layering

    @Test
    @DisplayName("platform must not depend on any domain module")
    void platformMustNotDependOnDomainModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.familyfood.platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.familyfood.identity..",
                        "com.familyfood.household..",
                        "com.familyfood.consent..",
                        "com.familyfood.profile..",
                        "com.familyfood.catalog..",
                        "com.familyfood.search..",
                        "com.familyfood.capture..",
                        "com.familyfood.scoring..",
                        "com.familyfood.recommendation..",
                        "com.familyfood.activity..",
                        "com.familyfood.ai..")
                .because("platform is infrastructure shared by every module; a dependency "
                       + "downward would make it impossible to reason about startup order "
                       + "or to test a module in isolation.");

        rule.check(classes);
    }

    @Test
    @DisplayName("consent must not depend on the modules it gates")
    void consentMustNotDependOnGatedModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..consent..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..scoring..", "..recommendation..", "..capture..", "..activity..")
                .because("consent is checked by those modules, not the other way round. A "
                       + "cycle here would let a gated module influence its own gate.")
                // Inert until the consent module exists (Phase 2).
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("modules communicate through published interfaces, not each other's repositories")
    void repositoriesAreNotSharedAcrossModules() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Identity").definedBy("com.familyfood.identity..")
                // Deliberately excludes household.application: that package holds the one
                // published interface (HouseholdAccess, D-023) other modules are meant to
                // call. Only the truly private parts — persistence, domain, the HTTP
                // surface — are walled off here (D-024).
                .layer("Household").definedBy(
                        "com.familyfood.household.domain..",
                        "com.familyfood.household.infra..",
                        "com.familyfood.household.api..")
                .layer("Catalog").definedBy("com.familyfood.catalog..")
                .layer("Scoring").definedBy("com.familyfood.scoring..")

                // Catalog is a leaf: nothing it owns should reach back up into a person.
                .whereLayer("Household").mayNotBeAccessedByAnyLayer()
                .whereLayer("Identity").mayOnlyBeAccessedByLayers("Household")
                .as("module access")
                .because("a module reaching into another module's persistence layer makes "
                       + "the boundary fictional.");

        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    @DisplayName("scoring may reach household only through the published HouseholdAccess interface")
    void scoringMayOnlyReachHouseholdApplicationPackage() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..scoring..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..household.domain..", "..household.infra..", "..household.api..")
                .because("household's persistence and HTTP surface stay private; scoring "
                       + "(and any future consumer) reads membership facts only through "
                       + "household.application.HouseholdAccess (D-023, D-024).");
                // scoring exists (Phase 4) — this rule is now genuinely live, not inert.

        rule.check(classes);
    }

    // ----------------------------------------------------------------- hygiene

    @Test
    @DisplayName("controllers must not touch repositories directly")
    void controllersMustNotUseRepositories() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("business logic belongs in a service, not an HTTP adapter "
                       + "(Constitution §36).")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("domain code may not use java.util.Date or Calendar")
    void noLegacyDateApiInDomainCode() {
        // JwtService is the single documented exception: the jjwt API takes and returns
        // java.util.Date, so the conversion has to happen somewhere. Confining it to the
        // adapter is the point — it must not leak into a domain type.
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.familyfood.platform.security..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
                .because("java.time only. Mixed date APIs are a reliable source of "
                       + "timezone defects, and verdict reproducibility depends on time "
                       + "being unambiguous.")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("time comes from the injected Clock, not from a static call")
    void noStaticWallClockCalls() {
        ArchRule rule = noClasses()
                .should().callMethod(System.class, "currentTimeMillis")
                .because("time comes from the injected Clock so that scoring is "
                       + "reproducible and consent expiry is testable.")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}

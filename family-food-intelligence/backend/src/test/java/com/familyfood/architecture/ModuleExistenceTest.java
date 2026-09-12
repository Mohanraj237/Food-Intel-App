package com.familyfood.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against a module quietly disappearing — and against a boundary rule in
 * {@link ModuleBoundaryTest} staying inert because its target package was never built.
 *
 * <p>{@code DELIVERED_MODULES} is extended as each phase completes. A rule in
 * {@code ModuleBoundaryTest} that carries {@code allowEmptyShould(true)} is only
 * acceptable while its module is absent from this list; once the module is listed, the
 * rule is genuinely enforcing something and the allowance should be removed.
 */
class ModuleExistenceTest {

    /**
     * Modules that exist today. Add to this as phases land — never remove without a
     * decision-log entry.
     */
    private static final List<String> DELIVERED_MODULES = List.of(
            "com.familyfood.platform",   // Phase 1
            "com.familyfood.identity",   // Phase 1
            "com.familyfood.audit",      // Phase 1
            "com.familyfood.household",  // Phase 2
            "com.familyfood.profile",    // Phase 2
            "com.familyfood.catalog",    // Phase 3 (read foundation)
            "com.familyfood.search",     // Phase 3 (read foundation)
            "com.familyfood.consent",    // Phase 2 (domain, service and gate)
            "com.familyfood.scoring",    // Phase 4 (deterministic health score, personal/household fit)
            "com.familyfood.capture",    // Phase 3 (manual-entry contribution intake; OCR intake not yet built)
            "com.familyfood.datarights", // Phase 2 gate (data export, account/child erasure — NFR-09/CH-008/FR-010)
            "com.familyfood.recommendation", // Feature-completion sprint (D-013 alternatives, D-033)
            "com.familyfood.activity",      // Feature-completion sprint (FR-012 outcome capture)
            "com.familyfood.wellness",      // Wellness Phase 1 (D-034): observations, age gate, visibility
            "com.familyfood.nutrition",     // Wellness Phase 2 (D-039): meals, portions, apportioning
            "com.familyfood.goals",         // Wellness Phase 2: goals over the MetricCode taxonomy
            "com.familyfood.habits",        // Wellness Phase 2: habits and completions
            "com.familyfood.insights"       // Wellness Phase 2: deterministic derived statements
    );

    /**
     * Modules the plan commits to, with the phase that delivers them. Listed here so the
     * intent is visible in code, not only in a document.
     */
    private static final List<String> PLANNED_MODULES = List.of(
            "com.familyfood.ai"              // Phase 3
    );

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.familyfood");
    }

    @Test
    @DisplayName("every delivered module still contains classes")
    void deliveredModulesArePresent() {
        for (String module : DELIVERED_MODULES) {
            boolean present = classes.stream()
                    .anyMatch(c -> c.getPackageName().startsWith(module));

            assertThat(present)
                    .as("module %s has no classes — it was deleted or renamed without a "
                      + "decision-log entry", module)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the planned module list and the delivered list do not overlap")
    void plannedAndDeliveredAreDisjoint() {
        // A module that appears in both lists means someone delivered it and forgot to
        // move it, which in turn means a boundary rule may still be inert.
        assertThat(PLANNED_MODULES)
                .as("move a module out of PLANNED_MODULES once it is delivered, and remove "
                  + "the matching allowEmptyShould() in ModuleBoundaryTest")
                .doesNotContainAnyElementsOf(DELIVERED_MODULES);
    }
}

package com.sepanexus.settlement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/**
 * EPIC-35 Story 35.2: enforces the `[FREEZE]` in
 * sepa-nexus-message-flow-and-data-blueprint.md §4.11 — strategy is selected only by
 * {@code (SettlementBasis, LiquidityMode)}, never by profile/CSM name — as an architectural
 * invariant on the settlement-selection API boundary, not as a parser of arbitrary {@code
 * if}/{@code switch} statements (ArchUnit cannot read control-flow semantics or string literals
 * inside a method body). Three checks, each proven non-vacuous against fixtures under {@code
 * com.sepanexus.architecturefixtures.settlementselection} before being trusted against production:
 * <ol>
 *   <li>no public method in the selection boundary accepts {@code String}/{@code CharSequence};</li>
 *   <li>the settlement module never depends on a profile-model type (routing/reference-data
 *       profile classes, simulated here since neither module exists in main sources yet);</li>
 *   <li>no class in the settlement module is named after a CSM/profile ({@code Tips}/{@code Rt1}/
 *       {@code Step2}/{@code Stet}/{@code Kir}/{@code Elixir}{@code *SettlementEngine}).</li>
 * </ol>
 */
class NoProfileNameSwitchTest {

    private static final JavaClasses PRODUCTION_SETTLEMENT = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.sepanexus.settlement");

    private static final JavaClasses FORBIDDEN_FIXTURES = new ClassFileImporter()
            .importPackages("com.sepanexus.architecturefixtures.settlementselection.forbidden",
                    "com.sepanexus.architecturefixtures.settlementselection.testprofile");

    private static final JavaClasses ALLOWED_FIXTURES = new ClassFileImporter()
            .importPackages("com.sepanexus.architecturefixtures.settlementselection.allowed");

    // -- 1. selection boundary must not accept String/CharSequence -----------------------------

    @Test
    void forbiddenFixtureSelectorsAcceptingStringOrCharSequenceAreDetected() {
        assertRuleViolated(noStringOrCharSequenceParameterRule("..architecturefixtures.settlementselection.forbidden.."),
                FORBIDDEN_FIXTURES);
    }

    @Test
    void allowedFixtureTypedPairSelectorAcceptsNoStringOrCharSequence() {
        noStringOrCharSequenceParameterRule("..architecturefixtures.settlementselection.allowed..")
                .check(ALLOWED_FIXTURES);
    }

    @Test
    void productionSelectionBoundaryAcceptsNoStringOrCharSequence() {
        noStringOrCharSequenceParameterRule("com.sepanexus.settlement..")
                .check(PRODUCTION_SETTLEMENT);
    }

    // -- 2. no dependency on a profile-model type ------------------------------------------------

    @Test
    void forbiddenFixtureDependencyOnProfileModelIsDetected() {
        assertRuleViolated(
                noDependencyOnProfileModelRule("..architecturefixtures.settlementselection.forbidden.."),
                FORBIDDEN_FIXTURES);
    }

    @Test
    void productionSettlementHasNoDependencyOnProfileModel() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sepanexus.settlement..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..architecturefixtures.settlementselection.testprofile..",
                        "..routing..", "..referencedata..", "..reference_data..");
        rule.check(PRODUCTION_SETTLEMENT);
    }

    // -- 3. no per-CSM class names ----------------------------------------------------------------

    @Test
    void forbiddenFixtureCsmNamedClassIsDetected() {
        assertRuleViolated(noCsmNamedClassesRule("..architecturefixtures.settlementselection.forbidden.."),
                FORBIDDEN_FIXTURES);
    }

    @Test
    void productionSettlementHasNoCsmNamedClasses() {
        noCsmNamedClassesRule("com.sepanexus.settlement..").check(PRODUCTION_SETTLEMENT);
    }

    // -- rule builders -----------------------------------------------------------------------------

    private static ArchRule noStringOrCharSequenceParameterRule(String packageIdentifier) {
        return methods()
                .that().areDeclaredInClassesThat().resideInAPackage(packageIdentifier)
                .and().areDeclaredInClassesThat().haveNameMatching(".*(Resolver|Selector)")
                .and().arePublic()
                .should(new ArchCondition<JavaMethod>("not accept String or CharSequence as a selection parameter") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        boolean violates = method.getRawParameterTypes().stream()
                                .anyMatch(type -> type.isEquivalentTo(String.class) || type.isEquivalentTo(CharSequence.class));
                        if (violates) {
                            events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " accepts String/CharSequence — strategy selection must be by typed (SettlementBasis, LiquidityMode) only"));
                        }
                    }
                });
    }

    private static ArchRule noDependencyOnProfileModelRule(String packageIdentifier) {
        return noClasses()
                .that().resideInAPackage(packageIdentifier)
                .should().dependOnClassesThat().resideInAPackage("..architecturefixtures.settlementselection.testprofile..");
    }

    private static ArchRule noCsmNamedClassesRule(String packageIdentifier) {
        return noClasses()
                .that().resideInAPackage(packageIdentifier)
                .should().haveNameMatching(".*(Tips|Rt1|Step2|Stet|Kir|Elixir).*SettlementEngine.*")
                .orShould().haveNameMatching(".*(Tips|Rt1|Step2|Stet|Kir|Elixir)SettlementEngine.*");
    }

    /**
     * Confirms the rule is non-vacuous: it must actually report a genuine architecture violation —
     * not merely throw because zero classes matched the {@code that()} clause (a missing/misnamed
     * fixture would otherwise vacuously "pass" this check, since ArchUnit reports both cases as an
     * {@link AssertionError}).
     */
    private static void assertRuleViolated(ArchRule rule, JavaClasses classes) {
        try {
            rule.check(classes);
            throw new AssertionError("Expected rule to be violated by the forbidden fixture, but it passed: " + rule);
        } catch (AssertionError caught) {
            String message = caught.getMessage() == null ? "" : caught.getMessage();
            if (message.startsWith("Expected rule to be violated")) {
                throw caught;
            }
            if (message.contains("failed to check any classes")) {
                throw new AssertionError(
                        "Rule matched zero classes — fixture is missing or misnamed, this is not a genuine violation: "
                                + message, caught);
            }
            if (!message.contains("Architecture Violation")) {
                throw new AssertionError("Caught an AssertionError that is not an architecture violation: " + message,
                        caught);
            }
            // A genuine, non-vacuous architecture violation was reported — this is the expected outcome.
        }
    }
}

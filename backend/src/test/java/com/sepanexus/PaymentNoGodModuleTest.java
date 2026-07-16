package com.sepanexus;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/**
 * EPIC-11 Story 11.3 (OWN-3): {@code payment-lifecycle} never depends directly on the internal
 * implementation of {@code settlement}, {@code routing}, or {@code egress} — root {@code AGENTS.md}'s
 * frozen rule "payment-lifecycle nie zapisuje bezpośrednio do: settlement; routing; egress". The rule
 * is package-boundary based (a module's {@code .internal} subpackage is its forbidden implementation
 * detail, its root package is its public port/contract — the same convention already established by
 * the real {@code signature} module, see {@link com.sepanexus.signature.SignatureVerificationPort} vs {@code
 * com.sepanexus.signature.internal}), not a class-name match, so it survives a class rename.
 *
 * <p>None of {@code settlement}/{@code routing}/{@code egress} exist in this codebase yet (confirmed:
 * {@code find backend/src/main/java/com/sepanexus/modules -maxdepth 4 -type d} lists only {@code
 * paymentlifecycle}; same finding as {@code epic10.IsoAdapterNoBusinessDecisionTest}'s own comment for
 * {@code routing}/{@code settlement}/{@code ledger}/{@code egress}). Checking the rule against
 * production code alone would therefore be vacuously true — it would still pass even if the rule
 * itself were broken or absent. The {@code architecturefixtures.*} test-only packages below simulate
 * a forbidden dependency and an allowed public-port dependency so the exact same {@link ArchRule} is
 * proven to both catch a real violation and not reject a legitimate integration.
 */
class PaymentNoGodModuleTest {

    private static ArchRule paymentLifecycleNoGodModuleRule() {
        return noClasses()
                .that().resideInAPackage("..paymentlifecycle..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..settlement.internal..", "..routing.internal..", "..egress.internal..")
                .because("payment-lifecycle owns its own FSM only; settlement/routing/egress internals "
                        + "are reached only through their public port, never directly");
    }

    @Test
    void productionPaymentLifecycleDoesNotDependOnSettlementRoutingOrEgressImplementation() {
        // Production code only (backend/src/main/java) — test fixtures/architecturefixtures must
        // never be scanned as if they were production code.
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sepanexus");

        paymentLifecycleNoGodModuleRule().check(productionClasses);
    }

    @Test
    void ruleCatchesForbiddenDependencyOnSettlementImplementation() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages(
                        "com.sepanexus.architecturefixtures.paymentlifecycle.forbidden",
                        "com.sepanexus.architecturefixtures.settlement.internal");

        EvaluationResult result = paymentLifecycleNoGodModuleRule().evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        String details = result.getFailureReport().getDetails().toString();
        assertThat(details).contains("ForbiddenSettlementCaller");
        assertThat(details).contains("InternalSettlementRepository");
    }

    @Test
    void ruleCatchesForbiddenDependencyOnRoutingImplementation() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages(
                        "com.sepanexus.architecturefixtures.paymentlifecycle.forbidden",
                        "com.sepanexus.architecturefixtures.routing.internal");

        EvaluationResult result = paymentLifecycleNoGodModuleRule().evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        String details = result.getFailureReport().getDetails().toString();
        assertThat(details).contains("ForbiddenRoutingCaller");
        assertThat(details).contains("InternalRoutingRepository");
    }

    @Test
    void ruleCatchesForbiddenDependencyOnEgressImplementation() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages(
                        "com.sepanexus.architecturefixtures.paymentlifecycle.forbidden",
                        "com.sepanexus.architecturefixtures.egress.internal");

        EvaluationResult result = paymentLifecycleNoGodModuleRule().evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        String details = result.getFailureReport().getDetails().toString();
        assertThat(details).contains("ForbiddenEgressCaller");
        assertThat(details).contains("InternalEgressRepository");
    }

    @Test
    void ruleAllowsDependencyOnPublicSettlementPort() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages(
                        "com.sepanexus.architecturefixtures.paymentlifecycle.allowed",
                        "com.sepanexus.architecturefixtures.settlement");

        EvaluationResult result = paymentLifecycleNoGodModuleRule().evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isFalse();
    }
}

package com.sepanexus.epic10;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * EPIC-10 Story 10.3: {@code iso-adapter} (today: {@code com.sepanexus.modules.paymentlifecycle.isoadapter}
 * — not yet a separate Modulith module/DB role, see {@code [OPEN-QUESTION]} in
 * {@code planning/epics/EPIC-10-iso-lineage-ownership.md} for why Story 10.1 stays blocked) never
 * makes a business decision: it maps, extracts identifiers, records lineage/parse evidence, and
 * answers correlation reads, but it never changes {@code payment.status}. Calling
 * {@code PaymentEntity.markValidated()} necessarily requires depending on the {@code PaymentEntity}
 * class first, so forbidding that one dependency also forbids the method call — there is no other
 * way to reach it. {@code PaymentTransitionTable} (the FSM rule table) and {@code PaymentRepository}
 * (the only writer of {@code payment.payments}) are forbidden for the same reason: touching either
 * is itself already a business/status decision, whether or not a mutation actually follows.
 *
 * <p>{@code routing}/{@code settlement}/{@code ledger}/{@code egress} are not covered by a dependency
 * rule here — those packages do not exist in this codebase yet (matches the same reasoning as
 * `planning/epics/EPIC-09-ownership-schema-grants.md` Story 9.4's {@code [PLANNING-DEFECT]}: a rule
 * against a non-existent package would be either vacuously true or an invention of architecture that
 * doesn't exist. This test file is the place a future rule belongs once those modules exist.
 */
@org.junit.jupiter.api.Tag("fast")
class IsoAdapterNoBusinessDecisionTest {

    // Production code only — test fixtures legitimately construct/read PaymentEntity for setup
    // (e.g. reading a generated id), which is not a business decision and would otherwise be a
    // false positive against this rule.
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.sepanexus");

    @Test
    void isoAdapterNeverDependsOnPaymentEntity() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..paymentlifecycle.isoadapter..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity");
        rule.check(CLASSES);
    }

    @Test
    void isoAdapterNeverDependsOnPaymentTransitionTable() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..paymentlifecycle.isoadapter..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.sepanexus.modules.paymentlifecycle.domain.PaymentTransitionTable");
        rule.check(CLASSES);
    }

    @Test
    void isoAdapterNeverDependsOnPaymentRepository() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..paymentlifecycle.isoadapter..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository");
        rule.check(CLASSES);
    }
}

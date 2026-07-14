package com.sepanexus;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * EPIC-09 Story 9.4: the subset of the ownership blueprint's §3.6.5 ArchUnit rules that
 * are meaningful against the codebase as it exists today (one real module,
 * payment-lifecycle, plus the security cross-cutting concern). Rules that reference
 * infrastructure not yet built in Iteration 0 (ClockPort, a GraphQL module, the
 * signature module, ledger, outbox_dispatcher_role, the approval workflow) are
 * deliberately not implemented here — see the `[PLANNING-DEFECT]` note in
 * planning/epics/EPIC-09-ownership-schema-grants.md rather than inventing that
 * architecture speculatively.
 */
class OwnershipArchRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.sepanexus");

    @Test
    void repositoriesLiveOnlyInARepositoryPackage() {
        ArchRule rule = classes()
                .that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .and().areInterfaces()
                .should().resideInAPackage("..repository..");
        rule.check(CLASSES);
    }

    @Test
    void controllersNeverReferenceARepositoryType() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat()
                .areAssignableTo(org.springframework.data.repository.Repository.class);
        rule.check(CLASSES);
    }

    @Test
    void noHibernateTenantIdOrTenantFilterAnywhereInTheEntityModel() {
        ArchRule noTenantIdField = noFields()
                .should().beAnnotatedWith("org.hibernate.annotations.TenantId");
        noTenantIdField.check(CLASSES);

        ArchRule noTenantFilterClass = noClasses()
                .should().beAnnotatedWith("org.hibernate.annotations.FilterDef")
                .orShould().beAnnotatedWith("org.hibernate.annotations.Filter");
        noTenantFilterClass.check(CLASSES);
    }
}

package com.sepanexus.graphql;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.sepanexus.modules.ApprovalQueueQuery;
import com.sepanexus.modules.PaymentIsoEvidenceQuery;
import com.sepanexus.evidenceaudit.AuditQueryPort;
import com.sepanexus.modules.paymentlifecycle.service.ApprovalDecisionService;
import org.junit.jupiter.api.Test;

/** Keeps the transport adapter unable to obtain data or invoke commands except through its public port. */
class GraphQLReadModelOwnershipTest {

    @Test
    void transportDoesNotDependOnRepositoriesOrCommandServices() {
        var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sepanexus");
        noClasses().that().resideInAPackage("com.sepanexus.graphql..")
                .should().dependOnClassesThat().resideInAnyPackage("..repository..")
                .because("GraphQL may call only explicit source-owned public query ports")
                .check(production);
        noClasses().that().resideInAPackage("com.sepanexus.graphql..")
                .should().dependOnClassesThat().areAssignableTo(ApprovalDecisionService.class)
                .because("approval commands remain REST-only")
                .check(production);
    }

    @Test
    void transportDependsOnTheExplicitApprovalQueryPort() {
        var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sepanexus.graphql", "com.sepanexus.modules");
        assertThat(production.get("com.sepanexus.graphql.ApprovalGraphQlController")
                .getDirectDependenciesFromSelf().stream().map(dependency -> dependency.getTargetClass().getName()))
                .contains(ApprovalQueueQuery.class.getName());
    }

    @Test
    void transportDependsOnTheExplicitAuditQueryPortRatherThanAuditInfrastructure() {
        var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sepanexus.graphql", "com.sepanexus.evidenceaudit");
        assertThat(production.get("com.sepanexus.graphql.ApprovalGraphQlController")
                .getDirectDependenciesFromSelf().stream().map(dependency -> dependency.getTargetClass().getName()))
                .contains(AuditQueryPort.class.getName());
    }

    @Test
    void transportDependsOnTheExplicitIsoEvidencePortRatherThanIsoInfrastructure() {
        var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sepanexus.graphql", "com.sepanexus.modules");
        assertThat(production.get("com.sepanexus.graphql.ApprovalGraphQlController")
                .getDirectDependenciesFromSelf().stream().map(dependency -> dependency.getTargetClass().getName()))
                .contains(PaymentIsoEvidenceQuery.class.getName());
    }
}

package com.sepanexus.signature;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * EPIC-31 Story 31.1 (OWN-10 S2): {@code JdbcKeyRegistryStore} and any future signature internals
 * live in {@code com.sepanexus.signature.internal} — no class outside the {@code signature} module
 * may reference them directly; every caller goes through {@link SignaturePort}/{@link
 * KeyRegistryPort} instead.
 */
class SignatureNoForeignRepoAccessTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.sepanexus");

    @Test
    void noClassOutsideSignatureModuleReferencesSignatureInternals() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.sepanexus.signature..")
                .should().dependOnClassesThat().resideInAPackage("com.sepanexus.signature.internal..");
        rule.check(CLASSES);
    }
}

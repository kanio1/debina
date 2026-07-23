package com.sepanexus;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * EPIC-22 Story 22.3: zero direct calls to the system clock outside {@code ClockPort}
 * (sepa-nexus-message-flow-and-data-blueprint.md §3.3/§8 EPIC-XCUT-1, G8) — critical for
 * deterministic simulation replay in Iteration 3. Scoped to production code only: test fixtures
 * legitimately construct objects with an arbitrary/"now" timestamp for setup and are not part of
 * this rule's source (§14.3 — no false positives in tests).
 */
@org.junit.jupiter.api.Tag("fast")
class ClockPortEnforcementTest {

    private static final Set<String> SYSTEM_CLOCK_OWNERS = Set.of(
            "java.time.Instant", "java.time.LocalDateTime", "java.time.LocalDate",
            "java.time.OffsetDateTime", "java.time.ZonedDateTime");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.sepanexus");

    @Test
    void noProductionClassOutsideSystemClockPortCallsTheSystemClockDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sepanexus..")
                .and().doNotHaveFullyQualifiedName("com.sepanexus.shared.SystemClockPort")
                .should(callTheSystemClockDirectly());
        rule.check(CLASSES);
    }

    private static ArchCondition<JavaClass> callTheSystemClockDirectly() {
        return new ArchCondition<>("call Instant.now()/LocalDateTime.now()/etc. directly") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    boolean isNowCall = "now".equals(call.getTarget().getName())
                            && SYSTEM_CLOCK_OWNERS.contains(call.getTargetOwner().getFullName());
                    events.add(new SimpleConditionEvent(item, isNowCall,
                            call.getDescription() + " calls " + call.getTargetOwner().getFullName() + ".now()"));
                }
            }
        };
    }
}

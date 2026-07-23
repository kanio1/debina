package com.sepanexus.evidenceaudit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Non-vacuous public-boundary check for the evidence-audit application module. */
@org.junit.jupiter.api.Tag("fast")
class CommandAuditArchitectureTest {

    @Test
    void modulithAcceptsEvidenceAuditAndItsPublicApiDoesNotLeakInfrastructure() {
        ApplicationModules.of(SepaNexusApplication.class).verify();

        assertThat(Modifier.isPublic(CommandAuditPort.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(CommandAuditEntry.class.getModifiers())).isTrue();
        assertThat(CommandAuditPort.class.getMethods()).allSatisfy(method -> {
            assertThat(method.getReturnType().getPackageName()).doesNotContain(".internal");
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType.getPackageName()).doesNotContain(".internal");
            }
        });
    }
}

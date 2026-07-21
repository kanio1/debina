package com.sepanexus.evidenceaudit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Wave 10 RED boundary: audit reads must be public typed ports, never repository leakage. */
class AuditQueryArchitectureTest {

    @Test
    void queryBoundaryIsPublicAndEvidenceAuditKeepsItsInfrastructurePrivate() throws Exception {
        ApplicationModules.of(SepaNexusApplication.class).verify();

        Class<?> port = Class.forName("com.sepanexus.evidenceaudit.AuditQueryPort");
        assertThat(Modifier.isPublic(port.getModifiers())).isTrue();
        assertThat(port.getMethods()).allSatisfy(method -> {
            assertThat(method.getReturnType().getPackageName()).doesNotContain(".internal");
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType.getPackageName()).doesNotContain(".internal");
            }
        });
    }
}

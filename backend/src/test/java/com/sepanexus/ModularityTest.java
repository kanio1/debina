package com.sepanexus;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@org.junit.jupiter.api.Tag("fast")
class ModularityTest {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(SepaNexusApplication.class).verify();
    }
}

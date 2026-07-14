package com.sepanexus;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(SepaNexusApplication.class).verify();
    }
}

package dev.iamkavindu.reconflow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationArchitectureTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(ReconFlowApplication.class).verify();
    }
}

package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Phase3R3SecurityRuntimeTest {
    @Test
    void everyRuntimeAndExternalGateMustPassBeforeProductionReadiness() {
        var notReady = Phase3ReleaseReadinessController.calculate(
                true, true, false, true, true, true, true, true, true);
        assertThat(notReady.productionReady()).isFalse();
        assertThat(notReady.gates()).anyMatch(gate -> gate.gateId().equals("ADMIN_UI") && !gate.status().equals("PASSED"));

        var ready = Phase3ReleaseReadinessController.calculate(
                true, true, true, true, true, true, true, true, true);
        assertThat(ready.productionReady()).isTrue();
    }
}

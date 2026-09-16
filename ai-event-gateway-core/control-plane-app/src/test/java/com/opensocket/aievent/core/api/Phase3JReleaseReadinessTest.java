package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Phase3JReleaseReadinessTest {
    @Test
    void failClosedWhenAnyGateIsMissing() {
        var value = Phase3ReleaseReadinessController.calculate(false, false, false, false, false, false, false, false, false);
        assertThat(value.productionReady()).isFalse();
        assertThat(value.status()).isEqualTo("NOT_READY");
        assertThat(value.gates()).allMatch(gate -> gate.gateId().equals("SOURCE") || gate.status().equals("NOT_CERTIFIED"));
    }

    @Test
    void everyBlockingGateMustPassBeforeProductionReady() {
        assertThat(Phase3ReleaseReadinessController.calculate(true, true, true, true, true, true, true, false, true).productionReady()).isFalse();
        assertThat(Phase3ReleaseReadinessController.calculate(true, true, true, true, true, true, true, true, false).productionReady()).isFalse();
        assertThat(Phase3ReleaseReadinessController.calculate(true, true, true, true, true, true, true, true, true).productionReady()).isTrue();
    }
}

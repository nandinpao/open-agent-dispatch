package com.opensocket.aievent.worker.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class V41C3R3GHF2WorkerRuntimeConfigurationProductionGuardTest {
    @Test
    void prdRejectsDisabledRuntimeConfigurationPlane() {
        WorkerRuntimeConfigurationProperties properties = hardened();
        properties.setEnabled(false);
        WorkerRuntimeConfigurationProductionGuard guard = new WorkerRuntimeConfigurationProductionGuard(properties, "PRD");
        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void prdRejectsNonFailClosedColdStart() {
        WorkerRuntimeConfigurationProperties properties = hardened();
        properties.setFailClosedOnColdStart(false);
        WorkerRuntimeConfigurationProductionGuard guard = new WorkerRuntimeConfigurationProductionGuard(properties, "PRD");
        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void prdAcceptsHardenedRuntimeConfigurationPlane() {
        WorkerRuntimeConfigurationProductionGuard guard = new WorkerRuntimeConfigurationProductionGuard(hardened(), "PRD");
        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void nonPrdDoesNotForceRemoteAuthorityPlane() {
        WorkerRuntimeConfigurationProperties properties = new WorkerRuntimeConfigurationProperties();
        WorkerRuntimeConfigurationProductionGuard guard = new WorkerRuntimeConfigurationProductionGuard(properties, "LOCAL");
        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments(new String[0])));
    }

    private static WorkerRuntimeConfigurationProperties hardened() {
        WorkerRuntimeConfigurationProperties properties = new WorkerRuntimeConfigurationProperties();
        properties.setEnabled(true);
        properties.setFailClosedOnColdStart(true);
        properties.setHmacKey("0123456789abcdef0123456789abcdef");
        return properties;
    }
}

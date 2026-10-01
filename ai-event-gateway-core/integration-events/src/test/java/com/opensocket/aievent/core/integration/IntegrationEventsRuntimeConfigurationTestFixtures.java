package com.opensocket.aievent.core.integration;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;

import tools.jackson.databind.ObjectMapper;

/** Test fixture for exercising integration-event consumers through the production typed runtime view. */
final class IntegrationEventsRuntimeConfigurationTestFixtures {
    private IntegrationEventsRuntimeConfigurationTestFixtures() {
    }

    static IntegrationEventsRuntimeConfigurationView startupBacked(
            IntegrationEventProperties startup,
            ObjectMapper mapper) {
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(
                new RuntimeConfigurationLocalSnapshotRegistry(), mapper);
        return new IntegrationEventsRuntimeConfigurationView(
                values,
                new RuntimeConfigurationAuthorityRegistry(),
                startup);
    }
}

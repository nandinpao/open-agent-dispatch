package com.opensocket.aievent.core.outbox;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;

import tools.jackson.databind.ObjectMapper;

/** Test fixture for exercising outbox consumers through the production typed runtime view. */
final class OutboxRuntimeConfigurationTestFixtures {
    private OutboxRuntimeConfigurationTestFixtures() {
    }

    static OutboxRuntimeConfigurationView startupBacked(OutboxProperties startup, ObjectMapper mapper) {
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(
                new RuntimeConfigurationLocalSnapshotRegistry(), mapper);
        return new OutboxRuntimeConfigurationView(
                values,
                new RuntimeConfigurationAuthorityRegistry(),
                startup);
    }
}

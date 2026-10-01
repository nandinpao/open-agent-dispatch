package com.opensocket.aievent.core.kernel.configuration.revision;

import java.time.OffsetDateTime;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;

/** Revision aggregate and optimistic-concurrency boundary. */
public record RuntimeConfigurationConfigSet(
        String configSetId,
        String setKey,
        OpenDispatchEnvironment environment,
        ConfigurationScope scope,
        String scopeRef,
        String ownerComponent,
        String status,
        long resourceVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}

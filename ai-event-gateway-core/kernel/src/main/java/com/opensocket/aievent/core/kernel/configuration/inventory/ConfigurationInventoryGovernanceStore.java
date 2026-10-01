package com.opensocket.aievent.core.kernel.configuration.inventory;

import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import java.util.List;
import java.util.Optional;

/** Persistence port for source observations and human governance state. */
public interface ConfigurationInventoryGovernanceStore {
    List<ConfigurationInventoryObservation> listObservations(ConfigurationInventoryGovernanceStatus status, String namespace, String query, int limit);
    Optional<ConfigurationInventoryObservation> findObservation(String key);
    Optional<ConfigurationInventoryGovernance> findGovernance(String key);
    List<ConfigurationInventoryGovernanceEvent> listEvents(String key, int limit);

    ConfigurationInventoryGovernance save(
        ConfigurationInventoryGovernance next,
        long expectedVersion,
        String eventType,
        String actor,
        String reason,
        String detailJson);
}

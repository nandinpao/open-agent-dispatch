package com.opensocket.aievent.core.configuration.distribution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;

/**
 * Projects required-node presence into STALE apply state without inventing heartbeats from
 * control-plane publication. A future topology provider may manage required targets; this
 * reconciler only evaluates their observed presence.
 */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationNodePresenceReconciler {
    private final RuntimeConfigurationDistributionStore store;
    private final RuntimeConfigurationDistributionProperties properties;
    private final String environment;

    public RuntimeConfigurationNodePresenceReconciler(RuntimeConfigurationDistributionStore store,
            RuntimeConfigurationDistributionProperties properties,
            @Value("${opendispatch.environment}") String environment) {
        this.store=store;
        this.properties=properties;
        this.environment=OpenDispatchEnvironment.parseCanonical(environment).name();
    }

    @Scheduled(fixedDelayString="${opendispatch.runtime-configuration.distribution.node-stale-scan-ms:30000}",scheduler="reconciliationOperationalScheduler")
    public void reconcilePresence() {
        store.markStaleRequiredNodes(environment,properties.nodeStaleAfter());
    }
}

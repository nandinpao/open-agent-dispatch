package com.opensocket.aievent.core.configuration.distribution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;

/** Periodic Core reconciliation is the durable healing path; Redis/pub-sub is only acceleration. */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationCoreReconciler {
    private final RuntimeConfigurationDistributionStore store;
    private final RuntimeConfigurationSnapshotService snapshots;
    private final RuntimeConfigurationCoreSnapshotApplier applier;
    private final String environment;

    public RuntimeConfigurationCoreReconciler(RuntimeConfigurationDistributionStore store,RuntimeConfigurationSnapshotService snapshots,
            RuntimeConfigurationCoreSnapshotApplier applier,@Value("${opendispatch.environment}") String environment) {
        this.store=store; this.snapshots=snapshots; this.applier=applier;
        this.environment=OpenDispatchEnvironment.parseCanonical(environment).name();
    }

    @Scheduled(fixedDelayString="${opendispatch.runtime-configuration.distribution.reconcile-ms:5000}",scheduler="reconciliationOperationalScheduler")
    public void reconcile() {
        for(String configSetId:store.listActiveConfigSetIds(environment)) {
            try {
                var desired=snapshots.desiredSnapshot(configSetId,"CORE",2);
                var current=applier.registry().current(configSetId).orElse(null);
                if(current==null || !sameEffectiveSnapshot(desired,current)) applier.apply(desired);
            } catch(RuntimeException ignored) {
                // apply path persists FAILED evidence; next cycle retries from PostgreSQL authority.
            }
        }
    }

    static boolean sameEffectiveSnapshot(com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope desired,
            com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope current) {
        return desired.revisionId().equals(current.revisionId())
                && desired.payloadHash().equalsIgnoreCase(current.payloadHash())
                && desired.authorityMode().equals(current.authorityMode())
                && desired.requiredKeys().equals(current.requiredKeys());
    }
}


package com.opensocket.aievent.core.configuration.distribution;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;

/** Claims durable outbox work and publishes only the still-active desired revision. */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationDistributor {
    private final RuntimeConfigurationDistributionStore distribution;
    private final RuntimeConfigurationRevisionStore revisions;
    private final RuntimeConfigurationSnapshotService snapshots;
    private final RuntimeConfigurationRedisPublisher redis;
    private final RuntimeConfigurationCoreSnapshotApplier coreApplier;
    private final RuntimeConfigurationDistributionProperties properties;

    public RuntimeConfigurationDistributor(RuntimeConfigurationDistributionStore distribution,RuntimeConfigurationRevisionStore revisions,
            RuntimeConfigurationSnapshotService snapshots,RuntimeConfigurationRedisPublisher redis,
            RuntimeConfigurationCoreSnapshotApplier coreApplier,RuntimeConfigurationDistributionProperties properties) {
        this.distribution=distribution; this.revisions=revisions; this.snapshots=snapshots; this.redis=redis; this.coreApplier=coreApplier; this.properties=properties;
    }

    @Scheduled(fixedDelayString="${opendispatch.runtime-configuration.distribution.poll-ms:2000}",scheduler="reconciliationOperationalScheduler")
    public void run() {
        for(var entry:claim()) {
            try {
                String active=revisions.findActiveRevisionId(entry.configSetId()).orElse(null);
                if(!entry.revisionId().equals(active)) { distribution.markSuperseded(entry.configSetId(),entry.revisionId(),properties.workerId()); continue; }
                RuntimeConfigurationSnapshotEnvelope envelope=snapshots.snapshot(entry.configSetId(),entry.revisionId(),"ALL_NODES");
                // PostgreSQL authority -> Core correctness must not depend on Redis availability.
                // Apply locally first; Redis is only cache/pub-sub acceleration for remote nodes.
                coreApplier.apply(envelope);
                redis.publish(envelope);
                distribution.markDistributed(entry.configSetId(),entry.revisionId(),properties.workerId(),envelope.snapshotFingerprint());
            } catch(RuntimeException ex) {
                safeFail(entry.configSetId(),entry.revisionId(),ex);
            }
        }
    }

    protected java.util.List<com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionOutboxEntry> claim() {
        return distribution.claimDue(properties.workerId(),properties.batchSize(),properties.lease());
    }

    private void safeFail(String setId,String revisionId,RuntimeException ex) {
        try{distribution.markFailed(setId,revisionId,properties.workerId(),OffsetDateTime.now(ZoneOffset.UTC).plus(properties.retry()),ex.getMessage());}
        catch(RuntimeException ignored){/* original claim may already have been finalized; reconciliation is durable */}
    }
}

package com.opensocket.aievent.core.configuration;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expires V40-7 TTL overlays and redistributes the latest normal revision without rewriting history. */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationEmergencyOverrideReconciler {
    private final RuntimeConfigurationGovernanceStore governance;
    private final RuntimeConfigurationRevisionStore revisions;
    private final RuntimeConfigurationDistributionStore distribution;

    public RuntimeConfigurationEmergencyOverrideReconciler(RuntimeConfigurationGovernanceStore governance,
            RuntimeConfigurationRevisionStore revisions,RuntimeConfigurationDistributionStore distribution){
        this.governance=governance;this.revisions=revisions;this.distribution=distribution;
    }

    @Scheduled(fixedDelayString="${opendispatch.runtime-configuration.governance.override-reconcile-ms:30000}",scheduler="reconciliationOperationalScheduler")
    @Transactional
    public void expireDue(){
        for(var expired:governance.expireDueEmergencyOverrides(100,"runtime-config-emergency-expiry")){
            revisions.findActiveRevisionId(expired.configSetId()).ifPresent(active->
                distribution.requestRedistribution(expired.configSetId(),active,"Emergency override expired: "+expired.overrideId()));
        }
    }
}

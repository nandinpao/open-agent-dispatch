package com.opensocket.aievent.core.configuration.distribution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;

/**
 * Materializes deployment topology into the convergence denominator independently from node ACKs.
 * The deployment file declares expected nodes; this reconciler keeps newly-created Config Sets aligned
 * so a missing Gateway/Worker is visible as NOT_SEEN rather than disappearing from convergence.
 */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationTopologyReconciler implements ApplicationRunner {
    private final RuntimeConfigurationDistributionStore store;
    private final RuntimeConfigurationDistributionProperties properties;
    private final String environment;

    public RuntimeConfigurationTopologyReconciler(RuntimeConfigurationDistributionStore store,
            RuntimeConfigurationDistributionProperties properties,@Value("${opendispatch.environment}") String environment) {
        this.store=store; this.properties=properties;
        this.environment=OpenDispatchEnvironment.parseCanonical(environment).name();
    }

    @Override public void run(ApplicationArguments args){reconcileTopology();}

    @Scheduled(fixedDelayString="${opendispatch.runtime-configuration.distribution.topology-reconcile-ms:10000}",
            scheduler="reconciliationOperationalScheduler")
    public void reconcileTopology(){
        var targets=properties.requiredTargets();
        if(targets.isEmpty()) return;
        for(String configSetId:store.listActiveConfigSetIds(environment)){
            for(var target:targets){
                store.registerRequiredTarget(configSetId,target.nodeId(),target.nodeRole(),target.nodeInstanceId(),"DEPLOYMENT_TOPOLOGY");
            }
        }
    }
}

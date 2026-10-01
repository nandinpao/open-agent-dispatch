package com.opensocket.aievent.worker.configuration;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix="adapter-worker.runtime-configuration",name="enabled",havingValue="true")
public class WorkerRuntimeConfigurationColdStartGuard implements ApplicationRunner {
    private final WorkerRuntimeConfigurationProperties properties;private final WorkerRuntimeConfigurationReconciler reconciler;
    public WorkerRuntimeConfigurationColdStartGuard(WorkerRuntimeConfigurationProperties properties,WorkerRuntimeConfigurationReconciler reconciler){this.properties=properties;this.reconciler=reconciler;}
    @Override public void run(ApplicationArguments args){
        if(!properties.failClosedOnColdStart())return;
        reconciler.restoreLastKnownGood();
        List<String> ids;
        try{ids=reconciler.activeConfigSetIds();}
        catch(RuntimeException discoveryFailure){ids=reconciler.registry().currentConfigSetIds().stream().sorted().toList();if(ids.isEmpty())throw new IllegalStateException("RUNTIME_CONFIGURATION_COLD_START_DISCOVERY_FAILED",discoveryFailure);}
        for(String id:ids){reconciler.reconcileOne(id);if(!reconciler.usable(id))throw new IllegalStateException("RUNTIME_CONFIGURATION_COLD_START_FAILED configSetId="+id);}
    }
}

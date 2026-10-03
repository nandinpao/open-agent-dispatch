package com.opensocket.aievent.worker.configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Worker-side cold-start authority guard with authenticated LKG recovery and bounded Core readiness wait. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(prefix="adapter-worker.runtime-configuration",name="enabled",havingValue="true")
public class WorkerRuntimeConfigurationColdStartGuard implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(WorkerRuntimeConfigurationColdStartGuard.class);
    private final WorkerRuntimeConfigurationProperties properties;
    private final WorkerRuntimeConfigurationReconciler reconciler;

    public WorkerRuntimeConfigurationColdStartGuard(WorkerRuntimeConfigurationProperties properties,
            WorkerRuntimeConfigurationReconciler reconciler) {
        this.properties=properties;
        this.reconciler=reconciler;
    }

    @Override
    public void run(ApplicationArguments args) {
        int restored=reconciler.restoreLastKnownGood();
        if(hasUsableRecoveredSnapshot()) {
            log.info("RUNTIME_CONFIGURATION_COLD_START_LKG_READY restored={} failClosed={}",restored,properties.failClosedOnColdStart());
            return;
        }

        if(!properties.failClosedOnColdStart()) {
            bestEffortMigrationReconcile();
            if(hasUsableRecoveredSnapshot()) {
                log.info("RUNTIME_CONFIGURATION_COLD_START_LIVE_READY mode=MIGRATION_FALLBACK");
            } else {
                log.info("RUNTIME_CONFIGURATION_COLD_START_STARTUP_FALLBACK_ALLOWED mode=DUAL_READ_MIGRATION");
            }
            return;
        }

        long waitMs=properties.coldStartWaitMs();
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(waitMs);
        RuntimeException lastFailure=null;

        while(true) {
            try {
                List<String> ids=reconciler.activeConfigSetIds();
                if(ids.isEmpty()) {
                    lastFailure=new IllegalStateException("RUNTIME_CONFIGURATION_ACTIVE_CONFIG_SETS_EMPTY");
                } else {
                    boolean allUsable=true;
                    for(String id:ids) {
                        reconciler.reconcileOne(id);
                        if(!reconciler.usable(id)) allUsable=false;
                    }
                    if(allUsable) return;
                    lastFailure=new IllegalStateException("RUNTIME_CONFIGURATION_COLD_START_NOT_YET_USABLE configSetIds="+ids);
                }
            } catch(RuntimeException discoveryFailure) {
                lastFailure=discoveryFailure;
                if(hasUsableRecoveredSnapshot()) return;
            }

            long remainingNanos=deadline-System.nanoTime();
            if(waitMs==0 || remainingNanos<=0) {
                throw new IllegalStateException(
                        "RUNTIME_CONFIGURATION_COLD_START_FAILED waitMs="+waitMs+
                        " no authenticated usable LKG and Core Runtime Configuration authority did not become ready",
                        lastFailure);
            }
            sleepBeforeRetry(Math.min(properties.coldStartRetryMs(),
                    Math.max(1,TimeUnit.NANOSECONDS.toMillis(remainingNanos))));
        }
    }


    private void bestEffortMigrationReconcile() {
        try {
            List<String> ids=reconciler.activeConfigSetIds();
            if(ids.isEmpty()) {
                log.info("RUNTIME_CONFIGURATION_ACTIVE_CONFIG_SETS_EMPTY mode=DUAL_READ_MIGRATION action=STARTUP_FALLBACK");
                return;
            }
            for(String id:ids) reconciler.reconcileOne(id);
        } catch(RuntimeException authorityUnavailable) {
            log.warn("RUNTIME_CONFIGURATION_COLD_START_RECONCILE_DEFERRED mode=DUAL_READ_MIGRATION coreAuthorityUnavailable={} message={}",
                    authorityUnavailable.getClass().getSimpleName(),authorityUnavailable.getMessage());
        }
    }

    private boolean hasUsableRecoveredSnapshot() {
        List<String> recovered=reconciler.registry().currentConfigSetIds().stream().sorted().toList();
        return !recovered.isEmpty() && recovered.stream().allMatch(reconciler::usable);
    }

    private static void sleepBeforeRetry(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RUNTIME_CONFIGURATION_COLD_START_INTERRUPTED",interrupted);
        }
    }
}

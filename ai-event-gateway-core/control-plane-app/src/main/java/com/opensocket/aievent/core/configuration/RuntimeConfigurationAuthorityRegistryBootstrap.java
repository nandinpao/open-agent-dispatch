package com.opensocket.aievent.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Bootstraps local runtime authority from the same durable reconciliation path used after cutover commit. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class RuntimeConfigurationAuthorityRegistryBootstrap implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(RuntimeConfigurationAuthorityRegistryBootstrap.class);
    private final RuntimeConfigurationAuthorityReconciliationService reconciliation;
    public RuntimeConfigurationAuthorityRegistryBootstrap(RuntimeConfigurationAuthorityReconciliationService reconciliation){this.reconciliation=reconciliation;}
    @Override public void run(ApplicationArguments args){
        var keys=reconciliation.reconcileFromDurableState();
        log.info("runtime_config_authority_registry_bootstrapped authoritativeKeyCount={} keys={}",keys.size(),keys);
    }
}

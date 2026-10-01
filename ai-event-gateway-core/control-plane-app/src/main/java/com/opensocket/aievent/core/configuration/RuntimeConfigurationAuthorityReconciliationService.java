package com.opensocket.aievent.core.configuration;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceStore;

/** Rebuilds the I/O-free local authority registry exclusively from durable governance truth. */
@Service
public class RuntimeConfigurationAuthorityReconciliationService {
    private static final Logger log=LoggerFactory.getLogger(RuntimeConfigurationAuthorityReconciliationService.class);
    private final RuntimeConfigurationDefinitionStore definitions;
    private final ConfigurationInventoryGovernanceStore governance;
    private final RuntimeConfigurationAuthorityRegistry registry;

    public RuntimeConfigurationAuthorityReconciliationService(RuntimeConfigurationDefinitionStore definitions,
            ConfigurationInventoryGovernanceStore governance,RuntimeConfigurationAuthorityRegistry registry){
        this.definitions=definitions;this.governance=governance;this.registry=registry;
    }

    public Set<String> reconcileFromDurableState(){
        LinkedHashSet<String> authoritative=new LinkedHashSet<>();
        definitions.listMigrationAuthorized().forEach(definition->governance.findGovernance(definition.key()).ifPresent(state->{
            if(state.status()==ConfigurationInventoryGovernanceStatus.MIGRATED||state.status()==ConfigurationInventoryGovernanceStatus.LEGACY_RETIRED)
                authoritative.add(definition.key());
        }));
        registry.replace(authoritative);
        log.info("runtime_config_authority_registry_reconciled authoritativeKeyCount={}",authoritative.size());
        return Set.copyOf(authoritative);
    }
}

package com.opensocket.aievent.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Prevents durable-governance / JVM-authority split brain by applying local authority only after commit. */
@Component
public class RuntimeConfigurationAuthorityChangedListener {
    private static final Logger log=LoggerFactory.getLogger(RuntimeConfigurationAuthorityChangedListener.class);
    private final RuntimeConfigurationAuthorityReconciliationService reconciliation;
    public RuntimeConfigurationAuthorityChangedListener(RuntimeConfigurationAuthorityReconciliationService reconciliation){this.reconciliation=reconciliation;}

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void afterCommit(RuntimeConfigurationAuthorityChangedEvent event){
        var keys=reconciliation.reconcileFromDurableState();
        log.info("runtime_config_authority_after_commit_reconciled setKey={} cutoverId={} authoritativeKeyCount={}",event.setKey(),event.cutoverId(),keys.size());
    }
}

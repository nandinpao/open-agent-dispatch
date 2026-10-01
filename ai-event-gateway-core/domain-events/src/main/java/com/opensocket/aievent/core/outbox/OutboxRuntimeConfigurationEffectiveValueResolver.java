package com.opensocket.aievent.core.outbox;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class OutboxRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final OutboxRuntimeConfigurationView view;
    public OutboxRuntimeConfigurationEffectiveValueResolver(OutboxRuntimeConfigurationView view){ this.view=view; }
    @Override public String owner(){ return "CORE_OUTBOX"; }
    @Override public Set<String> supportedKeys(){ return OutboxRuntimeConfigurationView.ALL; }
    @Override public Object resolve(String key){
        return switch(key){
            case OutboxRuntimeConfigurationView.BATCH_SIZE -> view.batchSize();
            case OutboxRuntimeConfigurationView.CLAIM_LEASE -> view.claimLease().toString();
            case OutboxRuntimeConfigurationView.INITIAL_BACKOFF -> view.initialBackoff().toString();
            case OutboxRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
            case OutboxRuntimeConfigurationView.MAX_BACKOFF -> view.maxBackoff().toString();
            case OutboxRuntimeConfigurationView.SCAN_INTERVAL_MS -> view.scanInterval().toMillis();
            case OutboxRuntimeConfigurationView.WORKER_ID -> view.workerId();
            default -> throw new IllegalArgumentException("CORE_OUTBOX_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
        };
    }
}

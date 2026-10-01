package com.opensocket.aievent.core.action;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class AdapterActionWorkerRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS=AdapterActionWorkerRuntimeConfigurationView.ALL;
    private final AdapterActionWorkerRuntimeConfigurationView view;
    public AdapterActionWorkerRuntimeConfigurationEffectiveValueResolver(AdapterActionWorkerRuntimeConfigurationView view){this.view=view;}
    @Override public String owner(){return "ADAPTER_ACTION_WORKER";}
    @Override public Set<String> supportedKeys(){return KEYS;}
    @Override public Object resolve(String key){return switch(key){
        case AdapterActionWorkerRuntimeConfigurationView.RETRY_ENABLED -> view.retryEnabled();
        case AdapterActionWorkerRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
        case AdapterActionWorkerRuntimeConfigurationView.INITIAL_BACKOFF -> view.initialBackoff().toString();
        case AdapterActionWorkerRuntimeConfigurationView.MAX_BACKOFF -> view.maxBackoff().toString();
        case AdapterActionWorkerRuntimeConfigurationView.EXPIRED_LEASE_SCAN_BATCH_SIZE -> view.expiredLeaseScanBatchSize();
        case AdapterActionWorkerRuntimeConfigurationView.EXPIRED_LEASE_SCAN_INTERVAL_MS -> view.expiredLeaseScanInterval().toMillis();
        default -> throw new IllegalArgumentException("ADAPTER_ACTION_WORKER_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
    };}
}

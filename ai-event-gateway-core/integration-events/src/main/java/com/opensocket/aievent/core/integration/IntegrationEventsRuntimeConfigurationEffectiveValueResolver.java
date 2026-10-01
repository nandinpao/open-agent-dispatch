package com.opensocket.aievent.core.integration;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class IntegrationEventsRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final IntegrationEventsRuntimeConfigurationView view;
    public IntegrationEventsRuntimeConfigurationEffectiveValueResolver(IntegrationEventsRuntimeConfigurationView view){this.view=view;}
    @Override public String owner(){return "CORE_INTEGRATION_EVENTS";}
    @Override public Set<String> supportedKeys(){return IntegrationEventsRuntimeConfigurationView.ALL;}
    @Override public Object resolve(String key){return switch(key){
        case IntegrationEventsRuntimeConfigurationView.BATCH_SIZE -> view.batchSize();
        case IntegrationEventsRuntimeConfigurationView.CLAIM_LEASE -> view.claimLease().toString();
        case IntegrationEventsRuntimeConfigurationView.ENDPOINT_URL -> view.endpointUrl();
        case IntegrationEventsRuntimeConfigurationView.EXPORTED_EVENT_TYPES -> String.join(",",view.exportedEventTypes());
        case IntegrationEventsRuntimeConfigurationView.INITIAL_BACKOFF -> view.initialBackoff().toString();
        case IntegrationEventsRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
        case IntegrationEventsRuntimeConfigurationView.MAX_BACKOFF -> view.maxBackoff().toString();
        case IntegrationEventsRuntimeConfigurationView.REQUEST_TIMEOUT -> view.requestTimeout().toString();
        case IntegrationEventsRuntimeConfigurationView.SCAN_INTERVAL_MS -> view.scanInterval().toMillis();
        case IntegrationEventsRuntimeConfigurationView.SINK -> view.sink().name();
        case IntegrationEventsRuntimeConfigurationView.SOURCE -> view.source();
        case IntegrationEventsRuntimeConfigurationView.WORKER_ID -> view.workerId();
        default -> throw new IllegalArgumentException("CORE_INTEGRATION_EVENTS_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
    };}
}

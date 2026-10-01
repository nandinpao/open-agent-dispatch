package com.opensocket.aievent.core.processing;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class EventDecisionRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final EventDecisionRuntimeConfigurationView view;
    public EventDecisionRuntimeConfigurationEffectiveValueResolver(EventDecisionRuntimeConfigurationView view) { this.view = view; }
    @Override public String owner() { return "CORE_EVENT_DECISION"; }
    @Override public Set<String> supportedKeys() { return EventDecisionRuntimeConfigurationView.ALL; }
    @Override public Object resolve(String key) {
        return switch (key) {
            case EventDecisionRuntimeConfigurationView.DEDUP_WINDOW -> view.dedupWindow().toString();
            case EventDecisionRuntimeConfigurationView.DEDUP_TTL -> view.dedupTtl().toString();
            default -> throw new IllegalArgumentException("CORE_EVENT_DECISION_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}

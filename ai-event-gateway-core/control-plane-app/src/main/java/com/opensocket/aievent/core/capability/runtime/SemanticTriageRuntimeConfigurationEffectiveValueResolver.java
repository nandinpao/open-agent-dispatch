package com.opensocket.aievent.core.capability.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for semantic-triage operational settings. */
@Component
public final class SemanticTriageRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final SemanticTriageRuntimeConfigurationView view;

    public SemanticTriageRuntimeConfigurationEffectiveValueResolver(SemanticTriageRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "CAPABILITY_OPERATIONAL"; }
    @Override public Set<String> supportedKeys() { return SemanticTriageRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        if (SemanticTriageRuntimeConfigurationView.WORKER_DELAY_MS.equals(key)) {
            return view.workerDelay().toMillis();
        }
        throw new IllegalArgumentException("SEMANTIC_TRIAGE_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
    }
}

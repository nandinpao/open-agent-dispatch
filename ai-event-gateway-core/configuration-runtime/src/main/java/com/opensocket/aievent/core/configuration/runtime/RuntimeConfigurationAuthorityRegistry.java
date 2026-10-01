package com.opensocket.aievent.core.configuration.runtime;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Local, I/O-free authority projection for runtime configuration consumers.
 *
 * <p>The registry is populated from durable migration-governance state at startup and is updated
 * only after a cutover is finalized. Consumers use it to distinguish the pre-cutover observation
 * window (startup fallback is still legal) from a completed MIGRATED cutover (missing runtime
 * values are a configuration error, never a reason to silently re-activate YAML/ENV authority).</p>
 */
@Component
public class RuntimeConfigurationAuthorityRegistry {
    private final AtomicReference<Set<String>> runtimeAuthoritativeKeys = new AtomicReference<>(Set.of());

    public boolean isRuntimeAuthoritative(String definitionKey) {
        return definitionKey != null && runtimeAuthoritativeKeys.get().contains(definitionKey);
    }

    public boolean areRuntimeAuthoritative(Collection<String> definitionKeys) {
        return definitionKeys != null && runtimeAuthoritativeKeys.get().containsAll(definitionKeys);
    }

    public Set<String> snapshot() {
        return runtimeAuthoritativeKeys.get();
    }

    public void replace(Collection<String> definitionKeys) {
        runtimeAuthoritativeKeys.set(immutable(definitionKeys));
    }

    public void activate(Collection<String> definitionKeys) {
        if (definitionKeys == null || definitionKeys.isEmpty()) return;
        runtimeAuthoritativeKeys.updateAndGet(current -> {
            LinkedHashSet<String> next = new LinkedHashSet<>(current);
            next.addAll(definitionKeys);
            return Set.copyOf(next);
        });
    }

    public void deactivate(Collection<String> definitionKeys) {
        if (definitionKeys == null || definitionKeys.isEmpty()) return;
        runtimeAuthoritativeKeys.updateAndGet(current -> {
            LinkedHashSet<String> next = new LinkedHashSet<>(current);
            next.removeAll(definitionKeys);
            return Set.copyOf(next);
        });
    }

    private static Set<String> immutable(Collection<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Set.copyOf(new LinkedHashSet<>(values));
    }
}

package com.opensocket.aievent.core.action;

/** Optional outbound observability port owned by the Adapter Action module. */
@FunctionalInterface
public interface AdapterActionMetricsPort {
    void recordAdapterAction(AdapterAction action, String operation);

    static AdapterActionMetricsPort noop() {
        return (action, operation) -> { };
    }
}

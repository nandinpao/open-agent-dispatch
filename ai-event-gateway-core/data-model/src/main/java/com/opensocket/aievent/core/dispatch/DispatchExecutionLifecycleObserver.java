package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

/**
 * Additive observer around the actual external gateway call. beforeNetwork may fail closed;
 * afterNetwork records the irreversible delivery fact and must never trigger another send.
 */
public interface DispatchExecutionLifecycleObserver {
    default int order() { return 1000; }
    default void beforeNetwork(DispatchRequest request, OffsetDateTime at) { }
    default void afterNetwork(DispatchRequest request, GatewayDispatchResult result, OffsetDateTime at) { }
}

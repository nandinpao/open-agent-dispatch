package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

/** Additive pre-network safety gate. Implementations must be side-effect free. */
public interface DispatchExecutionSafetyGuard {
    default int order() { return 1000; }
    DispatchExecutionSafetyDecision evaluate(DispatchRequest request, OffsetDateTime now);
}

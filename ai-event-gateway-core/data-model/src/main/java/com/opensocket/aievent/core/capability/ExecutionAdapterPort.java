package com.opensocket.aievent.core.capability;

/** Phase 5 execution-port convergence contract. Implementations perform HOW only and may not route providers. */
public interface ExecutionAdapterPort {
    String adapterType();
    ExecutionAdapterResult submit(ExecutionAdapterRegistration adapter, ExecutionAdapterCommand command);
}

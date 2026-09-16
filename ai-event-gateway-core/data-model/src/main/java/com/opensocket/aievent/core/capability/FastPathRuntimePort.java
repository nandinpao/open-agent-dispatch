package com.opensocket.aievent.core.capability;
/** Internal runtime SPI. ACTIVE Fast Path may skip Triage/Planner only; Step execution remains governed by Phase3-5. */
public interface FastPathRuntimePort { FastPathRuntimeDecision resolveForRuntime(String tenantId, FastPathRuntimeRequest request); }

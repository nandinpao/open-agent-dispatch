package com.opensocket.aievent.core.capability;

/** Internal machine/runtime port. No HTTP caller may supply Provider, Agent, Pool or transport selection. */
public interface RuntimeStepAuthorityAutomationPort {
    RuntimeStepAuthorityAutomationResult prepare(String tenantId,String runId,String stepId);
}

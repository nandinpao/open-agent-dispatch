package com.opensocket.aievent.core.capability;

/** Optional runtime bridge for cancelling an already-submitted external execution. */
public interface PlanExecutionCancellationPort { boolean supports(String adapterType); boolean cancel(String tenantId,String externalExecutionRef,String reason); }

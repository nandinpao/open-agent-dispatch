package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Phase 8 runtime guardrails. These limits do not select or authorize a Provider. */
public record PlanExecutionPolicy(String tenantId,String policyId,String displayName,int maxAttemptsPerStep,long defaultStepTimeoutSeconds,int maxActiveSteps,boolean requireArtifactOnSuccess,String status,int version,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}

package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;
/** Tenant runtime gate. Emergency kill switch always falls back to Adaptive Path. */
public record FastPathRuntimePolicy(String tenantId,String policyId,String displayName,boolean activeFastPathEnabled,boolean shadowEvaluationEnabled,boolean emergencyKillSwitch,int minShadowComparisons,double minShadowPlanMatchRate,double minShadowCapabilityCoverage,String status,int version,OffsetDateTime createdAt,OffsetDateTime updatedAt){}

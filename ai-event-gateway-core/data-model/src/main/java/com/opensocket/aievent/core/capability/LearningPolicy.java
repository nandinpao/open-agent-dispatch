package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;
/** Tenant learning thresholds. Promotion is governance-only; drift may fail-safe degrade an ACTIVE pattern. */
public record LearningPolicy(String policyId,String tenantId,String displayName,int minCandidateSamples,double minCandidateSuccessRate,double minCandidateHumanAcceptanceRate,int minShadowSamples,double minActiveSuccessRate,double minActiveHumanAcceptanceRate,Long maxP95LatencyMs,int driftWindowSize,boolean requireHumanApprovalForPromotion,String status,int version,OffsetDateTime createdAt,OffsetDateTime updatedAt){}

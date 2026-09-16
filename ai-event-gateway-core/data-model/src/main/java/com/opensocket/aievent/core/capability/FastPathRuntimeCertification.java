package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;
/** Human certification of one exact ACTIVE Routing Pattern version for runtime use. */
public record FastPathRuntimeCertification(String certificationId,String tenantId,String patternId,int patternVersion,String runtimePolicyId,int runtimePolicyVersion,String status,int shadowComparisonCount,double shadowPlanMatchRate,double shadowCapabilityCoverage,String reason,String actorRef,OffsetDateTime certifiedAt){}

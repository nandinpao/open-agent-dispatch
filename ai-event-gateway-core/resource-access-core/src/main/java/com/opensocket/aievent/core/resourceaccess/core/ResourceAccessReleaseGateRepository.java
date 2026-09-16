package com.opensocket.aievent.core.resourceaccess.core;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.List;
public interface ResourceAccessReleaseGateRepository {
 ShadowMismatchAggregate aggregateShadowEvidence(String tenantId,Instant from,Instant to);
 List<ResourceAccessCertificationEvidence> latestCertificationEvidence(String tenantId);
 List<LegacyBypassInventoryItem> activeLegacyBypasses(String tenantId,Instant at);
 void appendAssessment(ResourceAccessReleaseAssessment assessment);
 void appendRolloutTransition(String tenantId,String transitionId,ResourceAccessEnforcementMode fromMode,ResourceAccessEnforcementMode toMode,String assessmentId,String actorId,String reason,String correlationId,Instant at);
 void appendRollbackRehearsal(String tenantId,String rehearsalId,ResourceAccessEnforcementMode fromMode,ResourceAccessEnforcementMode toMode,boolean passed,String incidentId,String artifactRef,String actorId,String correlationId,Instant at);
}

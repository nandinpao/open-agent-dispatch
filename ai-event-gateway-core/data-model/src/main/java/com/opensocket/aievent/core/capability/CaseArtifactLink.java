package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;
/** Immutable evidence link from a Case to a normalized PlanExecutionArtifact. */
public record CaseArtifactLink(String tenantId,String caseId,String artifactId,String role,String stepId,String capabilityCode,OffsetDateTime linkedAt){}

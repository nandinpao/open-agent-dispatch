package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Normalized artifact handed from completed dependency Steps to downstream Steps. */
public record PlanExecutionArtifact(String artifactId,String tenantId,String runId,String stepId,String attemptId,String capabilityCode,String finding,Double confidence,List<String> evidenceRefs,Map<String,Object> output,String dataClassification,OffsetDateTime createdAt) { public PlanExecutionArtifact { evidenceRefs=evidenceRefs==null?List.of():List.copyOf(evidenceRefs); output=output==null?Map.of():Map.copyOf(output); } }

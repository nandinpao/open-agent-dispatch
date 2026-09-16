package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Append-only accepted Case outcome used by learning. No raw payload or provider topology is learned. */
public record ExecutionOutcomeEvidence(String outcomeId,String tenantId,String caseId,String runId,String planId,int planRevision,String problemSignature,String signatureVersion,String classification,List<String> capabilityKeys,boolean executionSucceeded,boolean humanAccepted,Long latencyMs,Long tokenUsage,Double estimatedCost,String humanDecision,String accountableRef,OffsetDateTime recordedAt){public ExecutionOutcomeEvidence{capabilityKeys=capabilityKeys==null?List.of():List.copyOf(capabilityKeys);}}

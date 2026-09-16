package com.opensocket.aievent.core.capability;
import java.util.List;
/** Creates a canonical enterprise Case from one authoritative aggregation Artifact plus the Run evidence set. */
public record CaseConvergenceRequest(String caseId,String runId,String aggregationId,String aggregationArtifactId,String classification,List<String> affectedResources,String idempotencyKey){public CaseConvergenceRequest{affectedResources=affectedResources==null?List.of():List.copyOf(affectedResources);}}

package com.opensocket.aievent.core.capability;
import java.util.List;
/** Requests preparation of a synthesis CapabilityRequirement from normalized Phase 8 Artifacts only. */
public record AggregationPreparationRequest(String aggregationId,String runId,List<String> artifactIds){public AggregationPreparationRequest{artifactIds=artifactIds==null?List.of():List.copyOf(artifactIds);}}

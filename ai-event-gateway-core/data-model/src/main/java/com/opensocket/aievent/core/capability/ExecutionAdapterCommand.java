package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Protocol-neutral command delivered to a Phase 5 adapter implementation after HOW resolution. */
public record ExecutionAdapterCommand(String tenantId, String localTaskId, String executionResolutionId, String capabilityCode, String operation, Map<String,Object> input, List<String> artifactRefs, OffsetDateTime deadline) {
    public ExecutionAdapterCommand { input = input == null ? Map.of() : Map.copyOf(input); artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs); }
}

package com.opensocket.aievent.core.capability;

import java.util.List;

/** Protocol-neutral request to create the authoritative child Task for one runtime Plan Step. */
public record PlanChildTaskRequest(
        String tenantId, String runId, String stepId, int attemptNo,
        CapabilityRequirement requiredCapability, List<String> parentArtifactRefs,
        String parentTaskRef, String requestingAgentId) {
    public PlanChildTaskRequest {
        parentArtifactRefs = parentArtifactRefs == null ? List.of() : List.copyOf(parentArtifactRefs);
    }

    /** Backward-compatible constructor retained for the still fail-closed generic Plan runtime. */
    public PlanChildTaskRequest(String tenantId, String runId, String stepId, int attemptNo,
            CapabilityRequirement requiredCapability, List<String> parentArtifactRefs, String parentTaskRef) {
        this(tenantId, runId, stepId, attemptNo, requiredCapability, parentArtifactRefs, parentTaskRef, null);
    }
}

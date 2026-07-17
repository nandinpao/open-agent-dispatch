package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;

public record AgentRuntimeCapabilityItem(
        String agentId,
        String capabilityKind,
        String capabilityValue,
        String capabilityRevision,
        String source,
        OffsetDateTime updatedAt
) {}

package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;

public record A2ADispatchProgressCommand(
        String tenantId, String childTaskId, String dispatchRequestId, String stage,
        A2ABlockerCode blockerCode, String reason, String evidenceReference,
        String actorId, OffsetDateTime occurredAt) {}

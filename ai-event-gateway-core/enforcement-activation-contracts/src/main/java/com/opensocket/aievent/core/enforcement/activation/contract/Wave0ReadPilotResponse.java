package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.UUID;

public record Wave0ReadPilotResponse<T extends Wave0CanonicalPayload>(
        T payload,
        Wave0ReadPilotEntryPoint entryPoint,
        AuthorityDecision authorityDecision,
        Wave0ReadPilotSource servedBy,
        boolean shadowCompared,
        boolean fallbackUsed,
        UUID observationId,
        Wave0ReadPilotGateState gateState,
        Instant completedAt) {

    public Wave0ReadPilotResponse {
        if (payload == null || entryPoint == null || authorityDecision == null || servedBy == null || gateState == null || completedAt == null) throw new IllegalArgumentException("pilot response fields are required");
    }
}

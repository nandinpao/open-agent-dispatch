package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;

public record Wave0ReadPilotGateStatus(
        Wave0ReadPilotEntryPoint entryPoint,
        Wave0ReadPilotGateState state,
        String reasonCode,
        String changedBy,
        Instant changedAt,
        long version) {

    public Wave0ReadPilotGateStatus {
        if (entryPoint == null || state == null || changedAt == null) throw new IllegalArgumentException("entryPoint, state and changedAt are required");
        reasonCode = normalize(reasonCode, "GATE_STATE");
        changedBy = normalize(changedBy, "system");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public boolean permitsPilotExecution() {
        return state == Wave0ReadPilotGateState.OBSERVING || state == Wave0ReadPilotGateState.OPEN;
    }

    public boolean permitsTargetServing() {
        return state == Wave0ReadPilotGateState.OPEN;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

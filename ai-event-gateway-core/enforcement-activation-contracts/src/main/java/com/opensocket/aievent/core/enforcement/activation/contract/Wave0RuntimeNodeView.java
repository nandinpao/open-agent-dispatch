package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;

public record Wave0RuntimeNodeView(
        String nodeId,
        String state,
        long activeRevision,
        long lastKnownGoodRevision,
        String activeChecksum,
        boolean stale,
        Instant updatedAt) implements Wave0CanonicalPayload {

    public Wave0RuntimeNodeView {
        nodeId = required(nodeId, "nodeId");
        state = required(state, "state");
        activeChecksum = activeChecksum == null ? "" : activeChecksum.trim();
        if (activeRevision < 0 || lastKnownGoodRevision < 0 || updatedAt == null) throw new IllegalArgumentException("runtime node values are invalid");
    }

    @Override public String canonicalValue() {
        return nodeId + "|" + state + "|" + activeRevision + "|" + lastKnownGoodRevision + "|" + activeChecksum + "|" + stale + "|" + updatedAt;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

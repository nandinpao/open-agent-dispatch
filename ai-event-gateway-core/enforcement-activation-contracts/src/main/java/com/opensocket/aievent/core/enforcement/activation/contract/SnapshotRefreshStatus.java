package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;

public record SnapshotRefreshStatus(
        SnapshotRefreshState state,
        long activeRevision,
        long attemptedRevision,
        long lastKnownGoodRevision,
        String activeChecksum,
        String failureCode,
        String failureMessage,
        Instant updatedAt) {

    public SnapshotRefreshStatus {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (activeRevision < 0 || attemptedRevision < 0 || lastKnownGoodRevision < 0) {
            throw new IllegalArgumentException("revision values must not be negative");
        }
        activeChecksum = activeChecksum == null ? "" : activeChecksum;
        failureCode = failureCode == null ? "" : failureCode;
        failureMessage = failureMessage == null ? "" : failureMessage;
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
    }
}

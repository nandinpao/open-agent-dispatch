package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;

public record Wave0ReadPilotMetricSummary(
        Wave0ReadPilotEntryPoint entryPoint,
        long sampleCount,
        long comparedCount,
        long mismatchCount,
        long targetErrorCount,
        int mismatchBasisPoints,
        int targetErrorBasisPoints,
        long targetP95Millis,
        Instant windowStartedAt,
        Instant evaluatedAt) {

    public Wave0ReadPilotMetricSummary {
        if (entryPoint == null || windowStartedAt == null || evaluatedAt == null) throw new IllegalArgumentException("entryPoint and timestamps are required");
        if (sampleCount < 0 || comparedCount < 0 || mismatchCount < 0 || targetErrorCount < 0 || targetP95Millis < 0) throw new IllegalArgumentException("metrics must not be negative");
        if (mismatchBasisPoints < 0 || mismatchBasisPoints > 10_000 || targetErrorBasisPoints < 0 || targetErrorBasisPoints > 10_000) throw new IllegalArgumentException("basis points are invalid");
    }
}

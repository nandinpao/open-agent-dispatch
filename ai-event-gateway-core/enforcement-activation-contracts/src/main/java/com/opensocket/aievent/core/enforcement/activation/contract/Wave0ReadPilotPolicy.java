package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Duration;

public record Wave0ReadPilotPolicy(
        Wave0ReadPilotEntryPoint entryPoint,
        boolean enabled,
        boolean compareLegacyCanary,
        long minimumSamples,
        int maximumMismatchBasisPoints,
        int maximumTargetErrorBasisPoints,
        long maximumTargetP95Millis,
        Duration observationWindow,
        boolean autoPause,
        long version) {

    public Wave0ReadPilotPolicy {
        if (entryPoint == null) throw new IllegalArgumentException("entryPoint is required");
        if (minimumSamples < 1) throw new IllegalArgumentException("minimumSamples must be positive");
        if (maximumMismatchBasisPoints < 0 || maximumMismatchBasisPoints > 10_000) throw new IllegalArgumentException("maximumMismatchBasisPoints is invalid");
        if (maximumTargetErrorBasisPoints < 0 || maximumTargetErrorBasisPoints > 10_000) throw new IllegalArgumentException("maximumTargetErrorBasisPoints is invalid");
        if (maximumTargetP95Millis < 1) throw new IllegalArgumentException("maximumTargetP95Millis must be positive");
        if (observationWindow == null || observationWindow.isZero() || observationWindow.isNegative()) throw new IllegalArgumentException("observationWindow must be positive");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
}

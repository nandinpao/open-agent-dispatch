package com.opensocket.aievent.core.enforcement.activation.contract;

public record Wave0ReadPilotOverview(
        Wave0ReadPilotPolicy policy,
        Wave0ReadPilotGateStatus gate,
        Wave0ReadPilotMetricSummary metrics,
        boolean executionPropertyEnabled,
        boolean routeEligible,
        String blockingReason,
        boolean executable) {

    public Wave0ReadPilotOverview {
        if (policy == null || gate == null || metrics == null) throw new IllegalArgumentException("policy, gate and metrics are required");
        blockingReason = blockingReason == null ? "" : blockingReason.trim();
    }
}

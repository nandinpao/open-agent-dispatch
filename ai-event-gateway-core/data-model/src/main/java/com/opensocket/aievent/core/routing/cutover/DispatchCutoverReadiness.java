package com.opensocket.aievent.core.routing.cutover;

public record DispatchCutoverReadiness(
        String tenantId,
        String flowId,
        long sampleSize,
        long authoritativeSampleSize,
        long controlSampleSize,
        long requirementBlockedCount,
        long noCandidateCount,
        long selectionDifferenceCount,
        double requirementBlockedRate,
        double noCandidateRate,
        double selectionDifferenceRate,
        boolean authoritativeMetricsAvailable
) {
    public static DispatchCutoverReadiness empty(String tenantId, String flowId) {
        return new DispatchCutoverReadiness(tenantId, flowId, 0, 0, 0, 0, 0, 0, 0d, 0d, 0d, false);
    }
}

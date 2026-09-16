package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** A0-R6 immutable routing inputs required to replay WHY a Binding/Pool was selected. */
public record RoutingFeatureSnapshot(
        String snapshotId,String tenantId,String eligibilityDecisionId,String candidateCatalogVersion,
        OffsetDateTime metricAsOf,String qualityModelVersion,String poolHealthRevision,
        Map<String,Object> availabilitySnapshot,Map<String,Object> loadSnapshot,String pricingVersion,
        String routingProfileId,int routingProfileVersion,String scoringConfigVersion,OffsetDateTime capturedAt) {
    public RoutingFeatureSnapshot {
        availabilitySnapshot=availabilitySnapshot==null?Map.of():Map.copyOf(availabilitySnapshot);
        loadSnapshot=loadSnapshot==null?Map.of():Map.copyOf(loadSnapshot);
    }
}

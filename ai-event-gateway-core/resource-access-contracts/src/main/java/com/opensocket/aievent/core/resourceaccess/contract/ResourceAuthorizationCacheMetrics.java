package com.opensocket.aievent.core.resourceaccess.contract;

/** Node-local cache coordination telemetry. Counters are monotonic for the process lifetime. */
public record ResourceAuthorizationCacheMetrics(
        long hits,
        long misses,
        long coordinatedLoads,
        long followerWaits,
        long rejectedLoads,
        long prewarmedEntries,
        long invalidations,
        int residentEntries,
        int inFlightLoads) {
    public ResourceAuthorizationCacheMetrics {
        if (hits < 0 || misses < 0 || coordinatedLoads < 0 || followerWaits < 0 || rejectedLoads < 0
                || prewarmedEntries < 0 || invalidations < 0 || residentEntries < 0 || inFlightLoads < 0) {
            throw new IllegalArgumentException("cache metrics must be non-negative");
        }
    }
    public static final ResourceAuthorizationCacheMetrics ZERO =
            new ResourceAuthorizationCacheMetrics(0,0,0,0,0,0,0,0,0);
}

package com.opensocket.aievent.core.resourceaccess.core;
import java.time.Instant;
/** Aggregated decision/list shadow evidence for one tenant and observation window. */
public record ShadowMismatchAggregate(long sampleCount,long mismatchCount,long criticalMismatchCount,long legacyUnavailableCount,long authorizationErrorCount,Instant windowStartedAt,Instant windowEndedAt){
 public ShadowMismatchAggregate{if(sampleCount<0||mismatchCount<0||criticalMismatchCount<0||legacyUnavailableCount<0||authorizationErrorCount<0)throw new IllegalArgumentException("counts must be non-negative");if(windowStartedAt==null||windowEndedAt==null||windowEndedAt.isBefore(windowStartedAt))throw new IllegalArgumentException("valid window is required");}
 public int mismatchRateBps(){return rate(mismatchCount,sampleCount);}
 public int legacyUnavailableRateBps(){return rate(legacyUnavailableCount,sampleCount);}
 private static int rate(long numerator,long denominator){if(denominator<=0)return 10_000;return (int)Math.min(10_000,Math.round((numerator*10_000.0d)/denominator));}
}

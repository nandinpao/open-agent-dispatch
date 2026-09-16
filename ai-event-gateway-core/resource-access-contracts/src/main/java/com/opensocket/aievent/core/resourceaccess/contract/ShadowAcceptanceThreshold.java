package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Duration;
/** Versioned acceptance policy. Rates are expressed in basis points (1/100 of one percent). */
public record ShadowAcceptanceThreshold(
 long minimumSamples, int maximumMismatchRateBps, int maximumLegacyUnavailableRateBps,
 long maximumCriticalMismatches, Duration minimumObservationWindow) {
 public ShadowAcceptanceThreshold {
  if(minimumSamples<1) throw new IllegalArgumentException("minimumSamples must be positive");
  if(maximumMismatchRateBps<0||maximumMismatchRateBps>10_000) throw new IllegalArgumentException("maximumMismatchRateBps out of range");
  if(maximumLegacyUnavailableRateBps<0||maximumLegacyUnavailableRateBps>10_000) throw new IllegalArgumentException("maximumLegacyUnavailableRateBps out of range");
  if(maximumCriticalMismatches<0) throw new IllegalArgumentException("maximumCriticalMismatches must be non-negative");
  if(minimumObservationWindow==null||minimumObservationWindow.isNegative()||minimumObservationWindow.isZero()) throw new IllegalArgumentException("minimumObservationWindow is required");
 }
 public static ShadowAcceptanceThreshold productionDefault(){return new ShadowAcceptanceThreshold(1_000,10,100,0,Duration.ofHours(24));}
}

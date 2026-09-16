package com.opensocket.aievent.core.resourceaccess.core;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Duration;
import java.util.*;
/** Pure policy used by P4RA-I release assessment and rollout transition checks. */
public final class ResourceAccessReleaseGatePolicy {
 private static final Set<ResourceAccessCertificationType> ALL=EnumSet.allOf(ResourceAccessCertificationType.class);
 public List<String> blockers(ShadowMismatchAggregate a,ShadowAcceptanceThreshold t,ResourceAccessEnforcementMode target,List<ResourceAccessCertificationEvidence> evidence,List<LegacyBypassInventoryItem> bypasses){
  Objects.requireNonNull(a);Objects.requireNonNull(t);Objects.requireNonNull(target);List<String>b=new ArrayList<>();
  if(target!=ResourceAccessEnforcementMode.SHADOW){
   Duration observed=Duration.between(a.windowStartedAt(),a.windowEndedAt());
   if(observed.compareTo(t.minimumObservationWindow())<0)b.add("SHADOW_OBSERVATION_WINDOW_TOO_SHORT");
   if(a.sampleCount()<t.minimumSamples())b.add("SHADOW_SAMPLE_SIZE_INSUFFICIENT");
   if(a.criticalMismatchCount()>t.maximumCriticalMismatches())b.add("CRITICAL_SHADOW_MISMATCH_PRESENT");
   if(a.authorizationErrorCount()>0)b.add("AUTHORIZATION_ERROR_PRESENT");
   if(a.mismatchRateBps()>t.maximumMismatchRateBps())b.add("SHADOW_MISMATCH_RATE_EXCEEDED");
   if(a.legacyUnavailableRateBps()>t.maximumLegacyUnavailableRateBps())b.add("LEGACY_UNAVAILABLE_RATE_EXCEEDED");
  }
  if(target==ResourceAccessEnforcementMode.FULL_ENFORCE&&!bypasses.isEmpty())b.add("ACTIVE_LEGACY_BYPASS_PRESENT");
  Set<ResourceAccessCertificationType> required=requiredEvidence(target);Map<ResourceAccessCertificationType,ResourceAccessCertificationStatus> latest=new EnumMap<>(ResourceAccessCertificationType.class);
  for(var e:evidence)latest.putIfAbsent(e.evidenceType(),e.status());
  for(var type:required){var status=latest.get(type);if(status!=ResourceAccessCertificationStatus.PASS)b.add("CERTIFICATION_NOT_PASSED:"+type.name());}
  return List.copyOf(b);
 }
 public boolean validForwardTransition(ResourceAccessEnforcementMode from,ResourceAccessEnforcementMode to){return switch(from){case OFF->to==ResourceAccessEnforcementMode.SHADOW;case SHADOW->to==ResourceAccessEnforcementMode.READ_ENFORCE;case READ_ENFORCE->to==ResourceAccessEnforcementMode.WRITE_ENFORCE;case WRITE_ENFORCE->to==ResourceAccessEnforcementMode.FULL_ENFORCE;case FULL_ENFORCE->false;};}
 public boolean validRollbackTransition(ResourceAccessEnforcementMode from,ResourceAccessEnforcementMode to){return to.ordinal()<from.ordinal();}
 private Set<ResourceAccessCertificationType> requiredEvidence(ResourceAccessEnforcementMode target){if(target==ResourceAccessEnforcementMode.SHADOW)return EnumSet.noneOf(ResourceAccessCertificationType.class);if(target==ResourceAccessEnforcementMode.READ_ENFORCE)return EnumSet.of(ResourceAccessCertificationType.MAVEN_FULL_REACTOR,ResourceAccessCertificationType.CLEAN_MIGRATION,ResourceAccessCertificationType.UPGRADE_MIGRATION,ResourceAccessCertificationType.FORCE_RLS,ResourceAccessCertificationType.ADMIN_UI_TYPECHECK,ResourceAccessCertificationType.ADMIN_UI_PRODUCTION_BUILD,ResourceAccessCertificationType.BROWSER_E2E,ResourceAccessCertificationType.SECURITY_CONCURRENCY);if(target==ResourceAccessEnforcementMode.WRITE_ENFORCE)return EnumSet.complementOf(EnumSet.of(ResourceAccessCertificationType.ROLLBACK_REHEARSAL,ResourceAccessCertificationType.LEGACY_BYPASS_INVENTORY));return EnumSet.copyOf(ALL);}
}

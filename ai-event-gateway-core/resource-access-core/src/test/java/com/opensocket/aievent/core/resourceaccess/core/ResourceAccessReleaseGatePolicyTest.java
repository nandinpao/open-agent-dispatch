package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceAccessReleaseGatePolicyTest {
 private final ResourceAccessReleaseGatePolicy policy=new ResourceAccessReleaseGatePolicy();
 private final Instant start=Instant.parse("2026-07-28T00:00:00Z");
 private final Instant end=start.plus(Duration.ofHours(24));
 @Test void criticalMismatchAlwaysBlocks(){
  var aggregate=new ShadowMismatchAggregate(10_000,1,1,0,0,start,end);
  var blockers=policy.blockers(aggregate,ShadowAcceptanceThreshold.productionDefault(),ResourceAccessEnforcementMode.READ_ENFORCE,allEvidence(),List.of());
  assertTrue(blockers.contains("CRITICAL_SHADOW_MISMATCH_PRESENT"));
 }
 @Test void fullEnforceRequiresEveryCertificationAndNoBypass(){
  var aggregate=new ShadowMismatchAggregate(10_000,0,0,0,0,start,end);
  var bypass=new LegacyBypassInventoryItem("b1","t1","legacy-controller","/api/legacy/**","security","migration",end.plusSeconds(3600),true,"replacement",0);
  var blockers=policy.blockers(aggregate,ShadowAcceptanceThreshold.productionDefault(),ResourceAccessEnforcementMode.FULL_ENFORCE,allEvidence(),List.of(bypass));
  assertEquals(List.of("ACTIVE_LEGACY_BYPASS_PRESENT"),blockers);
 }
 @Test void onlyMonotonicForwardTransitionsAreAccepted(){
  assertTrue(policy.validForwardTransition(ResourceAccessEnforcementMode.OFF,ResourceAccessEnforcementMode.SHADOW));
  assertTrue(policy.validForwardTransition(ResourceAccessEnforcementMode.SHADOW,ResourceAccessEnforcementMode.READ_ENFORCE));
  assertFalse(policy.validForwardTransition(ResourceAccessEnforcementMode.SHADOW,ResourceAccessEnforcementMode.FULL_ENFORCE));
  assertTrue(policy.validRollbackTransition(ResourceAccessEnforcementMode.FULL_ENFORCE,ResourceAccessEnforcementMode.SHADOW));
 }
 @Test void productionThresholdRequiresTwentyFourHoursAndOneThousandSamples(){
  var shortWindow=new ShadowMismatchAggregate(999,0,0,0,0,start,start.plus(Duration.ofHours(23)));
  var blockers=policy.blockers(shortWindow,ShadowAcceptanceThreshold.productionDefault(),ResourceAccessEnforcementMode.READ_ENFORCE,List.of(),List.of());
  assertTrue(blockers.contains("SHADOW_OBSERVATION_WINDOW_TOO_SHORT"));
  assertTrue(blockers.contains("SHADOW_SAMPLE_SIZE_INSUFFICIENT"));
 }
 @Test void enteringShadowDoesNotRequirePriorShadowSamples(){
  var empty=new ShadowMismatchAggregate(0,0,0,0,0,start,start);
  assertTrue(policy.blockers(empty,ShadowAcceptanceThreshold.productionDefault(),ResourceAccessEnforcementMode.SHADOW,List.of(),List.of()).isEmpty());
 }

 private List<ResourceAccessCertificationEvidence> allEvidence(){
  List<ResourceAccessCertificationEvidence> result=new ArrayList<>();int i=0;
  for(var type:ResourceAccessCertificationType.values())result.add(new ResourceAccessCertificationEvidence("e"+(i++),"t1",type,ResourceAccessCertificationStatus.PASS,"cmd","artifact","","pass",start,end,"actor","corr"));
  return result;
 }
}

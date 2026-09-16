package com.opensocket.aievent.core.resourceaccess.persistence;
import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
/** Deterministic CPU-side release-policy budget; database P95 is certified by the live RLS/migration environment. */
class ResourceAccessReleasePerformanceTest {
 @Test void evaluatesFiftyThousandShadowWindowsWithinBudget(){
  var policy=new ResourceAccessReleaseGatePolicy();var threshold=ShadowAcceptanceThreshold.productionDefault();
  var aggregate=new ShadowMismatchAggregate(100_000,5,0,10,0,Instant.EPOCH,Instant.EPOCH.plus(Duration.ofHours(24)));
  var evidence=allEvidence();long started=System.nanoTime();
  for(int i=0;i<50_000;i++)assertTrue(policy.blockers(aggregate,threshold,ResourceAccessEnforcementMode.FULL_ENFORCE,evidence,List.of()).isEmpty());
  long elapsedMs=(System.nanoTime()-started)/1_000_000L;
  assertTrue(elapsedMs<5_000,"release policy evaluation exceeded 5s CPU budget: "+elapsedMs+"ms");
 }
 private List<ResourceAccessCertificationEvidence> allEvidence(){var r=new ArrayList<ResourceAccessCertificationEvidence>();var now=Instant.now();int i=0;for(var type:ResourceAccessCertificationType.values())r.add(new ResourceAccessCertificationEvidence("e"+(i++),"t",type,ResourceAccessCertificationStatus.PASS,"cmd","artifact","","pass",now,now,"actor","corr"));return r;}
}

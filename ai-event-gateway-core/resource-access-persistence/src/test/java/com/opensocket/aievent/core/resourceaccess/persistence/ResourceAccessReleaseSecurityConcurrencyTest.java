package com.opensocket.aievent.core.resourceaccess.persistence;
import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
/** Concurrency contract: no race can turn critical or stale evidence into a passed release gate. */
class ResourceAccessReleaseSecurityConcurrencyTest {
 @Test void concurrentCriticalAssessmentsAlwaysBlock()throws Exception{
  var policy=new ResourceAccessReleaseGatePolicy();var threshold=ShadowAcceptanceThreshold.productionDefault();var start=Instant.EPOCH;var aggregate=new ShadowMismatchAggregate(10_000,1,1,0,0,start,start.plus(Duration.ofHours(24)));
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<1_000;i++)tasks.add(()->policy.blockers(aggregate,threshold,ResourceAccessEnforcementMode.SHADOW,List.of(),List.of()).contains("CRITICAL_SHADOW_MISMATCH_PRESENT"));
   for(var future:executor.invokeAll(tasks))assertTrue(future.get());
  }
 }
 @Test void rolloutCannotSkipStages(){var policy=new ResourceAccessReleaseGatePolicy();assertFalse(policy.validForwardTransition(ResourceAccessEnforcementMode.OFF,ResourceAccessEnforcementMode.FULL_ENFORCE));assertFalse(policy.validForwardTransition(ResourceAccessEnforcementMode.READ_ENFORCE,ResourceAccessEnforcementMode.FULL_ENFORCE));}
}

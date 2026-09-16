package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class PrincipalClearanceServiceTest {
 @Test void clearanceRequiresIndependentApproval(){
  var repo=new InMemoryResourcePolicyRepository();var service=new PrincipalClearanceService(repo);var now=Instant.parse("2026-07-28T00:00:00Z");
  var draft=service.create(new GrantPrincipalClearanceCommand("t","c",ScopePrincipalType.USER,"u",SensitivityLevel.RESTRICTED,now,now.plusSeconds(60),"creator","business need","corr","clear-create",now));
  assertEquals(ClearanceStatus.DRAFT,draft.status());
  var pending=service.submit(new ClearanceMutationCommand("t","c",1,"creator","submit","corr2","clear-submit",now.plusSeconds(1)));
  assertEquals(ClearanceStatus.PENDING_APPROVAL,pending.status());
  assertThrows(IllegalStateException.class,()->service.approve(new ClearanceMutationCommand("t","c",2,"creator","approve","corr3","clear-self",now.plusSeconds(2))));
  var active=service.approve(new ClearanceMutationCommand("t","c",2,"reviewer","approved","corr4","clear-approve",now.plusSeconds(3)));
  assertEquals(ClearanceStatus.ACTIVE,active.status());assertEquals("reviewer",active.approvedBy());
 }
}

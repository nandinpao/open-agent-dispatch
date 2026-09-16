package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class ResourceExplicitDenyServiceTest {
 @Test void requesterCannotSelfApproveDeny(){
  var repo=new InMemoryResourcePolicyRepository();var service=new ResourceExplicitDenyService(repo);var now=Instant.parse("2026-07-28T00:00:00Z");
  var draft=service.create(new CreateExplicitDenyCommand("t","d",ScopePrincipalType.USER,"u","task.read",ResourceType.TASK,ScopeType.RESOURCE,"task-1","incident",DenySeverity.CRITICAL,now,now.plusSeconds(60),"creator","c1","deny-create",now));
  assertEquals(ScopeDenyState.DRAFT,draft.state());
  var pending=service.submit(new ExplicitDenyMutationCommand("t","d",1,"creator","submit","c2","deny-submit",now.plusSeconds(1)));
  assertEquals(ScopeDenyState.PENDING_APPROVAL,pending.state());
  assertThrows(IllegalStateException.class,()->service.approve(new ExplicitDenyMutationCommand("t","d",2,"creator","approve","c3","deny-self",now.plusSeconds(2))));
  var active=service.approve(new ExplicitDenyMutationCommand("t","d",2,"reviewer","approved","c4","deny-approve",now.plusSeconds(3)));
  assertEquals(ScopeDenyState.ACTIVE,active.state());assertEquals("reviewer",active.approvedBy());
 }
}

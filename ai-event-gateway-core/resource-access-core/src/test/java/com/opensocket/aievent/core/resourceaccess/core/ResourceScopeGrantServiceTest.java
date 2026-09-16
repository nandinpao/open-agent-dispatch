package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ResourceScopeGrantServiceTest {
    @Test void grantRequiresSubmissionAndIndependentApproval(){
        var repo=new InMemoryResourcePolicyRepository();var service=new ResourceScopeGrantService(repo);Instant now=Instant.parse("2026-07-28T00:00:00Z");
        var created=service.create(new CreateScopeGrantCommand("t1","g1",ScopePrincipalType.USER,"u1","task.read",ResourceType.TASK,ScopeType.DEPARTMENT,"erp",VisibilityLevel.STANDARD,now,now.plusSeconds(3600),ScopeGrantSource.MANUAL,"support queue","creator","c1","create-1",now));
        assertEquals(ScopeGrantState.DRAFT,created.state());
        var pending=service.submit(new ScopeGrantMutationCommand("t1","g1",1,"creator","submit","c2","submit-1",now.plusSeconds(1)));
        assertEquals(ScopeGrantState.PENDING_APPROVAL,pending.state());
        assertThrows(IllegalStateException.class,()->service.approve(new ScopeGrantMutationCommand("t1","g1",2,"creator","approve","c3","approve-self",now.plusSeconds(2))));
        var active=service.approve(new ScopeGrantMutationCommand("t1","g1",2,"reviewer","approved","c4","approve-1",now.plusSeconds(3)));
        assertEquals(ScopeGrantState.ACTIVE,active.state());assertEquals("reviewer",active.approvedBy());
    }
    @Test void activeGrantRecordRejectsCreatorAsApprover(){
        Instant now=Instant.parse("2026-07-28T00:00:00Z");
        assertThrows(IllegalArgumentException.class,()->new ScopeGrantRecord("t1","g-system",ScopePrincipalType.SERVICE_ACCOUNT,"svc","task.read",ResourceType.TASK,ScopeType.TENANT,"t1",VisibilityLevel.METADATA,now,now.plusSeconds(60),ScopeGrantSource.SYSTEM_POLICY,"bootstrap","same-actor","same-actor",ScopeGrantState.ACTIVE,"system-idem",1,now,now));
    }

}

package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplicitDenyPrecedencePolicyTest {
    @Test void activeExplicitDenyOverridesActiveGrant(){
        Instant now=Instant.parse("2026-07-28T00:00:00Z");
        var grant=new ScopeGrantRecord("t","g",ScopePrincipalType.USER,"u","task.read",ResourceType.TASK,ScopeType.RESOURCE,"task-1",VisibilityLevel.FULL,now.minusSeconds(5),now.plusSeconds(60),ScopeGrantSource.MANUAL,"reason","maker","approver",ScopeGrantState.ACTIVE,"g-idem",1,now,now);
        var deny=new ExplicitDenyRecord("t","d",ScopePrincipalType.USER,"u","task.read",ResourceType.TASK,ScopeType.RESOURCE,"task-1","investigation",DenySeverity.CRITICAL,now.minusSeconds(5),now.plusSeconds(60),"security","reviewer",ScopeDenyState.ACTIVE,"d-idem",1,now,now);
        var policy=new ExplicitDenyPrecedencePolicy();
        assertTrue(policy.denyOverridesGrant(deny,grant,"task.read",now));
        assertEquals("d",policy.blockingDeny(List.of(deny),"task.read",now).orElseThrow().denyId());
    }
}

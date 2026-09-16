package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultRuntimeResultFenceServiceTest {
    private static final Instant NOW=Instant.parse("2026-07-29T00:00:00Z");

    @Test void staleFencingVersionIsQuarantined(){
        InMemoryQuarantine q=new InMemoryQuarantine();RuntimeAuthorizationLease lease=lease(RuntimeLeaseStatus.FENCED,7);
        RuntimeAuthorizationLeasePort leases=new FixedLeasePort(lease);
        var service=new DefaultRuntimeResultFenceService(leases,q,Clock.fixed(NOW,ZoneOffset.UTC));
        RuntimeResultFenceResult result=service.acceptOrQuarantine(submission(6));
        assertEquals(RuntimeResultDisposition.QUARANTINED,result.disposition());assertEquals(1,q.values.size());assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",q.values.get(0).submission().payloadHash());
    }

    @Test void exactActiveBindingIsAccepted(){
        InMemoryQuarantine q=new InMemoryQuarantine();RuntimeAuthorizationLease lease=lease(RuntimeLeaseStatus.ACTIVE,7);
        var service=new DefaultRuntimeResultFenceService(new FixedLeasePort(lease),q,Clock.fixed(NOW,ZoneOffset.UTC));
        RuntimeResultFenceResult result=service.acceptOrQuarantine(submission(7));
        assertEquals(RuntimeResultDisposition.ACCEPTED,result.disposition());assertTrue(q.values.isEmpty());
    }

    private static RuntimeResultSubmission submission(long fence){return new RuntimeResultSubmission("T1","submission-1","lease-1",fence,new ResourceRef("T1",ResourceType.TASK_RESULT,"result-1"),"assignment-1",2,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","corr-1",NOW);}
    private static RuntimeAuthorizationLease lease(RuntimeLeaseStatus status,long fence){return new RuntimeAuthorizationLease("lease-1","decision-1","descriptor-hash",new ResourceRef("T1",ResourceType.TASK_RESULT,"result-1"),new PrincipalRef(PrincipalRef.PrincipalType.SYSTEM_SERVICE,"worker-1"),"a2a.result.create","assignment-1",2,PolicyVersion.ZERO,SecurityEpoch.ZERO,fence,NOW.minusSeconds(10),NOW.plusSeconds(60),NOW.plusSeconds(30),NOW.plusSeconds(10),status,1);}
    private record FixedLeasePort(RuntimeAuthorizationLease lease) implements RuntimeAuthorizationLeasePort {public RuntimeAuthorizationLease issue(AuthorizationRequest r,AuthorizationDecision d){return lease;}public RuntimeAuthorizationLease check(RuntimeAuthorizationCheckpoint c){return lease;}public RuntimeAuthorizationLease complete(String t,String l,String c){return lease;}public RuntimeAuthorizationLease revoke(String t,String l,String r,String c){return lease;}}
    private static final class InMemoryQuarantine implements RuntimeLateResultQuarantinePort {final List<RuntimeLateResultQuarantine> values=new ArrayList<>();public RuntimeLateResultQuarantine append(RuntimeLateResultQuarantine r){values.add(r);return r;}public Optional<RuntimeLateResultQuarantine> findBySubmission(String t,String s){return values.stream().filter(v->v.submission().submissionId().equals(s)).findFirst();}public List<RuntimeLateResultQuarantine> findOpen(String t,int l){return List.copyOf(values);}public RuntimeLateResultQuarantine resolve(String t,String q,long v,RuntimeLateResultQuarantineStatus target,String a,String r,Instant at){throw new UnsupportedOperationException();}}
}

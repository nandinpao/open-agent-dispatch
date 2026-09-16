package com.opensocket.aievent.core.a2a;
import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Repository;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2AResultEvidenceRepository implements A2AResultEvidenceRepository {
 private final Map<String,A2AResultEvidence> values=new ConcurrentHashMap<>(); public A2AResultEvidence save(A2AResultEvidence v){values.put(v.getTenantId()+":"+v.getEvidenceId(),v);return v;} public List<A2AResultEvidence> findByAttempt(String t,String id,int n){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getAttemptId())).sorted(Comparator.comparing(A2AResultEvidence::getVerifiedAt)).limit(Math.max(1,Math.min(n,1000))).toList();} public String mode(){return "MEMORY";}
}

package com.opensocket.aievent.core.a2a;
import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Repository;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2AResultAttemptRepository implements A2AResultAttemptRepository {
 private final Map<String,A2AResultAttempt> values=new ConcurrentHashMap<>(); public A2AResultAttempt save(A2AResultAttempt v){values.put(k(v.getTenantId(),v.getAttemptId()),v);return v;} public List<A2AResultAttempt> findByRequest(String t,String id,int n){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getRequestId())).sorted(Comparator.comparing(A2AResultAttempt::getReceivedAt).reversed()).limit(cap(n)).toList();} public String mode(){return "MEMORY";} private String k(String t,String id){return t+":"+id;} private int cap(int n){return Math.max(1,Math.min(n,1000));}
}

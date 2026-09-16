package com.opensocket.aievent.core.a2a;
import java.util.*;import java.util.concurrent.CopyOnWriteArrayList;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.context.annotation.Profile;import org.springframework.stereotype.Repository;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY",matchIfMissing=true)
public class InMemoryA2ACancellationEvidenceRepository implements A2ACancellationEvidenceRepository {
 private final List<A2ACancellationEvidence> values=new CopyOnWriteArrayList<>();
 public A2ACancellationEvidence append(A2ACancellationEvidence v){Optional<A2ACancellationEvidence> existing=findByEventKey(v.getTenantId(),v.getEventKey());if(existing.isPresent())return existing.get();values.add(v);return v;}
 public List<A2ACancellationEvidence> findByCancellation(String t,String id,int l){return values.stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getCancellationId())).limit(l).toList();}
 public Optional<A2ACancellationEvidence> findByEventKey(String t,String k){return values.stream().filter(v->t.equals(v.getTenantId())&&Objects.equals(k,v.getEventKey())).findFirst();}
 public String mode(){return "MEMORY";}
}

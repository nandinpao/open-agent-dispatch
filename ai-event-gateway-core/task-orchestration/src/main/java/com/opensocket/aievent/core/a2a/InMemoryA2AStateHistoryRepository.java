package com.opensocket.aievent.core.a2a;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2AStateHistoryRepository implements A2AStateHistoryRepository {
    private final ConcurrentHashMap<String,A2AStateHistoryEntry> values=new ConcurrentHashMap<>();
    public synchronized A2AStateHistoryEntry save(A2AStateHistoryEntry e){Optional<A2AStateHistoryEntry> replay=findByIdempotencyKey(e.getTenantId(),e.getIdempotencyKey());if(replay.isPresent())return replay.get();values.put(e.getTenantId()+":"+e.getHistoryId(),e);return e;}
    public Optional<A2AStateHistoryEntry> findByIdempotencyKey(String t,String k){if(k==null)return Optional.empty();return values.values().stream().filter(v->t.equals(v.getTenantId())&&k.equals(v.getIdempotencyKey())).findFirst();}
    public List<A2AStateHistoryEntry> findByRequest(String t,String id,int n){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getRequestId())).sorted(Comparator.comparing(A2AStateHistoryEntry::getTransitionAt)).limit(Math.max(1,Math.min(n,1000))).toList();}
    public String mode(){return "MEMORY";}
}

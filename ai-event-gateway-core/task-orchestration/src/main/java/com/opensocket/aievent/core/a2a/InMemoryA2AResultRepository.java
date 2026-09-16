package com.opensocket.aievent.core.a2a;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2AResultRepository implements A2AResultRepository {
    private final ConcurrentHashMap<String,A2AResult> values=new ConcurrentHashMap<>();
    public synchronized A2AResult save(A2AResult r){
        Optional<A2AResult> replay=findByIdempotencyKey(r.getTenantId(),r.getIdempotencyKey());
        if(replay.isPresent())return replay.get();
        Optional<A2AResult> canonical=findByRequest(r.getTenantId(),r.getRequestId());
        if(canonical.isPresent()){
            if(same(canonical.get(),r))return canonical.get();
            throw new IllegalStateException("A2A_CANONICAL_RESULT_CONFLICT");
        }
        values.put(r.getTenantId()+":"+r.getResultId(),r);return r;
    }
    public Optional<A2AResult> findById(String tenantId,String resultId){return values.values().stream().filter(v->java.util.Objects.equals(tenantId,v.getTenantId())&&java.util.Objects.equals(resultId,v.getResultId())).findFirst();}
    public Optional<A2AResult> findByRequest(String t,String id){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getRequestId())).findFirst();}
    public Optional<A2AResult> findByIdempotencyKey(String t,String k){if(k==null)return Optional.empty();return values.values().stream().filter(v->t.equals(v.getTenantId())&&k.equals(v.getIdempotencyKey())).findFirst();}
    public List<A2AResult> findByParentTask(String t,String id,int n){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getParentTaskId())).sorted(Comparator.comparing(A2AResult::getCompletedAt).reversed()).limit(Math.max(1,Math.min(n,1000))).toList();}
    public String mode(){return "MEMORY";}
    private boolean same(A2AResult a,A2AResult b){return a.getResultStatus()==b.getResultStatus()&&Objects.equals(a.getPayloadHash(),b.getPayloadHash())&&Objects.equals(a.getCallbackInboxId(),b.getCallbackInboxId())&&Objects.equals(a.getAssignmentId(),b.getAssignmentId());}
}

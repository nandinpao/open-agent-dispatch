package com.opensocket.aievent.core.a2a;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod") @ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2AResultIdempotencyClaimRepository implements A2AResultIdempotencyClaimRepository {
    private final ConcurrentHashMap<String,A2AResultIdempotencyClaim> values=new ConcurrentHashMap<>();
    @Override public synchronized A2AResultIdempotencyClaim save(A2AResultIdempotencyClaim claim){
        String key=k(claim.getTenantId(),claim.getIdempotencyKey());
        A2AResultIdempotencyClaim existing=values.get(key);
        if(existing!=null){
            if(same(existing,claim))return existing;
            throw new IllegalStateException("A2A_RESULT_IDEMPOTENCY_CLAIM_CONFLICT");
        }
        values.put(key,claim); return claim;
    }
    @Override public Optional<A2AResultIdempotencyClaim> findByIdempotencyKey(String tenantId,String idempotencyKey){
        return idempotencyKey==null?Optional.empty():Optional.ofNullable(values.get(k(tenantId,idempotencyKey)));
    }
    @Override public String mode(){return "MEMORY";}
    private String k(String t,String k){return t+":"+k;}
    private boolean same(A2AResultIdempotencyClaim a,A2AResultIdempotencyClaim b){
        return java.util.Objects.equals(a.getRequestId(),b.getRequestId())
                &&java.util.Objects.equals(a.getResultId(),b.getResultId())
                &&java.util.Objects.equals(a.getResultFingerprint(),b.getResultFingerprint());
    }
}

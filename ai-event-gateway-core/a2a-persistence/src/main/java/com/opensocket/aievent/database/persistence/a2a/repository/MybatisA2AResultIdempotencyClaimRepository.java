package com.opensocket.aievent.database.persistence.a2a.repository;
import java.util.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository; import com.opensocket.aievent.core.a2a.*; import com.opensocket.aievent.database.persistence.a2a.*; import com.opensocket.aievent.database.persistence.a2a.dao.*;
@Repository @ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS") public class MybatisA2AResultIdempotencyClaimRepository implements A2AResultIdempotencyClaimRepository {
 private final A2AResultIdempotencyClaimDao dao; private final A2APersistenceConverter c; public MybatisA2AResultIdempotencyClaimRepository(A2AResultIdempotencyClaimDao d,A2APersistenceConverter c){dao=d;this.c=c;}
 public A2AResultIdempotencyClaim save(A2AResultIdempotencyClaim v){dao.insert(c.toPo(v));A2AResultIdempotencyClaim canonical=findByIdempotencyKey(v.getTenantId(),v.getIdempotencyKey()).orElseThrow();if(!same(canonical,v))throw new IllegalStateException("A2A_RESULT_IDEMPOTENCY_CLAIM_CONFLICT");return canonical;}
 public Optional<A2AResultIdempotencyClaim> findByIdempotencyKey(String t,String k){return k==null?Optional.empty():Optional.ofNullable(dao.findByIdempotencyKey(t,k)).map(c::toDomain);} public String mode(){return "MYBATIS";}
 private boolean same(A2AResultIdempotencyClaim a,A2AResultIdempotencyClaim b){return Objects.equals(a.getRequestId(),b.getRequestId())&&Objects.equals(a.getResultId(),b.getResultId())&&Objects.equals(a.getResultFingerprint(),b.getResultFingerprint());}
}

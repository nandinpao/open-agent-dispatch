package com.opensocket.aievent.database.persistence.a2a.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.a2a.A2AIdempotencyClaim;
import com.opensocket.aievent.core.a2a.A2AIdempotencyRecord;
import com.opensocket.aievent.core.a2a.A2AIdempotencyRepository;
import com.opensocket.aievent.core.a2a.A2AIdempotencyStatus;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AIdempotencyDao;
import com.opensocket.aievent.database.persistence.a2a.po.A2AIdempotencyPo;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisA2AIdempotencyRepository implements A2AIdempotencyRepository {
    private final A2AIdempotencyDao dao;
    public MybatisA2AIdempotencyRepository(A2AIdempotencyDao dao){this.dao=dao;}

    @Override
    @Transactional
    public A2AIdempotencyClaim claim(String tenantId,String idempotencyKey,String operationType,
                                      String requestHash,OffsetDateTime createdAt,OffsetDateTime expiresAt){
        A2AIdempotencyPo value=new A2AIdempotencyPo();
        value.setTenantId(tenantId); value.setIdempotencyKey(idempotencyKey); value.setOperationType(operationType);
        value.setRequestHash(requestHash); value.setResultStatus(A2AIdempotencyStatus.IN_PROGRESS.name());
        value.setCreatedAt(createdAt); value.setExpiresAt(expiresAt); int inserted=dao.claim(value);
        return new A2AIdempotencyClaim(find(tenantId,idempotencyKey,operationType).orElseThrow(), inserted == 1);
    }
    @Override public Optional<A2AIdempotencyRecord> find(String t,String k,String o){return Optional.ofNullable(dao.find(t,k,o)).map(this::toDomain);}
    @Override public void complete(String t,String k,String o,String rt,String rid,OffsetDateTime at){dao.complete(t,k,o,rt,rid,at);}
    @Override public String mode(){return "MYBATIS";}
    private A2AIdempotencyRecord toDomain(A2AIdempotencyPo p){A2AIdempotencyRecord v=new A2AIdempotencyRecord();v.setTenantId(p.getTenantId());v.setIdempotencyKey(p.getIdempotencyKey());v.setOperationType(p.getOperationType());v.setRequestHash(p.getRequestHash());v.setResourceType(p.getResourceType());v.setResourceId(p.getResourceId());v.setResultStatus(p.getResultStatus()==null?A2AIdempotencyStatus.IN_PROGRESS:A2AIdempotencyStatus.valueOf(p.getResultStatus()));v.setCreatedAt(p.getCreatedAt());v.setCompletedAt(p.getCompletedAt());v.setExpiresAt(p.getExpiresAt());return v;}
}

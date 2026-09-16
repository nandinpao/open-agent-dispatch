package com.opensocket.aievent.database.persistence.a2a.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.a2a.A2ACancellationRepository;
import com.opensocket.aievent.database.persistence.a2a.A2APersistenceConverter;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ACancellationDao;

@Repository
@ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisA2ACancellationRepository implements A2ACancellationRepository {
    private final A2ACancellationDao dao; private final A2APersistenceConverter converter;
    public MybatisA2ACancellationRepository(A2ACancellationDao dao,A2APersistenceConverter converter){this.dao=dao;this.converter=converter;}
    public A2ACancellationRecord save(A2ACancellationRecord value){dao.upsert(converter.toPo(value));return findById(value.getTenantId(),value.getCancellationId()).orElse(value);}
    public A2ACancellationRecord saveExpectedVersion(A2ACancellationRecord value,long expectedVersion){if(dao.updateExpectedVersion(converter.toPo(value),expectedVersion)!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");return findById(value.getTenantId(),value.getCancellationId()).orElseThrow();}
    public Optional<A2ACancellationRecord> findById(String tenantId,String cancellationId){return Optional.ofNullable(dao.findById(tenantId,cancellationId)).map(converter::toDomain);}
    public Optional<A2ACancellationRecord> findByRequest(String tenantId,String requestId){return Optional.ofNullable(dao.findByRequest(tenantId,requestId)).map(converter::toDomain);}
    public Optional<A2ACancellationRecord> findByIdempotencyKey(String tenantId,String key){return Optional.ofNullable(dao.findByIdempotencyKey(tenantId,key)).map(converter::toDomain);}
    public List<A2ACancellationRecord> findDue(OffsetDateTime now,int limit){return dao.findDue(now,cap(limit)).stream().map(converter::toDomain).toList();}
    public List<A2ACancellationRecord> findRecent(String tenantId,int limit){return dao.findRecent(tenantId,cap(limit)).stream().map(converter::toDomain).toList();}
    public String mode(){return "MYBATIS";} private int cap(int limit){return Math.max(1,Math.min(limit,1000));}
}

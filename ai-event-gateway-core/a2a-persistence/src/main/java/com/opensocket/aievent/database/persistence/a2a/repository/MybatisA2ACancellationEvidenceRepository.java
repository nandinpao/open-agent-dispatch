package com.opensocket.aievent.database.persistence.a2a.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.database.persistence.a2a.A2APersistenceConverter;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ACancellationEvidenceDao;

@Repository @ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisA2ACancellationEvidenceRepository implements A2ACancellationEvidenceRepository {
    private final A2ACancellationEvidenceDao dao; private final A2APersistenceConverter converter;
    public MybatisA2ACancellationEvidenceRepository(A2ACancellationEvidenceDao dao,A2APersistenceConverter converter){this.dao=dao;this.converter=converter;}
    public A2ACancellationEvidence append(A2ACancellationEvidence value){dao.insert(converter.toPo(value));return findByEventKey(value.getTenantId(),value.getEventKey()).orElse(value);}
    public List<A2ACancellationEvidence> findByCancellation(String tenantId,String cancellationId,int limit){return dao.findByCancellation(tenantId,cancellationId,Math.max(1,Math.min(limit,1000))).stream().map(converter::toDomain).toList();}
    public Optional<A2ACancellationEvidence> findByEventKey(String tenantId,String eventKey){return Optional.ofNullable(dao.findByEventKey(tenantId,eventKey)).map(converter::toDomain);}
    public String mode(){return "MYBATIS";}
}

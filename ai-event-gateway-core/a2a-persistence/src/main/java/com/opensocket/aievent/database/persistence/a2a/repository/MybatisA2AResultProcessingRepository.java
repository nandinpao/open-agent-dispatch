package com.opensocket.aievent.database.persistence.a2a.repository;
import java.time.OffsetDateTime; import java.util.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository; import com.opensocket.aievent.core.a2a.*; import com.opensocket.aievent.database.persistence.a2a.*; import com.opensocket.aievent.database.persistence.a2a.dao.*;
@Repository @ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisA2AResultProcessingRepository implements A2AResultProcessingRepository {
 private final A2AResultProcessingDao dao; private final A2APersistenceConverter c; public MybatisA2AResultProcessingRepository(A2AResultProcessingDao d,A2APersistenceConverter c){dao=d;this.c=c;}
 public A2AResultProcessing save(A2AResultProcessing v){dao.insert(c.toPo(v));return findByResult(v.getTenantId(),v.getResultId()).orElse(v);}
 public A2AResultProcessing saveExpectedVersion(A2AResultProcessing v,long expected){if(dao.updateExpectedVersion(c.toPo(v),expected)!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");return findByResult(v.getTenantId(),v.getResultId()).orElseThrow();}
 public Optional<A2AResultProcessing> findByResult(String t,String id){return Optional.ofNullable(dao.findByResult(t,id)).map(c::toDomain);}
 public List<A2AResultProcessing> findDue(OffsetDateTime dueAt,int n){return dao.findDue(dueAt,cap(n)).stream().map(c::toDomain).toList();}
 public List<A2AResultProcessing> findByTask(String t,String id,int n){return dao.findByTask(t,id,cap(n)).stream().map(c::toDomain).toList();}
 public String mode(){return "MYBATIS";} private int cap(int n){return Math.max(1,Math.min(n,1000));}
}

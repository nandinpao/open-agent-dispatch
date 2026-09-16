package com.opensocket.aievent.database.persistence.a2a.repository;
import java.util.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository; import com.opensocket.aievent.core.a2a.*; import com.opensocket.aievent.database.persistence.a2a.*; import com.opensocket.aievent.database.persistence.a2a.dao.*;
@Repository @ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisA2AAggregationEvidenceRepository implements A2AAggregationEvidenceRepository {
 private final A2AAggregationEvidenceDao dao; private final A2APersistenceConverter c; public MybatisA2AAggregationEvidenceRepository(A2AAggregationEvidenceDao d,A2APersistenceConverter c){dao=d;this.c=c;}
 public A2AAggregationEvidence save(A2AAggregationEvidence v){dao.insert(c.toPo(v));return v;} public List<A2AAggregationEvidence> findByParentTask(String t,String id,int n){return dao.findByParentTask(t,id,Math.max(1,Math.min(n,1000))).stream().map(c::toDomain).toList();} public String mode(){return "MYBATIS";}
}

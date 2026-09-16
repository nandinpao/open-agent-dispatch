package com.opensocket.aievent.database.persistence.a2a.repository;
import java.util.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.stereotype.Repository;import com.opensocket.aievent.core.a2a.*;import com.opensocket.aievent.database.persistence.a2a.A2APersistenceConverter;import com.opensocket.aievent.database.persistence.a2a.dao.A2ACutoverDao;
@Repository @ConditionalOnProperty(prefix="task",name="store",havingValue="MYBATIS")
public class MybatisA2ACutoverRepository implements A2ACutoverRepository {
 private final A2ACutoverDao dao;private final A2APersistenceConverter converter;public MybatisA2ACutoverRepository(A2ACutoverDao d,A2APersistenceConverter c){dao=d;converter=c;}
 public Optional<A2ACutoverState> find(String scope){return Optional.ofNullable(dao.find(scope)).map(converter::toDomain);}
 public A2ACutoverState save(A2ACutoverState state){dao.upsert(converter.toPo(state));return find(state.getScopeId()).orElse(state);}
 public A2ACutoverState saveExpectedVersion(A2ACutoverState state,long expected){if(dao.updateExpectedVersion(converter.toPo(state),expected)!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");return find(state.getScopeId()).orElseThrow();}
 public A2ACutoverEvidence appendEvidence(A2ACutoverEvidence value){dao.appendEvidence(converter.toPo(value));return Optional.ofNullable(dao.findEvidence(value.getScopeId(),value.getEvidenceId())).map(converter::toDomain).orElse(value);}
 public List<A2ACutoverEvidence> evidence(String scope,int limit){return dao.evidence(scope,Math.max(1,Math.min(limit,500))).stream().map(converter::toDomain).toList();}
 public String mode(){return "MYBATIS";}
}
